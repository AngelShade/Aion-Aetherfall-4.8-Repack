package com.aionemu.gameserver.services.playerbot;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.controllers.FlyController;
import com.aionemu.gameserver.controllers.effect.PlayerEffectController;
import com.aionemu.gameserver.dao.PlayerDAO;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.account.PlayerAccountData;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.model.items.storage.PlayerStorage;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.stats.calc.functions.PlayerStatFunctions;
import com.aionemu.gameserver.model.team.TeamType;
import com.aionemu.gameserver.model.team.group.PlayerGroupService;
import com.aionemu.gameserver.services.AccountService;
import com.aionemu.gameserver.services.NameRestrictionService;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.services.player.PlayerLeaveWorldService;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.SkillKind;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.WorldMapInstance;

/** Owner-controlled, real Player companions. No fake client connection and no account privileges. */
public final class PlayerBotService {
	private static final Logger log = LoggerFactory.getLogger(PlayerBotService.class);
	private static final PlayerBotService INSTANCE = new PlayerBotService();
	private final Map<Integer, PlayerBotSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();
	private final Map<Integer, PlayerBotPersistence.Home> homes = new HashMap<>();
	private record ReservationKey(int ownerId, int targetId, SkillKind kind) {}
	private record Reservation(int caster, long until) {}
	private final Map<ReservationKey, Reservation> reservations = new java.util.concurrent.ConcurrentHashMap<>();
	private Future<?> task, saveTask;
	private boolean shuttingDown;

	public static PlayerBotService getInstance() { return INSTANCE; }
	private PlayerBotService() {}

	public synchronized List<PlayerBotSession> companions(Player owner) {
		return sessions.values().stream().filter(s -> s.owner() == owner).toList();
	}

	public synchronized PlayerBotSession find(Player owner, String name) {
		return companions(owner).stream().filter(s -> s.bot().getName().equalsIgnoreCase(name)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("That character is not your active companion."));
	}

	/** Called by the native quest-share packet. Only the bot's recruiting owner can make it accept. */
	public synchronized boolean acceptSharedQuest(Player owner, Player bot, int questId) {
		PlayerBotSession session = sessions.get(bot.getObjectId());
		var template = DataManager.QUEST_DATA.getQuestById(questId);
		var ownerState = owner.getQuestStateList().getQuestState(questId);
		if (session == null || session.closing() || session.owner() != owner || bot.isDead()
			|| template == null || template.isCannotShare() || ownerState == null
			|| ownerState.getStatus() == com.aionemu.gameserver.questEngine.model.QuestStatus.COMPLETE
			|| bot.getPlayerGroup() != owner.getPlayerGroup() || bot.getWorldId() != owner.getWorldId() || bot.getInstanceId() != owner.getInstanceId()
			|| !com.aionemu.gameserver.utils.PositionUtil.isInRange(owner, bot, GroupConfig.GROUP_MAX_DISTANCE)) return false;
		synchronized (session) {
			PlayerBotQuestSync.state(session);
			boolean accepted = com.aionemu.gameserver.services.QuestService.startQuest(new com.aionemu.gameserver.questEngine.model.QuestEnv(null, bot, questId));
			if (accepted) PlayerBotQuestSync.accepted(owner, bot, questId, false);
			return accepted;
		}
	}

