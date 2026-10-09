/*
 * Strategy / trigger wiring and relevance bands adapted on 2026-10-03 from
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c (HealPriestStrategy,
 * TankAssistStrategy, DpsAssistStrategy). SPDX-License-Identifier: GPL-2.0-or-later
 * Upstream contributors: third-party/playerbots/AUTHORS.md. Aion actions are local adapters.
 */
package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

import java.util.*;
import java.util.function.BooleanSupplier;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.restrictions.PlayerRestrictions;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.skillengine.properties.FirstTargetAttribute;
import com.aionemu.gameserver.skillengine.properties.TargetRangeAttribute;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

public final class PlayerBotSession {
	private final Player owner, bot;
	private final PlayerBotLease lease;
	private final boolean generated;
	private final PlayerBotEngine engine = new PlayerBotEngine();
	private final PlayerBotNavigation navigation;
	private final PlayerBotPets pets = new PlayerBotPets();
	private final PlayerBotLoot loot = new PlayerBotLoot();
	private final PlayerBotQuests quests = new PlayerBotQuests();
	private final PlayerBotThreat threat = new PlayerBotThreat();
	private boolean withholdDamage;
	private PlayerBotMission mission;
	private final int formationSlot;
	private List<PlayerBotSkills.Entry> skills = List.of();
	private long nextSkillRefresh, nextAttack, nextDecision;
	private volatile Role role;
	private Order order = Order.FOLLOW;
	private boolean areaSkills;
	private boolean consumables = true;
	private boolean autoGear;
	private boolean autoLoot;
	private boolean questing;
	private boolean restorePassives;
	private long nextGearCheck;
	private static final PlayerBotPreferences PREFERENCES = new PlayerBotPreferences(java.nio.file.Path.of("config", "playerbots"));
	private int commandedTarget;
	private int questTarget;
	private PlayerBotNavigation.Point guardPosition;
	private String status = "ready";
	private volatile boolean closing;
	private int failures;
	private long ownerTransferStarted;
	private java.util.concurrent.Future<?> chargeRelease;

	PlayerBotSession(Player owner, Player bot, PlayerBotLease lease, int formationSlot, boolean generated) {
		this.owner = owner; this.bot = bot; this.lease = lease; this.formationSlot = formationSlot;
		this.generated = generated;
		restorePassives = bot.isDead();
		role = roleFor(bot.getPlayerClass());
		navigation = new PlayerBotNavigation(bot);
		try { applyPreferences(PREFERENCES.load(owner.getAccount().getId(), bot.getObjectId(), role)); }
		catch (java.io.IOException e) { throw new IllegalArgumentException("Cannot load companion settings: " + e.getMessage(), e); }
	}

	public Player bot() { return bot; }
	public Player owner() { return owner; }
	public Role combatRole() { return role; }
	public synchronized Map<String, Object> snapshot() {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("appearanceEnabled", PlayerBotAppearance.enabled());
		state.put("name", bot.getName()); state.put("id", bot.getObjectId()); state.put("level", bot.getLevel());
		state.putAll(PlayerBotSpacing.snapshot(bot,role));
		state.put("playerClass", bot.getPlayerClass().name()); state.put("role", role.name()); state.put("order", order.name());
		state.put("health", Math.round(hp(bot))); state.put("mana", Math.round(mp(bot))); state.put("dead", bot.isDead());
		state.put("closing", closing); state.put("status", status); state.put("action", engine.getLastAction());
		state.put("mission", mission == null ? 0 : mission.quest); state.put("missionStatus", mission == null ? "" : mission.status());
		state.put("area", areaSkills); state.put("supplies", consumables); state.put("gear", generated && autoGear); state.put("loot", autoLoot); state.put("questing", questing);
		state.put("generated",generated);state.put("temporary",generated);state.put("characterType",generated ? "Temporary Bot" : "Player-owned alt");
		state.put("build",generated ? PlayerBotTemporary.description(bot) : "Your existing build is preserved");
		state.putAll(PlayerBotQuestSync.snapshot(this));
		state.putAll(PlayerBotGearPolicy.snapshot(this));
		state.put("inventory", PlayerBotAppearance.inventory(this,java.util.stream.Stream.concat(bot.getEquipment().getEquippedItems().stream(), bot.getInventory().getItems().stream())
			.map(item -> {
				long mask = item.getItemTemplate().getItemSlot();
				// Ordinary cube items have no equipment slots; retain them in the panel.
				return Map.of("id", item.getObjectId(), "itemId", item.getItemId(), "name", item.getItemTemplate().getName(), "count", item.getItemCount(),
					"equipped", item.isEquipped(), "slots", mask == 0 ? List.of() : java.util.Arrays.stream(com.aionemu.gameserver.model.items.ItemSlot.getSlotsFor(mask)).map(Enum::name).toList());
			}).toList()));
		state.put("quests", bot.getQuestStateList().getUncompletedQuests().stream().filter(q -> q.getStatus() == com.aionemu.gameserver.questEngine.model.QuestStatus.START || q.getStatus() == com.aionemu.gameserver.questEngine.model.QuestStatus.REWARD)
			.map(q -> PlayerBotQuestJournal.describe(owner, bot, q)).toList());
		return state;
	}
	boolean generated() { return generated; }
	PlayerBotLease lease() { return lease; }
	boolean closing() { return closing; }
	void markClosing() { PlayerBotSteelRake.close(bot); PlayerBotTrade.close(this); PlayerBotAppearance.close(this); closing = true; PlayerBotArbitration.clear(engine); PlayerBotQuestRoutes.close(bot); PlayerBotRevival.close(bot); cancelCharge(); navigation.stop(); pets.release(bot); bot.getObserveController().notifyMoveObservers(); bot.getController().cancelCurrentSkill(null); PlayerBotQuestSync.close(this); }
	private void cancelCharge() { if (chargeRelease != null) { chargeRelease.cancel(false); chargeRelease = null; } }
	void releasePet() { pets.release(bot); }
	int failed() { return ++failures; }
	void resetFailures() { failures = 0; }

	public synchronized void setRole(Role role) { configure(new PlayerBotPreferences.Values(role, areaSkills, consumables, autoGear, autoLoot, questing)); }
	public synchronized void setAreaSkills(boolean enabled) { configure(new PlayerBotPreferences.Values(role, enabled, consumables, autoGear, autoLoot, questing)); }
	public synchronized void setConsumables(boolean enabled) { configure(new PlayerBotPreferences.Values(role, areaSkills, enabled, autoGear, autoLoot, questing)); }
	public synchronized void setAutoGear(boolean enabled) { if(!generated && enabled)throw new IllegalArgumentException("Automatic gear is reserved for Temporary Bots; your alt's build is preserved."); configure(new PlayerBotPreferences.Values(role, areaSkills, consumables, enabled, autoLoot, questing)); nextGearCheck = 0; }
	public synchronized void setAutoLoot(boolean enabled) { configure(new PlayerBotPreferences.Values(role, areaSkills, consumables, autoGear, enabled, questing)); }
	public synchronized void setQuesting(boolean enabled) {
		configure(new PlayerBotPreferences.Values(role, areaSkills, consumables, autoGear, autoLoot, enabled));
		if (!enabled) { mission = null; questTarget = 0; navigation.stop(); }
	}
	private void configure(PlayerBotPreferences.Values values) {
		if (closing) throw new IllegalArgumentException("Companion is waiting for dismissal/save.");
		try { PREFERENCES.save(owner.getAccount().getId(), bot.getObjectId(), values); }
		catch (java.io.IOException e) { throw new IllegalArgumentException("Companion settings could not be saved: " + e.getMessage(), e); }
		applyPreferences(values);
	}
	private void applyPreferences(PlayerBotPreferences.Values values) {
		// Reapplying an unchanged menu value is not a new engine context. In
		// particular it must not discard a pending skill-chain continuation.
		if (role != values.role() || areaSkills != values.area() || consumables != values.supplies()
			|| autoGear != values.gear() || autoLoot != values.loot() || questing != values.questing())
			PlayerBotArbitration.clear(engine);
		role = values.role(); areaSkills = values.area(); consumables = values.supplies(); autoGear = values.gear(); autoLoot = values.loot();
		questing = values.questing();
	}

