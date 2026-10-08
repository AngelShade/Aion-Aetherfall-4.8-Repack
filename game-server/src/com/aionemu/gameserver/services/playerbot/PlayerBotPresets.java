package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.alibaba.fastjson2.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dao.PlayerDAO;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.TeamType;
import com.aionemu.gameserver.services.AccountService;
import com.aionemu.gameserver.services.player.PlayerLeaveWorldService;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import com.aionemu.gameserver.world.World;

/** Account-owned bookmarks and mixed companion parties; character progress stays
 * in native persistence, never copied into a preset or converted into a new bot.
 */
public final class PlayerBotPresets {
 private static final Store STORE=new Store(Path.of("config/playerbots/saved-parties"));
 static final int MAX_PRESETS=20;
 public record Member(int id,String name,boolean temporary,Role role,Order order) {
  public Member { if(id<=0 || name==null || name.isBlank() || role==null || order==null)throw new IllegalArgumentException("Invalid saved companion."); }
 }
 public record Preset(String id,String name,List<Member> members) {
  public Preset {
   if(id==null || !id.matches("[a-f0-9]{32}") || name==null || name.isBlank() || name.length()>40 || name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Use a party name of 1–40 characters.");
   members=List.copyOf(members);if(members.isEmpty() || members.size()>5 || members.stream().map(Member::id).distinct().count()!=members.size())throw new IllegalArgumentException("Save between one and five different companions.");
  }
 }
 record Document(int account,Set<Integer> saved,List<Preset> presets) {
  Document { saved=Set.copyOf(saved);presets=List.copyOf(presets);if(account<=0 || saved.size()>100 || saved.stream().anyMatch(i->i<=0) || presets.size()>MAX_PRESETS || presets.stream().map(Preset::id).distinct().count()!=presets.size())throw new IllegalArgumentException("Invalid saved-party data."); }
 }
 static final class Store {
  private final Path directory;
  Store(Path directory){this.directory=directory.toAbsolutePath().normalize();}
  private Path path(int account){if(account<=0)throw new IllegalArgumentException("Invalid account.");return directory.resolve("account-"+account+".json");}
  synchronized Document load(int account)throws IOException {
   return PlayerBotRepository.load(account);
  }
  synchronized void write(Document document)throws IOException {
   PlayerBotRepository.save(document);
  }
  synchronized void saveBot(int account,int id)throws IOException {var doc=load(account);var saved=new HashSet<>(doc.saved());saved.add(id);write(new Document(account,saved,doc.presets()));}
  synchronized Preset saveParty(int account,String name,List<Member> members)throws IOException {
   var doc=load(account);var current=doc.presets().stream().filter(p->p.name().equalsIgnoreCase(name.strip())).findFirst().orElse(null);
   var preset=new Preset(current==null ? UUID.randomUUID().toString().replace("-","") : current.id(),name.strip(),members);
   if(current==null && doc.presets().size()>=MAX_PRESETS)throw new IllegalArgumentException("You can save up to 20 party presets.");
   var presets=new ArrayList<>(doc.presets());presets.removeIf(p->p.id().equals(preset.id()));presets.add(preset);var saved=new HashSet<>(doc.saved());for(var m:members)if(m.temporary())saved.add(m.id());write(new Document(account,saved,presets));return preset;
  }
  synchronized void remove(int account,String id)throws IOException {var doc=load(account);var presets=new ArrayList<>(doc.presets());if(!presets.removeIf(p->p.id().equals(id)))throw new IllegalArgumentException("That party preset no longer exists.");write(new Document(account,doc.saved(),presets));}
 }
 public static Map<String,Object> snapshot(Player owner) {
  try{var doc=STORE.load(owner.getAccount().getId());return Map.of("savedBots",doc.saved().stream().sorted().toList(),"presets",doc.presets(),"presetLimit",MAX_PRESETS);}
  catch(IOException e){throw new IllegalArgumentException(e.getMessage(),e);}
 }
 private static void checkpoint(PlayerBotSession session)throws Exception {
  if(session.closing())throw new IllegalArgumentException(session.bot().getName()+" is waiting for a save retry.");
  // Read the persisted home instead of saving a temporary/private-instance position.
  var data=AccountService.loadPlayerAccountData(session.bot().getObjectId());if(data==null)throw new IllegalStateException("The saved companion character is missing.");
  var home=data.getPlayerCommonData();PlayerBotPersistence.save(session.bot(),new PlayerBotPersistence.Home(home.getMapId(),home.getX(),home.getY(),home.getZ(),home.getHeading(),home.getWorldOwnerId()));
 }
 public static String saveBot(Player owner,String name) {
  synchronized(PlayerBotService.getInstance()) {
   var entry=PlayerBotRoster.list(owner.getAccount().getId()).stream().filter(e->e.ready() && name.equalsIgnoreCase(e.name())).findFirst().orElseThrow(()->new IllegalArgumentException("Choose one of your Temporary Bots to save."));
   try {
    var active=PlayerBotService.getInstance().companions(owner).stream().filter(s->s.bot().getObjectId()==entry.id()).findFirst().orElse(null);
    if(active!=null)synchronized(active){checkpoint(active);}
    STORE.saveBot(owner.getAccount().getId(),entry.id());return "Saved Temporary Bot "+entry.name()+". Recruit it again from Saved bots; its equipment, skills and quests are retained.";
   }catch(Exception e){throw failure("Could not save this Temporary Bot",e);}
  }
 }
 public static String saveParty(Player owner,String name) {
  synchronized(PlayerBotService.getInstance()) {
   var sessions=PlayerBotService.getInstance().companions(owner);if(sessions.isEmpty())throw new IllegalArgumentException("Recruit a party before saving a preset.");
   List<Member> members=new ArrayList<>();
   try {
    for(var session:sessions)synchronized(session){if(session.generated())checkpoint(session);var state=session.snapshot();members.add(new Member(session.bot().getObjectId(),session.bot().getName(),session.generated(),session.combatRole(),Order.valueOf(state.get("order").toString())));}
    var preset=STORE.saveParty(owner.getAccount().getId(),name,members);return "Saved party preset "+preset.name()+" ("+members.size()+" companions).";
   }catch(Exception e){throw failure("Could not save this party preset",e);}
  }
 }
 private static IllegalArgumentException failure(String context,Exception e){return new IllegalArgumentException(context+": "+e.getMessage(),e);}
 public static String remove(Player owner,String id){if(id.startsWith("bot:"))return PlayerBotRosterRemoval.remove(owner,Integer.parseInt(id.substring(4)),STORE);try{STORE.remove(owner.getAccount().getId(),id);return "Party preset removed. Your characters and saved bots were kept.";}catch(IOException e){throw failure("Could not remove the preset",e);}}
 static void checkRoom(int active,int missing,int partySize,int limit,int global,int globalLimit){if(active+missing>limit || partySize+missing>6 || global+missing>globalLimit)throw new IllegalArgumentException("This preset needs more free companion/party slots. Dismiss companions outside the preset first; human party members are kept.");}
 public static String activate(Player owner,String id) {
  var service=PlayerBotService.getInstance();synchronized(service) {
   List<PlayerBotSession> created=new ArrayList<>();
   try {
    var preset=STORE.load(owner.getAccount().getId()).presets().stream().filter(p->p.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("That party preset no longer exists."));
    if(!PlayerBotConfig.ENABLED || !owner.isOnline() || !owner.isSpawned())throw new IllegalArgumentException("Wait until your character is in the world.");
    if(owner.getController().isInCombat())throw new IllegalArgumentException("You cannot recruit a party preset while in combat.");
    var group=owner.getPlayerGroup();if(owner.isInAlliance() || group!=null && (!group.isLeader(owner) || group.getTeamType()!=TeamType.GROUP))throw new IllegalArgumentException("Lead a normal party to summon this preset.");
    Map<Integer,PlayerBotSession> active=new HashMap<>();for(var s:service.companions(owner))active.put(s.bot().getObjectId(),s);
    Map<Integer,String> names=new LinkedHashMap<>();var roster=PlayerBotRoster.list(owner.getAccount().getId());
    for(var m:preset.members()) {
     if(m.id()==owner.getObjectId())throw new IllegalArgumentException(m.name()+" is your current character. Use another character to summon this preset.");
     boolean temporary=roster.stream().anyMatch(e->e.id()==m.id() && e.ready());
     if(temporary!=m.temporary() || PlayerDAO.getAccountId(m.id())!=owner.getAccount().getId())throw new IllegalArgumentException(m.name()+" no longer belongs to this saved party/account.");
     var current=active.get(m.id());if(current!=null){if(current.closing() || current.bot().getController().isInCombat())throw new IllegalArgumentException(m.name()+" is busy or saving.");names.put(m.id(),current.bot().getName());continue;}
     if(World.getInstance().isInWorld(m.id()) || PlayerBotLease.isReserved(m.id()) || PlayerLeaveWorldService.isLeavingWorld(m.id()))throw new IllegalArgumentException(m.name()+" is online or saving. No companions were recruited.");
     var data=AccountService.loadPlayerAccountData(m.id());if(data==null)throw new IllegalArgumentException(m.name()+" no longer exists.");var common=data.getPlayerCommonData();
     boolean banned=data.getCharBanInfo()!=null && data.getCharBanInfo().getEnd()>=System.currentTimeMillis()/1000;
     if(!PlayerBotRules.canRecruit(true,common.getRace()==owner.getRace(),common.isOnline(),banned,data.getDeletionDate()!=null,false,temporary ? 0 : common.getLevel()-owner.getLevel(),PlayerBotConfig.LEVEL_DIFFERENCE))throw new IllegalArgumentException(common.getName()+" is not eligible for recruitment. No companions were recruited.");
     names.put(m.id(),common.getName());
    }
    int missing=(int)preset.members().stream().filter(m->!active.containsKey(m.id())).count();
    checkRoom(active.size(),missing,group==null ? 1 : group.getMembers().size(),Math.min(5,PlayerBotConfig.MAX_PER_OWNER),World.getInstance().getAllPlayers().stream().filter(Player::isPlayerBot).toList().size(),PlayerBotConfig.MAX_ACTIVE);
    for(var m:preset.members())if(!active.containsKey(m.id())){service.recruit(owner,names.get(m.id()));var session=service.find(owner,names.get(m.id()));created.add(session);active.put(m.id(),session);}
    for(var m:preset.members()){var s=active.get(m.id());if(s.combatRole()!=m.role())s.setRole(m.role());s.order(m.order());if(!s.bot().isDead())PlayerBotTravel.summon(s);}
    return "Party preset "+preset.name()+" summoned. "+preset.members().size()+" companions are online; owned-alt builds were kept.";
   }catch(Exception e){for(var s:created)try{service.dismiss(owner,s.bot().getName());}catch(Exception rollback){e.addSuppressed(rollback);}throw failure("Could not summon this party preset",e);}
  }
 }
 private PlayerBotPresets(){}
}