	public synchronized void recruit(Player owner, String name) {
		ensureOwner(owner);
		PlayerAccountData ownData = owner.getAccount().getPlayerAccDataList().stream()
			.filter(p -> p.getPlayerCommonData().getName().equalsIgnoreCase(name)).findFirst()
			.orElse(null);
		if (ownData == null) {
			var dedicated = PlayerBotRoster.list(owner.getAccount().getId()).stream()
				.filter(e -> e.ready() && name.equalsIgnoreCase(e.name())).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Recruit an offline character or generated companion belonging to your own account."));
			ownData = AccountService.loadPlayerAccountData(dedicated.id());
		}
		int id = ownData.getPlayerCommonData().getPlayerObjId();
		boolean generated = PlayerBotRoster.contains(id);
		if (id == owner.getObjectId()) throw new IllegalArgumentException("You cannot recruit your current character.");
		PlayerBotLease lease = PlayerBotLease.acquire(id);
		if (lease == null) throw new IllegalArgumentException("Character is logging in, saving, or already recruited.");
		Player bot = null;
		PlayerBotSession session = null;
		boolean registered = false;
		try {
			if (World.getInstance().isInWorld(id) || PlayerLeaveWorldService.isLeavingWorld(id))
				throw new IllegalArgumentException("Wait for that character to finish logging out.");
			PlayerAccountData data = AccountService.loadPlayerAccountData(id);
			PlayerCommonData common = data.getPlayerCommonData();
			boolean banned = data.getCharBanInfo() != null && data.getCharBanInfo().getEnd() >= System.currentTimeMillis() / 1000;
			if (!PlayerBotRules.canRecruit(PlayerDAO.getAccountId(id) == owner.getAccount().getId(), common.getRace() == owner.getRace(),
				common.isOnline(), banned, data.getDeletionDate() != null, false, generated ? 0 : common.getLevel() - owner.getLevel(), PlayerBotConfig.LEVEL_DIFFERENCE))
				throw new IllegalArgumentException("Companion must be offline, same faction, not banned/deleting, and within " + PlayerBotConfig.LEVEL_DIFFERENCE + " levels.");
			// Isolated account view avoids changing the human's live warehouse owner, visible equipment or passports.
			Account account = new Account(owner.getAccount().getId());
			account.setName(owner.getAccount().getName());
			account.setMembership(owner.getAccount().getMembership());
			account.setCreationDate(owner.getAccount().getCreationDate());
			account.addPlayerAccountData(data);
			account.setAccountWarehouse(new PlayerStorage(null, StorageType.ACCOUNT_WAREHOUSE));
			long loadStarted = System.currentTimeMillis();
			bot = PlayerService.getPlayer(id, account);
			try { PlayerBotPersistence.validateLoaded(bot, loadStarted); }
			catch (java.sql.SQLException e) { throw new IllegalArgumentException("Companion data could not be loaded completely. Check server logs before recruiting it.", e); }
			if (generated && !bot.getPlayerClass().isStartingClass()) bot.getCommonData().setDaeva(true);
			if (bot.isInPrison()) throw new IllegalArgumentException("Leave prison before recruiting this character.");
			PlayerBotPersistence.Home home = PlayerBotPersistence.Home.of(bot);
			bot.setPlayerBotOwner(owner.getObjectId());
			bot.setCubeLimit(); bot.setWarehouseLimit();
			session = new PlayerBotSession(owner, bot, lease, companions(owner).size(), generated);
			// Register Temporary Bot management before native Stigma reconstruction.
			// Owned alts return immediately from this path and retain normal login rules.
			PlayerBotTemporary.tick(session,Boolean.TRUE.equals(session.snapshot().get("gear")));
			// Stigma skills are temporary and are reconstructed by normal login, not player_skills.
			com.aionemu.gameserver.services.StigmaService.onPlayerLogin(bot);
			for (var skillEntry : bot.getSkillList().getAllSkills()) {
				var template = DataManager.SKILL_DATA.getSkillTemplate(skillEntry.getSkillId());
				if (template != null && template.isPassive())
					SkillEngine.getInstance().applyEffectDirectly(template, skillEntry.getSkillLevel(), bot, bot);
			}
			World.getInstance().setPosition(bot, owner.getWorldId(), owner.getInstanceId(), owner.getX(), owner.getY(), owner.getZ(), owner.getHeading());
			World.getInstance().storeObject(bot);
			World.getInstance().spawn(bot);
			bot.getController().updateZone();
			bot.getLifeStats().updateCurrentStats();
			if (owner.getPlayerGroup() == null) PlayerGroupService.createGroup(owner, bot, TeamType.GROUP, 0);
			else PlayerGroupService.addPlayer(owner.getPlayerGroup(), bot);
			if (owner.isInInstance()) InstanceService.onEnterInstance(bot);
			sessions.put(id, session); homes.put(id, home); registered = true;
			startTasks();
			PacketSendUtility.sendMessage(owner, "Companion recruited: " + bot.getName()
				+ (bot.isDead() ? ". This character needs resurrection before it can act." : ". Use .bot help for orders."));
		} finally {
			if (!registered) {
				if(session!=null)PlayerBotQuestSync.close(session);
				if (bot != null) {
					PlayerGroupService.removePlayer(bot);
					World.getInstance().removeObject(bot);
					bot.getEffectController().removeAllEffects(true);
					bot.getLifeStats().cancelAllTasks();
				}
				lease.close();
			}
		}
	}