	public synchronized List<String> inventory() {
		return java.util.stream.Stream.concat(bot.getEquipment().getEquippedItems().stream(), bot.getInventory().getItems().stream())
			.map(item -> "obj=" + item.getObjectId() + " item=" + item.getItemId() + " x" + item.getItemCount()
				+ (item.isEquipped() ? " equipped " + java.util.Arrays.toString(com.aionemu.gameserver.model.items.ItemSlot.getSlotsFor(item.getEquipmentSlot())) : " cube"))
			.toList();
	}

	public synchronized void equip(int itemId, com.aionemu.gameserver.model.items.ItemSlot slot) {
		if (bot.isTrading()) throw new IllegalArgumentException("Finish the companion trade before changing its equipment.");
		if (closing || bot.isDead() || bot.isCasting() || bot.getAggroList().stream().findAny().isPresent()) throw new IllegalArgumentException("Equip while the companion is alive and out of combat.");
		var item = bot.getInventory().getItemByObjId(itemId);
		if (item == null) throw new IllegalArgumentException("That object is not in the companion's cube. Use .bot inventory <name>.");
		if (item.getItemTemplate().isSoulBound() && !item.isSoulBound()) throw new IllegalArgumentException("Log in to the character to bind this item first.");
		navigation.stop();
		if (bot.getEquipment().equipItem(itemId, slot.getSlotIdMask()) == null) throw new IllegalArgumentException("That item cannot be equipped in that slot under normal character rules.");
		nextSkillRefresh = 0;
	}
	public synchronized void order(Order value) {
		if (closing) throw new IllegalArgumentException("Companion is waiting for dismissal/save.");
		Objects.requireNonNull(value, "Companion order");
		// An unchanged command must not abort the committed native cast. A
		// repeated FOLLOW still clears an explicit mission/attack when present.
		if (order == value && mission == null && commandedTarget == 0 && !PlayerBotRecall.recalling(this)) return;
		PlayerBotArbitration.clear(engine);
		stand();
		order = value; mission = null; commandedTarget = 0; questTarget = 0; nextDecision = 0;
		PlayerBotPartyBehavior.update(this, role, value);
		cancelCharge();
		navigation.stop(); pets.stop(bot); bot.setTarget(null); bot.getController().cancelCurrentSkill(null);
		guardPosition = new PlayerBotNavigation.Point(bot.getX(), bot.getY(), bot.getZ());
	}
	public synchronized void mission(int questId) {
		if (questId == 0) { order(Order.FOLLOW); return; }
		if (closing || bot.isDead() || owner.isInInstance()) throw new IllegalArgumentException("Assign missions while alive in an open-world map.");
		var assigned = new PlayerBotMission(bot, questId);
		setQuesting(true); order(Order.FOLLOW); mission = assigned;
	}
	public synchronized void attackSelectedTarget() {
		if (closing) throw new IllegalArgumentException("Companion is waiting for dismissal/save.");
		if (!(owner.getTarget() instanceof Npc npc) || !validEnemy(npc, List.of(owner, bot), true))
			throw new IllegalArgumentException("Select a living hostile PvE NPC within 45m.");
		PlayerBotArbitration.clear(engine);
		if (order == Order.PASSIVE) order = Order.FOLLOW;
		stand();
		commandedTarget = npc.getObjectId(); nextDecision = 0;
	}

	synchronized boolean allowsEnemy(Npc npc) {
		return !closing && bot.getPlayerGroup() != null && validEnemy(npc, bot.getPlayerGroup().getMembers(), explicitTarget(npc));
	}

	public synchronized String describe() {
		return bot.getName() + " Lv" + bot.getLevel() + " " + bot.getPlayerClass() + " / " + role + " / " + order
			+ " / " + engine.getState() + " / " + status + " / " + engine.getLastAction()
			+ " / " + Math.round(hp(bot)) + "% HP / AoE " + (areaSkills ? "on" : "off")
			+ " / supplies " + (consumables ? "on" : "off") + " / gear " + (autoGear ? "auto" : "manual")
			+ " / loot " + (autoLoot ? "on" : "off") + " / questing " + (questing ? "on" : "off") + " / " + navigation.status();
	}