	private void ensureOwner(Player owner) {
		if (!PlayerBotConfig.ENABLED || shuttingDown) throw new IllegalArgumentException("Player companions are disabled.");
		if (!owner.isOnline() || !owner.isSpawned()) throw new IllegalArgumentException("Wait until your character is in the world.");
		if (owner.getController().isInCombat()) throw new IllegalArgumentException("You cannot recruit companions while in combat. Wait until combat ends.");
		if (owner.isInAlliance()) throw new IllegalArgumentException("Companions currently need a normal party with a free slot; alliance membership is not implemented yet.");
		if (companions(owner).size() >= Math.min(5, PlayerBotConfig.MAX_PER_OWNER) || sessions.size() >= PlayerBotConfig.MAX_ACTIVE)
			throw new IllegalArgumentException("The companion limit has been reached.");
		if (owner.getPlayerGroup() != null && (!owner.getPlayerGroup().isLeader(owner)
			|| owner.getPlayerGroup().getMembers().size() >= 6 || owner.getPlayerGroup().getTeamType() != TeamType.GROUP))
			throw new IllegalArgumentException("You must lead a normal party with a free slot.");
	}

	/** Generated recruits are normal, persistent starting characters, not temporary NPCs or privileged max-level characters. */
	public synchronized void create(Player owner, String name, PlayerClass playerClass) {
		ensureOwner(owner);
		if (!PlayerBotConfig.GENERATED_ENABLED) throw new IllegalArgumentException("Generated companions are disabled.");
		if (!playerClass.isStartingClass()) throw new IllegalArgumentException("Choose a starting class: warrior, scout, mage, priest, engineer, artist.");
		if (!NameRestrictionService.isValidName(name) || NameRestrictionService.isForbidden(name) || PlayerService.isNameUsedOrReserved(null, name))
			throw new IllegalArgumentException("Choose an available valid character name.");
		int maxCharacters = owner.getAccount().getMembership() >= MembershipConfig.CHARACTER_ADDITIONAL_ENABLE
			? MembershipConfig.CHARACTER_ADDITIONAL_COUNT : GSConfig.CHARACTER_LIMIT_COUNT;
		if (PlayerDAO.getCharacterCountOnAccount(owner.getAccount().getId()) >= maxCharacters)
			throw new IllegalArgumentException("Your account has no free character slot.");
		int id = IDFactory.getInstance().nextId();
		PlayerCommonData common = new PlayerCommonData(id);
		common.setName(name); common.setRace(owner.getRace()); common.setGender(owner.getGender());
		common.setPlayerClass(playerClass); common.setLevel(1);
		// The native client already knows this appearance's race/gender combination.
		var data = new PlayerAccountData(common, owner.getPlayerAppearance().copy());
		Player generated = null;
		try (PlayerBotLease lease = PlayerBotLease.acquire(id)) {
			if (lease == null) throw new IllegalStateException("New character ID is already reserved");
			Account isolated = new Account(owner.getAccount().getId());
			isolated.setName(owner.getAccount().getName()); isolated.setMembership(owner.getAccount().getMembership());
			isolated.setCreationDate(owner.getAccount().getCreationDate()); isolated.addPlayerAccountData(data);
			isolated.setAccountWarehouse(new PlayerStorage(null, StorageType.ACCOUNT_WAREHOUSE));
			generated = PlayerService.newPlayer(data, isolated);
			generated.setPlayerBotOwner(owner.getObjectId());
			PlayerBotGenerationOptions.initialize(generated);
			if (!PlayerService.storeNewPlayer(generated, isolated.getName(), isolated.getId()))
				throw new java.sql.SQLException("Generated character save failed");
			PlayerBotPersistence.save(generated, PlayerBotPersistence.Home.of(generated));
			data.setCreationDate(new Timestamp(System.currentTimeMillis()));
			PlayerService.storeCreationTime(id, data.getCreationDate());
			data.setVisibleItems(generated.getEquipment().getEquippedForAppearance());
		} catch (Exception e) {
			log.error("Normal companion character creation failed for {} (id {})", name, id, e);
			throw new IllegalArgumentException("Character creation failed; check server logs for id " + id + " before retrying.", e);
		} finally {
			if (generated != null) { if (generated.getEffectController() != null) generated.getEffectController().removeAllEffects(true); generated.getLifeStats().cancelAllTasks(); }
		}
		owner.getAccount().addPlayerAccountData(data);
		PacketSendUtility.sendMessage(owner, "Created " + name + " as a level-1 " + playerClass + " in your character roster. It uses a character slot and normal progression.");
		if (owner.getLevel() - 1 <= PlayerBotConfig.LEVEL_DIFFERENCE) recruit(owner, name);
		else PacketSendUtility.sendMessage(owner, "Recruit it with a character within " + PlayerBotConfig.LEVEL_DIFFERENCE + " levels, or play it to level normally.");
	}

	/** Separate bot-only roster: level/class at recruitment, ordinary stats/skills, no human character slot. */
	public synchronized void generate(Player owner, String name, PlayerClass playerClass) {
		ensureOwner(owner);
		int startingLevel = PlayerBotGenerationOptions.level(owner.getLevel());
		if (!PlayerBotConfig.GENERATED_ENABLED) throw new IllegalArgumentException("Generated companions are disabled.");
		if (!PlayerBotRoster.available()) throw new IllegalArgumentException("The separate companion roster needs game-server/sql/playerbots.sql applied and a server restart.");
		if (PlayerBotRoster.list(owner.getAccount().getId()).size() >= PlayerBotConfig.GENERATED_MAX_PER_ACCOUNT)
			throw new IllegalArgumentException("Your generated companion roster is full.");
		if (startingLevel < 10 && !playerClass.isStartingClass() || startingLevel >= 10 && playerClass.isStartingClass())
			throw new IllegalArgumentException("Choose a starting class below level 10, or an advanced class from level 10 onward.");
		if (!NameRestrictionService.isValidName(name) || NameRestrictionService.isForbidden(name) || PlayerService.isNameUsedOrReserved(null, name))
			throw new IllegalArgumentException("Choose an available valid companion name.");
		int id = IDFactory.getInstance().nextId();
		Player generated = null;
		try (PlayerBotLease lease = PlayerBotLease.acquire(id)) {
			if (lease == null) throw new IllegalStateException("New generated companion ID is already reserved");
			// Pending metadata is inserted first so a partial create can never appear at character selection.
			PlayerBotRoster.reserve(owner.getAccount().getId(), id);
			PlayerCommonData common = new PlayerCommonData(id);
			common.setName(name); common.setRace(owner.getRace()); common.setGender(owner.getGender());
			common.setPlayerClass(playerClass); common.setDaeva(!playerClass.isStartingClass()); common.setLevel(startingLevel);
			var data = new PlayerAccountData(common, owner.getPlayerAppearance().copy());
			Account isolated = new Account(owner.getAccount().getId());
			isolated.setName(owner.getAccount().getName()); isolated.setMembership(owner.getAccount().getMembership());
			isolated.setCreationDate(owner.getAccount().getCreationDate()); isolated.addPlayerAccountData(data);
			isolated.setAccountWarehouse(new PlayerStorage(null, StorageType.ACCOUNT_WAREHOUSE));
			generated = PlayerService.newPlayer(data, isolated);
			generated.setPlayerBotOwner(owner.getObjectId());
			PlayerBotGenerationOptions.initialize(generated);
			PlayerBotTemporary.initialize(generated,PlayerBotRules.roleFor(playerClass));
			generated.getLifeStats().synchronizeWithMaxStats();
			if (!PlayerService.storeNewPlayer(generated, isolated.getName(), isolated.getId()))
				throw new java.sql.SQLException("Generated character or inventory creation failed");
			PlayerBotPersistence.save(generated, PlayerBotPersistence.Home.of(generated));
			PlayerBotRoster.ready(isolated.getId(), id);
		} catch (Exception e) {
			log.error("Generated companion {} (id {}) remains pending after creation failure", name, id, e);
			throw new IllegalArgumentException("Generated companion creation failed. Pending entries stay hidden from character selection; check server logs for id " + id + ".", e);
		} finally {
			if (generated != null) {
				PlayerBotTemporary.release(generated);
				if (generated.getEffectController() != null) generated.getEffectController().removeAllEffects(true);
				generated.getLifeStats().cancelAllTasks();
			}
		}
		PacketSendUtility.sendMessage(owner, "Temporary Bot " + name + " is ready as a level-" + startingLevel + " " + playerClass + ". Automatic builds and equipment apply only to Temporary Bots.");
		recruit(owner, name);
	}