	synchronized boolean tick() {
		if (closing) return true;
		PlayerBotArbitration.context(engine, List.of(owner.getWorldId(), owner.getInstanceId(),
			bot.getWorldId(), bot.getInstanceId(), owner.isSpawned(), bot.isSpawned(), owner.isDead(), bot.isDead(),
			owner.isFlying(), bot.isFlying(), role, order, commandedTarget, mission == null ? 0 : mission.quest,
			owner.getPlayerGroup() == null ? List.of() : owner.getPlayerGroup().getMembers().stream().map(Player::getObjectId).sorted().toList()));
		if (!owner.isOnline()) return false;
		// Distance recovery outranks trading, casting, quests and stale movement.
		if (PlayerBotRecall.automatic(this)) { status="recalling to owner and resuming follow"; return true; }
		if (PlayerBotTrade.tick(this)) { PlayerBotArbitration.clear(engine); navigation.stop(); pets.stop(bot); status="trading with owner"; return true; }
		if (PlayerBotTransfers.follow(this)) { PlayerBotArbitration.clear(engine); navigation.stop(); pets.stop(bot); status="following the complete party map transfer"; return true; }
		if (bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController move && move.hasFailed()) {
			status = "movement failed: dismissing"; return false;
		}
		if (!owner.isOnline() || !bot.isSpawned() || bot.getPosition().getMapRegion() == null
			|| owner.getPlayerGroup() == null || bot.getPlayerGroup() != owner.getPlayerGroup()) return false;
		if (!owner.isSpawned()) {
			if (ownerTransferStarted == 0) ownerTransferStarted = System.currentTimeMillis();
			status = "waiting for owner map transfer"; navigation.stop(); pets.stop(bot);
			return System.currentTimeMillis() - ownerTransferStarted < 30000;
		}
		ownerTransferStarted = 0;
		PlayerBotRecovery.tick(this);
		if (!PlayerBotRevival.ready(bot)) {
			cancelCharge(); restorePassives = true;
			navigation.stop(); pets.stop(bot);
			status = bot.isDead() ? "waiting for resurrection" : "finishing resurrection";
			engine.tick(State.DEAD, List.of(), List.of(), 1);
			return true;
		}
		PlayerBotPartyBehavior.update(this, role, order);
		if (PlayerBotTravel.followTeleport(this)) { PlayerBotArbitration.clear(engine); navigation.stop(); status = "following owner teleport or catching up"; return true; }
		PlayerBotGearPolicy.state(this);
		PlayerBotTemporary.tick(this,autoGear);
		// PB-CUSTOM-APPEARANCE-001 is staged only. Add its tick hook with the
		// complete feature installation, never through an unrelated AI update.
		loot.passRoll(bot);
		List<Player> party = owner.getPlayerGroup().getMembers().stream()
			.filter(p -> p.isPlaying() && p.getWorldId() == bot.getWorldId() && p.getInstanceId() == bot.getInstanceId()).toList();
		navigation.record(owner);
		if (bot.getWorldId() != owner.getWorldId() || bot.getInstanceId() != owner.getInstanceId()) {
			mission = null;
			if (!PlayerBotService.getInstance().relocate(this)) { navigation.stop(); pets.stop(bot); status = "waiting to follow map transfer out of combat"; return true; }
			status = "following map transfer"; return true;
		}
		if (bot.isDead()) {
			cancelCharge();
			restorePassives = true;
			status = "waiting for resurrection";
			navigation.stop(); pets.stop(bot);
			engine.tick(State.DEAD, List.of(), List.of(), 1);
			return true;
		}
		if (restorePassives) {
			for (var learned : bot.getSkillList().getAllSkills()) {
				var template = com.aionemu.gameserver.dataholders.DataManager.SKILL_DATA.getSkillTemplate(learned.getSkillId());
				if (template != null && template.isPassive()) SkillEngine.getInstance().applyEffectDirectly(template, learned.getSkillLevel(), bot, bot);
			}
			restorePassives = false; nextSkillRefresh = 0;
		}
		boolean incapacitated = !bot.canAttack() && !bot.isInState(CreatureState.RESTING);
		long now = System.currentTimeMillis();
		PlayerBotFlight.synchronize(owner, bot, order);
		PlayerBotPartyCompletion.tick(this);
		PlayerBotQuestSync.tick(this, questing);
		PlayerBotCare.observe(this);
		if (mission != null && mission.ended(bot, now)) {
			PacketSendUtility.sendMessage(owner, bot.getName() + ": quest mission ended. Check quest progress in Companions.");
			mission = null; questTarget = 0;
		}
		if (now >= nextSkillRefresh) {
			skills = PlayerBotRotation.rankedSkills(PlayerBotSkills.read(bot)); nextSkillRefresh = now + 10000;
		}
		List<Npc> enemies = new ArrayList<>();
		bot.getKnownList().forEachNpc(npc -> {
			if (validEnemy(npc, party, explicitTarget(npc))) enemies.add(npc);
		});
		boolean questActionsAllowed = questing && order == Order.FOLLOW && !incapacitated && !owner.isDead()
			&& !owner.getMoveController().isInMove()
			&& !owner.isFlying() && !bot.isFlying() && !PlayerBotQuestSync.returning(this) && !bot.isCasting()
			&& !owner.getController().isInCombat() && !bot.getController().isInCombat() && party.stream().allMatch(p -> p.getAggroList().stream().findAny().isEmpty())
			&& !shouldRest(hp(bot), mp(bot), bot.isInState(CreatureState.RESTING), !enemies.isEmpty(), false, PositionUtil.getDistance(bot, owner));
		PlayerBotQuestObjectives.prepare(this,questActionsAllowed && enemies.isEmpty() && !owner.getMoveController().isInMove(),mission!=null);
		if (enemies.isEmpty() && PlayerBotQuestConversations.tick(this,navigation,questActionsAllowed)) { status="advancing an intermediate quest conversation"; return true; }
		var questJob = enemies.isEmpty() && questActionsAllowed ? quests.choose(owner, bot) : null;
		if (PlayerBotQuestObjects.tick(this, navigation, questActionsAllowed && questJob == null)) { status = "collecting quest object"; return true; }
		if (enemies.isEmpty() && questActionsAllowed && mission != null) questJob = quests.missionReward(owner, bot, mission.quest);
		if (enemies.isEmpty()) {
			questTarget = 0;
			if (questActionsAllowed && questJob == null && hp(bot) >= 85 && mp(bot) >= 60) {
				Npc objective = mission == null ? quests.hunt(owner, bot) : quests.hunt(owner, bot, mission.quest);
				if (objective != null) {
					questTarget = objective.getObjectId();
					if (validEnemy(objective, party, true)) enemies.add(objective); else questTarget = 0;
				}
			}
		}
		Npc target = chooseTarget(enemies, party);
		PlayerBotSpacing.observe(bot,target,now);
		withholdDamage = threat.hold(bot, role, target, party, now);
		var hazards = PlayerBotHazards.visible(bot);
		navigation.hazards(hazards);
		pets.tick(bot, target, order != Order.STAY, order != Order.PASSIVE, withholdDamage);
		boolean escapeHazard = PlayerBotHazards.risk(new PlayerBotNavigation.Point(bot.getX(), bot.getY(), bot.getZ()), hazards) > 0
			&& order == Order.FOLLOW && bot.canPerformMove();
		var spread = order == Order.FOLLOW && !incapacitated ? PlayerBotCoordination.spread(bot, party, enemies) : null;
		if ((escapeHazard || spread != null) && bot.isCasting()) {
			// Let recovery/support casts finish; interrupt offensive casts to react to a newly visible hazard.
			var casting = bot.getCastingSkill();
			var kind = PlayerBotSkills.classify(casting.getSkillTemplate());
			if (kind == SkillKind.DAMAGE || kind == SkillKind.CONTROL || kind == SkillKind.TAUNT || casting.getSkillTemplate().isCharge()) {
				cancelCharge(); bot.getController().cancelCurrentSkill(null); nextDecision = 0;
			}
		}
  if(bot.getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.ACTION_ITEM_NPC)) {
   if(escapeHazard || !PlayerBotSteelRake.channeling(bot) && (!enemies.isEmpty() || owner.getController().isInCombat() || owner.getMoveController().isInMove()))bot.getObserveController().notifyMoveObservers();
   else {status="operating encounter device";return true;}
  }
		if (PlayerBotItemUse.pause(bot, !enemies.isEmpty() || owner.getController().isInCombat()
			|| owner.getMoveController().isInMove())) return true;
		if (bot.isCasting() || now < nextDecision) return true;
		boolean combat = !enemies.isEmpty();
		boolean traveling=PlayerBotFollowIntent.traveling(order,combat,owner.getMoveController().isInMove(),!owner.isDead(),incapacitated);
		boolean rest = shouldRest(hp(bot), mp(bot), bot.isInState(CreatureState.RESTING), combat,
			owner.getMoveController().isInMove(), PositionUtil.getDistance(bot, owner));
		rest |= mission != null && !combat && !owner.getMoveController().isInMove() && PositionUtil.isInRange(bot, owner, 8) && (hp(bot) < 85 || mp(bot) < 60);
		if (!rest) stand();
		if (commandedTarget != 0 && enemies.stream().noneMatch(n -> n.getObjectId() == commandedTarget)) commandedTarget = 0;
		var strategyPlan = new PlayerBotStrategyComposition.Plan(combat ? State.COMBAT : State.NON_COMBAT);
		strategyPlan.multiplier(PlayerBotStrategyComposition.threat(withholdDamage));
		List<Trigger> triggers = strategyPlan.triggers("encounter", State.COMBAT, State.NON_COMBAT);
		if (order == Order.FOLLOW && !incapacitated) {
			if (spread != null) triggers.add(trigger(new SimpleAction("spread targeted area cast", () -> true,
				() -> navigation.move(spread.x(), spread.y(), spread.z())), ENCOUNTER + 15));
			var facing = role == Role.TANK ? PlayerBotCoordination.tankFacing(bot, target, party) : null;
			if (facing != null) triggers.add(trigger(new SimpleAction("face enemy away from party", () -> PositionUtil.getDistance(bot, facing.x(), facing.y(), facing.z()) > 1.5,
				() -> navigation.move(facing.x(), facing.y(), facing.z())), MOVE + 3));
		}
		if (escapeHazard) triggers.add(trigger(new SimpleAction("avoid encounter hazard", () -> true, () -> {
			stand();
			if (navigation.escapeHazards()) { status = "avoiding encounter hazard"; return true; }
			status = "escape route blocked"; navigation.stop(); return false;
		}), ENCOUNTER + 20));
  if(order==Order.FOLLOW && !incapacitated){var feeding=PlayerBotSteelRake.feeding(this,navigation,enemies);if(feeding!=null)triggers.add(feeding);}
		Npc protection = PlayerBotEncounters.protection(owner, bot, enemies);
		if (protection != null && order == Order.FOLLOW && !incapacitated)
			triggers.add(trigger(new SimpleAction("acquire Vasharti flame protection", () -> true, () -> {
				stand();
				if (PositionUtil.isInRange(bot, protection, 2)) { navigation.stop(); return true; }
				return navigation.approach(protection, 2);
			}), ENCOUNTER));
		triggers = strategyPlan.triggers("quest interaction", State.NON_COMBAT);
		if (questActionsAllowed && !combat && !rest && (mission == null || !owner.getMoveController().isInMove())) {
			var job = questJob;
			if (job != null) triggers.add(trigger(new Action() {
				@Override public String name() { return (job.turnIn() ? "turn in quest " : "accept quest ") + job.quest(); }
				@Override public boolean isUseful() { return questing && !owner.isFlying() && !PlayerBotQuestSync.returning(PlayerBotSession.this) && PositionUtil.isInRange(owner, job.npc(), mission != null && job.quest() == mission.quest ? PlayerBotMission.range() : 40); }
				@Override public boolean isPossible() { return order == Order.FOLLOW && !bot.isCasting() && !bot.isLooting(); }
				@Override public List<Action> prerequisites() { return !PositionUtil.isInTalkRange(bot, job.npc()) || !GeoService.getInstance().canSee(bot, job.npc())
					? List.of(new ReachAction(job.npc(), 2)) : List.of(); }
				@Override public boolean execute() { navigation.stop(); stand(); return quests.interact(owner, bot, job, role, mission != null && job.quest() == mission.quest); }
			}, DEFAULT + 3));
		}
		triggers = strategyPlan.triggers("loot", State.NON_COMBAT);
		if (autoLoot && !combat && !incapacitated && order != Order.PASSIVE) {
			Npc corpse = loot.choose(owner, bot);
			if (corpse != null) triggers.add(trigger(new Action() {
				@Override public String name() { return "loot " + corpse.getName(); }
				@Override public boolean isUseful() { return corpse.isDead() && corpse.isSpawned(); }
				@Override public boolean isPossible() { return !bot.isCasting() && !bot.isLooting(); }
				@Override public List<Action> prerequisites() { return !PositionUtil.isInRange(bot, corpse, 3) || !GeoService.getInstance().canSee(bot, corpse)
					? List.of(new ReachAction(corpse, 2.5f)) : List.of(); }
				@Override public boolean execute() { navigation.stop(); stand(); return loot.collect(bot, corpse); }
			}, DEFAULT + 2));
		}
		triggers = strategyPlan.triggers("rest", State.NON_COMBAT);
		if (rest && !incapacitated && !bot.isInRobotMode() && !bot.isFlying())
			triggers.add(trigger(new SimpleAction("rest and recover", () -> !bot.isCasting() && !bot.getController().isUnderStance(), () -> {
				navigation.stop();
				if (!bot.isInState(CreatureState.RESTING)) {
					bot.getObserveController().notifySitObservers();
					bot.setState(CreatureState.RESTING);
					PacketSendUtility.broadcastPacketAndReceive(bot, new SM_EMOTION(bot, EmotionType.SIT));
				}
				bot.getLifeStats().triggerRestoreTask();
				return true;
			}), HIGH - 1));
		triggers = strategyPlan.triggers("equipment", State.NON_COMBAT);
		if (generated && autoGear && !combat && !incapacitated && now >= nextGearCheck) {
			nextGearCheck = now + 5000;
			for (var upgrade : PlayerBotEquipment.upgrades(bot, role))
				triggers.add(trigger(new SimpleAction("equip upgrade " + upgrade.item().getItemId(), () -> !bot.isCasting(), () -> {
					navigation.stop();
					if (!PlayerBotGearPolicy.equip(this, upgrade)) return false;
					nextSkillRefresh = 0; nextGearCheck = 0;
					return true;
				}), DEFAULT - 1));
		}
		triggers = strategyPlan.triggers("care and services", State.NON_COMBAT);
		if (generated && !combat && !incapacitated && !rest && order == Order.FOLLOW) triggers.add(PlayerBotCare.trigger(this, navigation));
		if (generated && autoGear && !combat && !incapacitated && !rest && order == Order.FOLLOW) triggers.add(PlayerBotGearPolicy.trigger(this, navigation));
		triggers = strategyPlan.triggers("supplies", State.NON_COMBAT, State.COMBAT);
		if (consumables && !incapacitated)
			for (var item : PlayerBotConsumables.candidates(bot))
				triggers.add(trigger(new SimpleAction("use recovery item " + item.getItemId(), () -> true, () -> {
					if (!PlayerBotConsumables.use(bot, item)) return false;
					nextDecision = System.currentTimeMillis() + 600;
					return true;
				}), hp(bot) < 30 ? EMERGENCY + 4 : HIGH + 4));
		triggers = strategyPlan.triggers(PlayerBotCleric.strategy(bot.getPlayerClass()), State.NON_COMBAT, State.COMBAT);
		strategyPlan.enable(PlayerBotCleric.strategy(bot.getPlayerClass()), order != Order.PASSIVE);
		if (order != Order.PASSIVE) {
			for (PlayerBotSkills.Entry entry : skills) {
				if (incapacitated && entry.kind() != SkillKind.RECOVERY || bot.isSkillDisabled(entry.template()) || !PlayerBotSkills.chainAvailable(bot, entry)) continue;
				Creature recipient = recipient(entry, party, target, combat);
				if (recipient == null) continue;
				double score = priority(entry, recipient, combat, party);
				if (score > 0) triggers.add(trigger(PlayerBotStrategyComposition.skill(
					new CastAction(entry, recipient, enemies), bot, entry, recipient, skills), score));
			}
			triggers = strategyPlan.triggers("combat positioning and attack", State.COMBAT);
			if (target != null && !incapacitated) {
				float range = Math.max(1.5f, bot.getGameStats().getAttackRange().getCurrent() / 1000f);
				float desired = PlayerBotCombatPosition.desired(bot,role,skills.stream()
					.filter(e -> e.kind()==SkillKind.DAMAGE || e.kind()==SkillKind.HEAL).toList());
				if (PlayerBotCombatPosition.ranged(bot.getPlayerClass(),role) && PlayerBotCombatPosition.tooClose(bot,target,desired))
					triggers.add(trigger(new SimpleAction("keep spell distance", () -> order == Order.FOLLOW && bot.canPerformMove(), () -> {
						return PlayerBotSpacing.retreat(owner,bot,target,navigation);
					}), MOVE + 2));
				triggers.add(trigger(new ReachAction(target, desired), MOVE));
				triggers.add(trigger(new SimpleAction("auto attack", () -> validEnemy(target, party, explicitTarget(target))
					&& !withholdDamage
					&& PlayerBotEncounters.allowsAttack(bot, target)
					&& System.currentTimeMillis() >= nextAttack && PositionUtil.isInAttackRange(bot, target, range)
					&& GeoService.getInstance().canSee(bot, target), () -> {
					navigation.stop(); bot.setTarget(target);
					int speed = Math.max(300, bot.getGameStats().getAttackSpeed().getCurrent());
					bot.getController().attackTarget(target, Math.min(500, speed / 3), false);
					nextAttack = System.currentTimeMillis() + speed;
					nextDecision = System.currentTimeMillis() + Math.min(500, speed / 3);
					return true;
				}), DEFAULT));
			}
		}
		triggers = strategyPlan.triggers("quest travel", State.NON_COMBAT);
		if (mission != null && questActionsAllowed && !combat && !rest && questJob == null && hp(bot) >= 85 && mp(bot) >= 60) {
			var destination = mission.destination(owner, bot, now);
			if (destination != null) triggers.add(trigger(new SimpleAction("quest mission travel", () -> mission != null, () -> {
				boolean moved = navigation.move(destination.point().x(), destination.point().y(), destination.point().z());
				mission.status(moved ? "traveling to NPC " + destination.npc() : "quest route blocked or destination not visible"); return moved;
			}), DEFAULT + 1));
		}
		if (mission==null && questActionsAllowed && !combat && !rest && questJob==null && hp(bot)>=85 && mp(bot)>=60 && !owner.getMoveController().isInMove()) {
			var route=PlayerBotQuestRoutes.trigger(this,navigation);if(route!=null)triggers.add(route);
		}
		triggers = strategyPlan.triggers("follow and guard", State.NON_COMBAT);
		if (!incapacitated && !combat && !owner.isDead() && (order == Order.FOLLOW || order == Order.PASSIVE) && (mission == null || hp(bot) < 85 || mp(bot) < 60 || owner.getMoveController().isInMove()))
			strategyPlan.defaults("follow and guard", new SimpleAction("follow owner", () -> PlayerBotFormation.needsFollow(owner,bot,formationSlot),
				() -> navigation.follow(owner, formationSlot)), PlayerBotFollowIntent.priority(traveling), State.NON_COMBAT);
		if (!incapacitated && !combat && order == Order.GUARD && guardPosition != null)
			strategyPlan.defaults("follow and guard", new SimpleAction("return to guard", () -> PositionUtil.getDistance(bot, guardPosition.x(), guardPosition.y(), guardPosition.z()) > 2,
				() -> navigation.move(guardPosition.x(), guardPosition.y(), guardPosition.z())), DEFAULT, State.NON_COMBAT);
		status = incapacitated ? "crowd controlled" : withholdDamage ? "waiting for tank threat" : combat ? "engaged" : rest ? "resting" : "ready";
		strategyPlan.tick(engine, 64);
		return true;
	}

	private Npc chooseTarget(List<Npc> enemies, List<Player> party) {
  if(commandedTarget==0){Npc add=PlayerBotSteelRake.adds(enemies);if(add!=null)return add;}
  return PlayerBotTargetValues.choose(bot,owner,role,commandedTarget,enemies.stream().filter(PlayerBotSteelRake::attackable).toList(),party,skills);
	}

	private boolean validEnemy(Npc npc, List<Player> party, boolean explicit) {
		// Territory flags are client control objects and their native AI rejects all damage.
		// Their opposing race/quest metadata must never turn them into a combat target.
		if (npc.isFlag() || PlayerBotSteelRakeCaptainTactics.scenery(npc.getNpcId())) return false;
		boolean engaged = party.stream().anyMatch(p -> npc.getAggroList().isHating(p)
			|| p.getSummon() != null && npc.getAggroList().isHating(p.getSummon()));
		return bot.getKnownList().sees(npc) && PlayerBotRules.canAttack(true, bot.isEnemy(npc), !npc.isDead(), engaged,
			explicit, controlled(npc), order == Order.PASSIVE, PositionUtil.getDistance(owner, npc), mission == null ? 45 : PlayerBotMission.range())
			&& npc.getWorldId() == bot.getWorldId() && npc.getInstanceId() == bot.getInstanceId()
			&& npc.getMaster() == npc && (order != Order.GUARD || guardPosition == null
				|| PositionUtil.getDistance(npc, guardPosition.x(), guardPosition.y(), guardPosition.z()) <= 20);
	}
	private boolean explicitTarget(Npc npc) {
		if (questing && PlayerBotPartyBehavior.explicit(bot, npc)) return true;
		if (mission != null && questing && Boolean.TRUE.equals(PlayerBotPartyBehavior.snapshot(this).get("questCombat")) && npc.getObjectId() == questTarget && order == Order.FOLLOW && !owner.isDead()
			&& !owner.getMoveController().isInMove() && PositionUtil.isInRange(owner, npc, PlayerBotMission.range()))
			return com.aionemu.gameserver.questEngine.QuestEngine.getInstance().getRequiredKillNpcIds(bot, mission.quest).contains(npc.getNpcId());
		return npc.getObjectId() == commandedTarget || questing && npc.getObjectId() == questTarget && order == Order.FOLLOW
			&& Boolean.TRUE.equals(PlayerBotPartyBehavior.snapshot(this).get("questCombat")) && !owner.isDead() && !owner.getMoveController().isInMove() && PositionUtil.isInRange(owner, npc, 25);
	}

	private Creature recipient(PlayerBotSkills.Entry e, List<Player> party, Npc enemy, boolean combat) {
		if (e.kind() == SkillKind.PET_ORDER) {
			var pet = bot.getSummon();
			if (pet == null || pet.isDead() || pet.isBeingReleased() || pet.getNextSkillOrder() != null) return null;
			var actual = PlayerBotSkills.actualTemplate(bot, e);
			if (actual == null) return null;
			SkillKind kind = PlayerBotSkills.classify(actual);
			if (kind == SkillKind.DAMAGE || kind == SkillKind.TAUNT || kind == SkillKind.CONTROL) return enemy;
			Creature friendly = actual.getProperties().getFirstTarget() == FirstTargetAttribute.MYMASTER ? bot : pet;
			return switch (kind) {
				case HEAL -> hp(friendly) < 85 ? friendly : null;
				case MANA -> mp(friendly) < 40 ? friendly : null;
				case DEFENSE -> hp(friendly) < 35 ? friendly : null;
				case BUFF -> !hasActualBuff(friendly, actual) ? friendly : null;
				case CLEANSE -> PlayerBotSkills.canCleanse(new PlayerBotSkills.Entry(actual, actual.getLvl(), kind, e.range()), friendly) ? friendly : null;
				default -> null;
			};
		}
		if (e.template().getProperties().getFirstTarget() == FirstTargetAttribute.MYPET) {
			var pet = bot.getSummon();
			if (pet == null || pet.isDead() || pet.isBeingReleased()) return null;
			return switch (e.kind()) {
				case HEAL -> hp(pet) < 85 ? pet : null;
				case CLEANSE -> PlayerBotSkills.canCleanse(e, pet) ? pet : null;
				case DEFENSE -> hp(pet) < 35 && !hasBuff(pet, e) ? pet : null;
				case BUFF -> !hasBuff(pet, e) && canAddBuff(pet, e) ? pet : null;
				default -> null;
			};
		}
		if (e.kind() == SkillKind.DEFENSE) return PlayerBotDefense.recipient(bot, e, party, combat);
		if (e.kind() == SkillKind.DAMAGE || e.kind() == SkillKind.CONTROL) {
			Creature pursuer = PlayerBotDefense.peelRecipient(bot, role, e, enemy);
			return pursuer != null ? pursuer : PlayerBotEnemyUtility.recipient(bot, e, enemy);
		}
		if (e.kind() == SkillKind.TAUNT) return enemy;
		if (PlayerBotHealing.managed(e)) return PlayerBotHealing.recipient(bot, e, party);
		if (e.kind() == SkillKind.RESURRECT) return PlayerBotHealing.resurrection(bot, e, party);
		List<Player> allowed = PlayerBotSupport.allowed(bot, e, party);
		return switch (e.kind()) {
			case SUMMON -> !combat && bot.getSummon() == null ? bot : null;
			case MODE -> !bot.isInRobotMode() && bot.getEquipment().getMainHandWeapon() != null
				&& bot.getEquipment().getMainHandWeapon().getItemSkinTemplate().getRobotId() > 0 ? bot : null;
			case RECOVERY -> bot.getEffectController().isInAnyAbnormalState(AbnormalState.CANT_ATTACK_STATE)
				|| bot.getEffectController().isInAnyAbnormalState(AbnormalState.CANT_MOVE_STATE) ? bot : null;
			case HEAL -> allowed.stream().filter(p -> !p.isDead() && hp(p) < 85 && !PlayerBotService.getInstance().isReserved(bot, p, SkillKind.HEAL))
				.min(Comparator.comparingDouble(PlayerBotSession::hp)).orElse(null);
			case MANA -> allowed.stream().filter(p -> !p.isDead() && mp(p) < 40).min(Comparator.comparingDouble(PlayerBotSession::mp)).orElse(null);
			case CLEANSE -> allowed.stream().filter(p -> !p.isDead() && !PlayerBotService.getInstance().isReserved(bot, p, SkillKind.CLEANSE)
				&& PlayerBotDispel.score(e, p) > 0).max(Comparator.comparingDouble(p -> PlayerBotDispel.score(e, p))).orElse(null);
			case RESURRECT -> combat ? null : allowed.stream().filter(p -> p.isDead() && !p.getResStatus()).findFirst().orElse(null);
			case DEFENSE -> allowed.stream().filter(p -> !p.isDead() && hp(p) < 35 && !hasBuff(p, e)).min(Comparator.comparingDouble(PlayerBotSession::hp)).orElse(null);
			case BUFF -> combat && !PlayerBotCombatBuffs.useful(bot.getPlayerClass(), role, e.template(), hp(bot), mp(bot)) ? null : allowed.stream().filter(p -> !p.isDead() && !hasBuff(p, e) && canAddBuff(p, e))
				.findFirst().orElse(null);
			default -> null;
		};
	}

	private double priority(PlayerBotSkills.Entry e, Creature recipient, boolean combat, List<Player> party) {
		double classSupport = PlayerBotSorcerer.support(bot,role,e,recipient,combat);
		if (!Double.isNaN(classSupport)) return classSupport;
		if (e.kind() == SkillKind.DEFENSE) return PlayerBotDefense.priority(bot, e, recipient, combat, party);
		if (e.kind() == SkillKind.DAMAGE && PlayerBotEnemyUtility.purge(e.template())) {
			if (withholdDamage || role == Role.HEALER && PlayerBotEnemyUtility.injured(party)) return 0;
			double purge = PlayerBotEnemyUtility.purgePriority(bot.getPlayerClass(), PlayerBotEnemyUtility.purgeScore(bot, e, recipient));
			if (purge > 0 || !PlayerBotEnemyUtility.damagePayload(e.template())) return purge;
		}
		return switch (e.kind()) {
			case SUMMON -> HIGH + 4 + Math.min(3, Math.max(0, e.template().getLvl()) / 5.0);
			case PET_ORDER -> petOrderPriority(e, recipient);
			case MODE -> MOVE + 4;
			case RECOVERY -> EMERGENCY + 10;
			case HEAL -> PlayerBotHealing.priority(bot, e, recipient, party);
			case MANA -> HIGH + 1;
			case DEFENSE -> EMERGENCY + 1;
			case CLEANSE -> PlayerBotHealing.priority(bot, e, recipient, party);
			case RESURRECT -> HIGH;
			case BUFF -> (combat && role==Role.TANK && PlayerBotTank.hateBuff(e.template()) ? DISPEL+2 : combat ? HIGH + 5 : NORMAL) + Math.min(2, Math.max(0, e.template().getLvl()) / 10.0);
			case TAUNT -> role == Role.TANK ? PlayerBotTank.priority(bot,e,recipient,party) : 0;
			case CONTROL -> Math.max(PlayerBotSkills.canInterrupt(e.template()) ? PlayerBotEnemyUtility.interruptPriority(bot.getPlayerClass(), recipient) : 0,
				PlayerBotDefense.peelPriority(bot, role, e, recipient));
			case DAMAGE -> withholdDamage && !(recipient.isCasting() && PlayerBotSkills.canInterrupt(e.template())) ? 0
				: role == Role.HEALER && party.stream().anyMatch(p -> !p.isDead() && hp(p) < 85) ? 0
				: Math.max((recipient.isCasting() && PlayerBotSkills.canInterrupt(e.template()) ? PlayerBotEnemyUtility.interruptPriority(bot.getPlayerClass(), recipient)
					+ damageFitness(e, recipient) : PlayerBotOffense.routine(bot,e,recipient,skills,damageFitness(e,recipient)))
					+ (role==Role.TANK ? PlayerBotTank.priority(bot,e,recipient,party) : 0), PlayerBotDefense.peelPriority(bot, role, e, recipient));
			default -> 0;
		};
	}

	private double damageFitness(PlayerBotSkills.Entry e, Creature recipient) {
		var weapon = bot.getEquipment().getMainHandWeapon();
		double baseDamage = weapon == null ? bot.getGameStats().getStatsTemplate().getAttack()
			: weapon.getItemTemplate().getWeaponStats().getMeanDamage();
		var context = new PlayerBotRotation.Context(bot.getPlayerClass(), role, hp(bot), mp(bot), hp(recipient),
			recipient.getLifeStats().getMaxHp(), baseDamage, 1, recipient.isCasting(), recipient.getTarget() instanceof Player p && roleFor(p.getPlayerClass()) == Role.TANK);
		var actual = PlayerBotSkills.actualTemplate(bot, e);
		return PlayerBotRotation.score(actual, e.level(), context);
	}
	private double petOrderPriority(PlayerBotSkills.Entry entry, Creature target) {
		var actual = PlayerBotSkills.actualTemplate(bot, entry);
		if (actual == null) return 0;
		if (PlayerBotEnemyUtility.purge(actual)) {
			if (withholdDamage) return 0;
			double purge = PlayerBotEnemyUtility.purgePriority(bot.getPlayerClass(), PlayerBotEnemyUtility.purgeScore(bot.getSummon(),
				new PlayerBotSkills.Entry(actual, actual.getLvl(), PlayerBotSkills.classify(actual), entry.range()), target));
			if (purge > 0 || !PlayerBotEnemyUtility.damagePayload(actual)) return purge;
		}
		return switch (PlayerBotSkills.classify(actual)) {
			case HEAL -> healPriority(hp(target), false);
			case MANA -> HIGH + 1;
			case DEFENSE -> EMERGENCY + 1;
			case CLEANSE -> DISPEL + PlayerBotDispel.score(new PlayerBotSkills.Entry(actual, actual.getLvl(), SkillKind.CLEANSE, entry.range()), target);
			case TAUNT -> target.getTarget() == bot || target.getTarget() == bot.getSummon() ? 0 : DISPEL;
			case CONTROL -> target.isCasting() ? INTERRUPT : 0;
			case DAMAGE -> withholdDamage && !(target.isCasting() && PlayerBotSkills.canInterrupt(actual)) ? 0
				: (target.isCasting() && PlayerBotSkills.canInterrupt(actual) ? INTERRUPT : HIGH) + damageFitness(entry, target);
			case BUFF -> NORMAL;
			default -> 0;
		};
	}

	private boolean safeArea(PlayerBotSkills.Entry e, Creature target, List<Npc> engaged) {
		if (e.template().isCharge()) {
			var condition = e.template().getSkillChargeCondition();
			var stages = com.aionemu.gameserver.dataholders.DataManager.SKILL_CHARGE_DATA.getChargedSkillEntry(condition.getValue());
			if (stages == null) return false;
			for (var stage : stages.getSkills())
				if (!safeAreaTemplate(com.aionemu.gameserver.dataholders.DataManager.SKILL_DATA.getSkillTemplate(stage.getId()), target, engaged, bot)) return false;
			return true;
		}
		var actual = PlayerBotSkills.actualTemplate(bot, e);
		return safeAreaTemplate(actual, target, engaged, e.kind() == SkillKind.PET_ORDER ? bot.getSummon() : bot);
	}
	private boolean safeAreaTemplate(com.aionemu.gameserver.skillengine.model.SkillTemplate template, Creature target, List<Npc> engaged, Creature effector) {
		if (template == null || template.getProperties() == null || effector == null) return false;
		var p = template.getProperties();
		boolean hostile = p.getTargetRelation() == com.aionemu.gameserver.skillengine.properties.TargetRelationAttribute.ENEMY;
		if (!hostile || p.getTargetType() == TargetRangeAttribute.ONLYONE) return true;
		if (!areaSkills) return false;
		float radius = Math.max(5, Math.max(p.getEffectiveDist(), Math.max(p.getEffectiveRange(), p.getTargetDistance())));
		boolean[] safe = { true };
		bot.getKnownList().forEachObject(object -> {
			if (object instanceof Creature other && !other.isDead() && bot.isEnemy(other)
				&& (PositionUtil.isInRange(target, other, radius) || PositionUtil.isInRange(effector, other, radius)))
				if (!(other instanceof Npc npc) || !engaged.contains(npc) || controlled(npc) || !PlayerBotEncounters.allowsAttack(effector, npc)) safe[0] = false;
		});
		return safe[0];
	}

	private final class CastAction implements Action {
		private final PlayerBotSkills.Entry entry;
		private final Creature recipient;
		private final List<Npc> enemies;
		CastAction(PlayerBotSkills.Entry entry, Creature recipient, List<Npc> enemies) { this.entry = entry; this.recipient = recipient; this.enemies = enemies; }
		@Override public String name() { return "cast " + entry.template().getSkillId() + " -> " + recipient.getName(); }
		@Override public boolean isUseful() {
			var actual = PlayerBotSkills.actualTemplate(bot, entry);
			if (actual == null) return false;
			if (!PlayerBotEnemyUtility.useful(bot, entry, recipient)) return false;
			if (!PlayerBotDefense.useful(bot, role, entry, recipient, enemies)) return false;
			if (!PlayerBotOffense.useful(bot, entry, recipient)) return false;
			if (recipient instanceof Npc npc && bot.isEnemy(npc) && (!PlayerBotEncounters.allowsAttack(bot, npc, Math.max(0, entry.template().getDuration()) + 750L)
				|| entry.kind() == SkillKind.PET_ORDER && (bot.getSummon() == null || !PlayerBotEncounters.allowsAttack(bot.getSummon(), npc)))) return false;
			if (entry.kind() == SkillKind.PET_ORDER && !actual.isPassive() && hasActualBuff(recipient, actual)) return false;
			return recipient.isSpawned() && (recipient.isDead() == (entry.kind() == SkillKind.RESURRECT)) && !bot.isCasting()
				// Resource-aware offense usefulness above owns rune decisions; the old
				// ClassCombat gate contradicted four-rune, expiry and no-builder choices.
				&& (entry.kind() != SkillKind.BUFF || canAddBuff(recipient, entry))
				// Periodic/hybrid effects have expiry/rank checks in Offense.useful;
				// nonperiodic attacks retain the existing duplicate-debuff veto.
				&& (entry.kind() != SkillKind.DAMAGE || PlayerBotOffense.periodic(actual) || !hasActualBuff(recipient, actual))
				&& (!recipient.isCasting() || !PlayerBotSkills.canInterrupt(entry.template())
					|| !PlayerBotService.getInstance().isReserved(bot, recipient, SkillKind.CONTROL))
				&& (!PlayerBotHealing.managed(entry) || PlayerBotHealing.useful(bot, entry, recipient))
				&& (entry.kind() != SkillKind.BUFF && entry.kind() != SkillKind.PET_ORDER || !hasBuff(recipient, entry))
				&& (PlayerBotHealing.managed(entry) || !PlayerBotService.getInstance().isReserved(bot, recipient, entry.kind()));
		}
		@Override public boolean isPossible() { return !bot.isSkillDisabled(entry.template()) && PlayerBotSkills.chainAvailable(bot, entry)
			&& (bot.canAttack() || entry.kind() == SkillKind.RECOVERY || bot.isInState(CreatureState.RESTING))
			&& PlayerBotCombatPosition.allowSpellApproach(bot,role,entry,recipient,skills)
			&& PlayerBotSkills.canPlan(bot, entry, recipient, !canFlank(recipient, entry)) && safeArea(entry, recipient, enemies); }
		@Override public List<Action> prerequisites() {
			if (canFlank(recipient, entry) && !PositionUtil.isBehind(bot, recipient)) return List.of(new FlankAction(recipient));
			return recipient != bot && (!PositionUtil.isInRange(bot, recipient, entry.range(),false) || !GeoService.getInstance().canSee(bot, recipient))
				? List.of(new ReachAction(recipient, entry.range())) : List.of();
		}
		@Override public boolean execute() {
			// Cast admission and movement shutdown share the mover's monitor. A
			// scheduled step cannot slip between stopping and publishing the cast.
			synchronized (bot.getMoveController()) {
				if (!isUseful() || !isPossible() || recipient.getWorldId() != bot.getWorldId() || recipient.getInstanceId() != bot.getInstanceId()) return false;
				if (!PlayerBotSkills.canPlan(bot, entry, recipient)) return false;
				if (recipient != bot && (!PositionUtil.isInRange(bot, recipient, entry.range(),false) || !GeoService.getInstance().canSee(bot, recipient))) return false;
				boolean peeling = PlayerBotDefense.peelPriority(bot, role, entry, recipient) > 0;
				navigation.stop(); stand(); bot.setTarget(recipient);
				Skill skill = SkillEngine.getInstance().getSkillFor(bot, entry.template(), recipient);
				if (skill == null || !PlayerRestrictions.canUseSkill(bot, skill)) return false;
				skill.setTargetType(entry.template().getProperties().getFirstTarget() == FirstTargetAttribute.POINT ? 1 : 0,
					recipient.getX(), recipient.getY(), recipient.getZ());
				// Server motion data determines hit time and cast lock; keep animation checks enabled.
				PlayerBotSkillTiming.prepare(bot, skill);
				if (!skill.useSkill()) return false;
				if (entry.template().isCharge()) return scheduleCharge(skill, entry, recipient);
				nextDecision = System.currentTimeMillis() + PlayerBotSkillTiming.remainingLock(bot, skill);
				if (PlayerBotHealing.managed(entry)) PlayerBotHealing.reserve(bot, entry, skill, nextDecision);
				else PlayerBotService.getInstance().reserve(bot, recipient, entry.kind(), nextDecision);
				if (peeling || recipient.isCasting() && PlayerBotSkills.canInterrupt(entry.template()))
					PlayerBotService.getInstance().reserve(bot, recipient, SkillKind.CONTROL, nextDecision);
				return true;
			}
		}
	}

	private boolean scheduleCharge(Skill skill, PlayerBotSkills.Entry entry, Creature target) {
		long delay = PlayerBotCharge.releaseDelay(skill, hp(target));
		if (delay < 0) { bot.getController().cancelCurrentSkill(null); return false; }
		cancelCharge(); nextDecision = System.currentTimeMillis() + delay + 600;
		chargeRelease = com.aionemu.gameserver.utils.ThreadPoolManager.getInstance().schedule(() -> {
			synchronized (PlayerBotSession.this) {
				chargeRelease = null;
				if (closing || bot.getCastingSkill() != skill) return;
				try {
					var group = bot.getPlayerGroup();
					List<Npc> engaged = new ArrayList<>();
					if (group != null) bot.getKnownList().forEachNpc(n -> { if (validEnemy(n, group.getMembers(), explicitTarget(n))) engaged.add(n); });
					if (order == Order.PASSIVE || bot.isDead() || !target.isSpawned() || !PlayerBotService.allowsTarget(bot, target)
						|| target.isDead() || target instanceof Npc npc && !PlayerBotEncounters.allowsAttack(bot, npc) || !safeArea(entry, target, engaged)) {
						bot.getController().cancelCurrentSkill(null); nextDecision = 0; return;
					}
					long elapsed = System.currentTimeMillis() - skill.getCastStartTime();
					if (!bot.getController().useChargeSkill(skill, elapsed, released -> {
						nextDecision = System.currentTimeMillis() + PlayerBotSkillTiming.remainingLock(bot, released);
						PlayerBotService.getInstance().reserve(bot, target, entry.kind(), nextDecision);
						if (target.isCasting() && PlayerBotSkills.canInterrupt(released.getSkillTemplate()))
							PlayerBotService.getInstance().reserve(bot, target, SkillKind.CONTROL, nextDecision);
					})) { bot.getController().cancelCurrentSkill(null); nextDecision = 0; return; }
				} catch (RuntimeException error) {
					org.slf4j.LoggerFactory.getLogger(PlayerBotSession.class).error("Companion charged skill release failed for {}", bot.getObjectId(), error);
					bot.getController().cancelCurrentSkill(null); nextDecision = 0;
				}
			}
		}, delay);
		return true;
	}
	private boolean canFlank(Creature target, PlayerBotSkills.Entry entry) {
		return PlayerBotSkills.requiresBack(entry) && order == Order.FOLLOW && role == Role.MELEE && target.getTarget() != bot;
	}
	private final class FlankAction implements Action {
		private final Creature target;
		FlankAction(Creature target) { this.target = target; }
		@Override public String name() { return "move behind " + target.getName(); }
		@Override public boolean isUseful() { return !PositionUtil.isBehind(bot, target); }
		@Override public boolean isPossible() { return order == Order.FOLLOW && bot.canPerformMove() && target.isSpawned() && target.getTarget() != bot; }
		@Override public boolean execute() {
			double angle = Math.toRadians(target.getHeading() * 3);
			float radius = Math.max(1.5f, target.getObjectTemplate().getBoundRadius().getMaxOfFrontAndSide() + 1);
			return navigation.move(target.getX() - (float) Math.cos(angle) * radius, target.getY() - (float) Math.sin(angle) * radius, target.getZ());
		}
	}

	private final class ReachAction implements Action {
		private final Creature target;
		private final float range;
		ReachAction(Creature target, float range) { this.target = target; this.range = range; }
		@Override public String name() { return "reach " + target.getName(); }
		@Override public boolean isUseful() { return !PositionUtil.isInRange(bot, target, range,false) || !GeoService.getInstance().canSee(bot, target); }
		@Override public boolean isPossible() { return order != Order.STAY && order != Order.PASSIVE && target.isSpawned() && bot.canPerformMove(); }
		@Override public boolean execute() { return navigation.approach(target, Math.max(1, range - 0.5f)); }
	}

	private record SimpleAction(String name, BooleanSupplier useful, BooleanSupplier execution) implements Action {
		@Override public boolean isUseful() { return useful.getAsBoolean(); }
		@Override public boolean isPossible() { return true; }
		@Override public boolean execute() { return execution.getAsBoolean(); }
	}
	private static Trigger trigger(Action action, double score) { return new Trigger(() -> true, action, () -> score); }
	private static boolean controlled(Creature c) {
		return c.getEffectController().isAbnormalSet(AbnormalState.SLEEP) || c.getEffectController().isUnderFear()
			|| c.getEffectController().isAbnormalSet(AbnormalState.PARALYZE);
	}
	static boolean inPvp(Player p) {
		// Retained for binary compatibility with older companion integrations.
		// PvP is not a recruitment, following, support or dismissal restriction.
		return false;
	}
	private static boolean hasBuff(Creature p, PlayerBotSkills.Entry entry) {
		return hasActualBuff(p, entry.template());
	}
	private static boolean hasActualBuff(Creature p, com.aionemu.gameserver.skillengine.model.SkillTemplate template) {
		return p.getEffectController().hasAbnormalEffect(e -> e.getSkillId() == template.getSkillId()
			|| template.getStack() != null && !template.getStack().isEmpty() && !"NONE".equalsIgnoreCase(template.getStack())
				&& Objects.equals(e.getSkillTemplate().getStack(), template.getStack()));
	}
	private boolean canAddBuff(Creature target, PlayerBotSkills.Entry entry) {
		return PlayerBotBuffs.canAdd(bot.getPlayerClass(), entry.template(), target.getEffectController().getAbnormalEffects().stream()
			.map(a -> a.getSkillTemplate()).toList());
	}
	private void stand() {
		if (!bot.isDead() && bot.isInState(CreatureState.RESTING)) {
			bot.unsetState(CreatureState.RESTING);
			PacketSendUtility.broadcastPacketAndReceive(bot, new SM_EMOTION(bot, EmotionType.STAND));
		}
	}
	private double healFitness(PlayerBotSkills.Entry entry, Creature target) {
		// Snapshot the existing heal formula without initialization, chance rolls, observers or effects.
		var effect = new com.aionemu.gameserver.skillengine.model.Effect(bot, target, entry.template(), entry.level());
		double heal = 0;
		for (var template : entry.template().getEffects().getEffects())
			if (template instanceof com.aionemu.gameserver.skillengine.effect.HealInstantEffect instant)
				heal += Math.max(0, instant.calculateSnapshotHealValue(effect, com.aionemu.gameserver.skillengine.model.HealType.HP));
		return PlayerBotTactics.healFit(target.getLifeStats().getMaxHp() - target.getLifeStats().getCurrentHp(), heal,
			entry.template().getDuration(), hp(target) < 30);
	}
	private static double hp(Creature p) { return 100.0 * p.getLifeStats().getCurrentHp() / Math.max(1, p.getLifeStats().getMaxHp()); }
	private static double mp(Creature p) { return 100.0 * p.getLifeStats().getCurrentMp() / Math.max(1, p.getLifeStats().getMaxMp()); }
}