	private void startTasks() {
		if (task == null || task.isCancelled())
			task = ThreadPoolManager.getInstance().scheduleAtFixedRate(this::tick, 400, Math.max(200, PlayerBotConfig.TICK_MS));
		if (saveTask == null || saveTask.isCancelled()) {
			long delay = Math.max(30, PlayerBotConfig.SAVE_SECONDS) * 1000L;
			saveTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(this::saveAll, delay, delay);
		}
	}

	private synchronized void tick() {
		reservations.values().removeIf(r -> r.until() < System.currentTimeMillis());
		for (PlayerBotSession session : List.copyOf(sessions.values())) {
			if (session.closing()) continue;
			try {
				if (!PlayerBotConfig.ENABLED || !session.tick()) dismiss(session);
				session.resetFailures();
			} catch (Exception e) {
				log.error("Playerbot tick failed for {}", session.bot().getName(), e);
				if (session.failed() >= 3) {
					PacketSendUtility.sendMessage(session.owner(), "Companion " + session.bot().getName() + " stopped after repeated AI errors. Check server logs.");
					dismiss(session);
				}
			}
		}
	}

	private synchronized void saveAll() {
		for (PlayerBotSession session : List.copyOf(sessions.values())) {
			try {
				if (session.closing()) dismiss(session);
				else synchronized (session) { PlayerBotPersistence.save(session.bot(), homes.get(session.bot().getObjectId())); }
			} catch (Exception e) { log.error("Companion periodic save failed for {}", session.bot().getName(), e); }
		}
	}

	public synchronized void dismissAll(Player owner) { companions(owner).forEach(this::dismiss); }
	public synchronized void dismiss(Player owner, String name) { dismiss(find(owner, name)); }

	/** Resolve current state at effect application too, so an area attack cannot hit a newly arrived player. */
	public static boolean allowsTarget(Player bot, com.aionemu.gameserver.model.gameobjects.Creature target) {
		if (!bot.isPlayerBot()) return true;
		if (target instanceof com.aionemu.gameserver.model.gameobjects.Npc npc && npc.isFlag()) return false;
		Player owner = World.getInstance().getPlayer(bot.getPlayerBotOwnerId());
		if (owner == null || !owner.isOnline() || !owner.isSpawned()
			|| owner.getWorldId() != bot.getWorldId() || owner.getInstanceId() != bot.getInstanceId()
			|| bot.getPlayerGroup() == null || bot.getPlayerGroup() != owner.getPlayerGroup()) return false;
		if (bot.isEnemy(target)) {
			PlayerBotSession session = getInstance().session(bot.getObjectId());
			return target instanceof com.aionemu.gameserver.model.gameobjects.Npc npc && target.getMaster() == target
				&& session != null && session.allowsEnemy(npc);
		}
		return !(target instanceof Player p) || p.getPlayerGroup() == bot.getPlayerGroup();
	}

	private PlayerBotSession session(int id) { return sessions.get(id); }
	public PlayerBotRules.Role combatRole(Player player) {
		var session = sessions.get(player.getObjectId());
		return session == null ? PlayerBotRules.roleFor(player.getPlayerClass()) : session.combatRole();
	}

	private void dismiss(PlayerBotSession session) {
		Player bot = session.bot();
		synchronized (session) {
			try {
				session.markClosing();
				PlayerGroupService.removePlayer(bot);
				if (bot.isSpawned()) {
					InstanceService.onLeaveInstance(bot);
					World.getInstance().despawn(bot);
				}
				bot.getLifeStats().cancelAllTasks();
				bot.getEffectController().removeNonStorableEffectsForLogout();
				PlayerBotPersistence.save(bot, homes.get(bot.getObjectId()));
				PlayerBotMetadata.release(session.owner().getAccount().getId(),bot.getObjectId());
				bot.getEffectController().removeAllEffects(true);
				World.getInstance().removeObject(bot);
				bot.getCommonData().setOnline(false);
				sessions.remove(bot.getObjectId()); homes.remove(bot.getObjectId());
				session.lease().close();
				if (session.owner().isOnline()) {
					if (!session.generated()) {
						try { session.owner().getAccount().addPlayerAccountData(AccountService.loadPlayerAccountData(bot.getObjectId())); }
						catch (Exception e) { log.warn("Saved companion roster refresh failed for {}", bot.getName(), e); }
					}
					PacketSendUtility.sendMessage(session.owner(), "Companion dismissed and saved: " + bot.getName() + ".");
				}
			} catch (Exception e) {
				// Hold the reservation until a retry succeeds. Never allow a second copy to overwrite unsaved progress.
				log.error("Companion {} is held pending a successful save", bot.getName(), e);
				PacketSendUtility.sendMessage(session.owner(), "Companion " + bot.getName() + " is waiting for a save retry. Its character remains reserved.");
			}
		}
	}

	public synchronized boolean relocate(PlayerBotSession session) {
		return PlayerBotTransfers.relocate(session);
	}

	private boolean canEnter(Player owner, WorldMapInstance destination) {
		// The owner has already entered through the native admission path. Bot
		// recruitment/following is not restricted by PvP flags or map allowlists.
		return destination != null;
	}

	public boolean isReserved(Player caster, com.aionemu.gameserver.model.gameobjects.Creature target, SkillKind kind) {
		Reservation r = reservations.get(new ReservationKey(caster.getPlayerBotOwnerId(), target.getObjectId(), kind));
		return r != null && r.caster() != caster.getObjectId() && r.until() > System.currentTimeMillis();
	}

	public void reserve(Player caster, com.aionemu.gameserver.model.gameobjects.Creature target, SkillKind kind, long until) {
		if (kind == SkillKind.HEAL || kind == SkillKind.RESURRECT || kind == SkillKind.CLEANSE || kind == SkillKind.CONTROL || kind == SkillKind.TAUNT)
			reservations.put(new ReservationKey(caster.getPlayerBotOwnerId(), target.getObjectId(), kind), new Reservation(caster.getObjectId(), until));
	}

	public synchronized void shutdown() {
		shuttingDown = true;
		if (task != null) task.cancel(false);
		if (saveTask != null) saveTask.cancel(false);
		List.copyOf(sessions.values()).forEach(this::dismiss);
	}
}
