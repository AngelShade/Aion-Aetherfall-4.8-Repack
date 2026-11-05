package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.SkillKind.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotSkillAdapters.Family.*;

import java.nio.file.Path;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.properties.FirstTargetAttribute;

/** Real learned ranks, native target/cost metadata and adapter decisions; no world or database. */
public final class PlayerBotSkillAdaptersCheck {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 // Before adapters: 110 class/group pairs, 518 rank IDs. ! remains deliberately unsupported.
 private static final String[] SURVEY={
  "GLADIATOR !HOUSEILLUSIONGATE:2,FI_FLYINGSLASH:1,FI_WARFLAG:6,FI_JUMPATTACK:3",
  "TEMPLAR !HOUSEILLUSIONGATE:2,KN_THREATENINGWAVE:2,KN_TURNAGGRESSIVE:1,KN_PURIFYWING:1",
  "ASSASSIN !HOUSEILLUSIONGATE:2,SC_HIDE:2,AS_RETURNATTACK:3,AS_BACKFOCUS:1,!AS_SIXSENSE_L:1,AS_VISIOUSEYE:1,AS_EXPLOSIONPOISON:6,AS_STIGMA_SHADOWWALK:1,AS_SHADOWDODGE:1,AS_ASSAULTSLASH:6,!AS_SIXSENSE_H:1,!AS_DEFENDROAR:2,AS_SHADOWILLUSION:1,AS_DASHATTACK:12,AS_STUNINGINTENTION:1",
  "RANGER !RA_WHITETIGER:8,!RA_KRALLSCOUT:8,!HOUSEILLUSIONGATE:2,RA_SPIKEBITETRAP:12,SC_TRUESHOTMIND:1,!RA_RESTOREWING:1,RA_THROWINGTRAP:16,SC_SPIRITOFGALE:1,!RA_SABAGEROAR:1,RA_SLEEPINGTRAP:6,RA_INSTANTSPRINT:1,RA_SPIKETRAP:22,RA_FASTHIDE:3,RA_SNAKEBITETRAP:16,RA_BLAZINGTRAP:16,RA_POISONTRAP:20,RA_SHOCKWAVETRAP:2,RA_SHOCKTRAP:6,RA_CRISISCOUNTER:4,!RA_MINDSEYE:1,!RA_FAIRYFLARE:2,RA_SANDSTORMTRAP:14,SC_HIDE:1,!AS_SIXSENSE_H:1,!RA_L_SABAGEROAR:1",
  "SORCERER !HOUSEILLUSIONGATE:2,WI_CRYSTALMIRROR:1,WI_FROSTPILLAR:16,WI_HPMPEXCHANGE:1,WI_COUNTERMAGIC:8,WI_DIEMSIONJUMP:1,WI_DIMENSIONDOOR:1,!WI_ILLUSIONGATE:2,WI_DELAYEDEXPLOSION:10,WI_DELAYEDSTRIKE:2,WI_SUMMONTORNADO:12,!WI_MINDSEYE:1,WI_STIGMA_VOLCANICFLAME:4",
  "SPIRIT_MASTER !HOUSEILLUSIONGATE:2,!EL_SOULKING:3,!EL_ESCAPE:1,EL_BIND:1,EL_ENERVATIONCURSE:7,!EL_SYMPATHETICSWITCH:1,!EL_SUMMONAID:1,EL_SLOW:1,EL_SLAVE_STORMSERVENT:12,EL_SLAVE_WINDSERVENT:24,!EL_DECAYINGWING:1",
  "CLERIC PR_HOLYSILIKA:8,PR_HOLYSILIKA_CRYSTAL:8,!HOUSEILLUSIONGATE:2,PR_REVERSECONDITION:1,PR_PROVOKESERVENT:6,PR_HEALINGSERVENT:12,!PR_CIRCLEOFWINGS:5,PR_HOLYSERVENT:22,PR_BLINDINGLIGHT:1,PR_ETERNALSERVENT:12",
  "CHANTER !HOUSEILLUSIONGATE:2,CH_IMBUEEARTH:10,CH_IMBUEPOWER:6,CH_ETERNALSEAL:1,CH_SPACEESCAPE:1,CH_HEALINGTANK:8",
  "RIDER !HOUSEILLUSIONGATE:2,RI_FORWARDDASH:1,RI_INSTANTTHRUST:1,RI_ESCAPEROBOT:2,!RI_REPLENISHBLAST:9,RI_CHAINDASH:1,RI_ACTIVATEGENERATOR:7,RI_ITHEUMREFLECTOR:8",
  "GUNNER !HOUSEILLUSIONGATE:2,!GU_MAGICALSEARCHEYE:1,GU_MAGICALPROMISE:5,GU_FIRECHAINRELOAD:1,GU_FIRECHAINREFRESH:1,GU_SURVIVALINSTINCT:1,GU_LIGHTNINGMOVEMENT:1,GU_BACKSTEPPINGSNIPE:10",
  "BARD !HOUSEILLUSIONGATE:2,BA_SONGOFDELAYDAMAGE:12,BA_SONGOFINJURY:3,BA_SONGOFDIMENSIONDOOR:1,BA_DELAYPARALYZE:1,BA_SONGOFBIND:1"
 };
 private static SkillTemplate skill(int id){return DataManager.SKILL_DATA.getSkillTemplate(id);}
 private static void survey(SkillTreeData tree) {
  int ranks=0,groups=0,admitted=0;
  for(String row:SURVEY) {
   String[] columns=row.split(" ");PlayerClass pc=PlayerClass.valueOf(columns[0]);Set<Integer> learned=new HashSet<>();
   for(Race race:List.of(Race.ELYOS,Race.ASMODIANS))for(int level=1;level<=65;level++) {
    for(var entry:tree.getTemplatesFor(pc,level,race))learned.add(entry.getSkillId());
    for(var entry:tree.getTemplatesFor(pc.getStartingClass(),level,race))learned.add(entry.getSkillId());
   }
   int classAdmitted=0;
   for(String column:columns[1].split(",")) {
    boolean allowed=column.charAt(0)!='!';String[] parts=(allowed?column:column.substring(1)).split(":");
    var templates=learned.stream().map(PlayerBotSkillAdaptersCheck::skill).filter(Objects::nonNull)
     .filter(t->!t.isPassive() && !t.isProvoked() && parts[0].equals(t.getGroup())).toList();
    check(templates.size()==Integer.parseInt(parts[1]),pc+" native rank count "+column+", got "+templates.size());
    for(var template:templates)check((PlayerBotSkills.classify(template)!=UNSUPPORTED)==allowed,
     pc+" "+template.getName()+" "+template.getSkillId()+" "+(allowed?"needs its adapter":"must retain exclusion"));
    ranks+=templates.size();groups++;if(allowed){admitted+=templates.size();classAdmitted+=templates.size();}
   }
   System.out.println(pc+": "+classAdmitted+" formerly rejected native rank IDs admitted");
  }
  check(ranks==518 && groups==110 && admitted==444,"complete pre-adapter survey: "+groups+"/"+ranks+"/"+admitted);
 }
 private static void families() {
  int[] ids={978,657,3980,4152,3797,1308,3036,755,3239,1347,2424,3329,1651,3312,1327,2054};
  PlayerBotSkillAdapters.Family[] families={TRAP,TOTEM,TOTEM,SERVANT,SERVANT,AREA,AREA,MOVEMENT,MOVEMENT,MOVEMENT,MOVEMENT,HIDE,PROC,ONETIME,EXCHANGE,RESET};
  PlayerBotRules.SkillKind[] kinds={DEPLOY,DEPLOY,DEPLOY,DEPLOY,DEPLOY,DEPLOY,DEPLOY,MOBILITY,MOBILITY,MOBILITY,MOBILITY,STEALTH,COMBAT_BUFF,COMBAT_BUFF,COMBAT_BUFF,COMBAT_BUFF};
  for(int i=0;i<ids.length;i++) {
   var t=skill(ids[i]);check(PlayerBotSkillAdapters.family(t)==families[i],"native family "+ids[i]);
   check(PlayerBotSkills.classify(t)==kinds[i],"native adapter action kind "+ids[i]);
  }
  for(int id:new int[]{1421,1506,4285,4297,4631})check(PlayerBotSkills.classify(skill(id))==DAMAGE,"native delayed/attack-trigger payload "+id);
  for(int id:new int[]{3571,3575,3780,4135,1754,4551,4490,1329})check(PlayerBotSkills.classify(skill(id))==CONTROL,"native control payload "+id);
  check(skill(657).getProperties().getFirstTarget()==FirstTargetAttribute.POINT,"banner requires an explicit native point");
  check(skill(1308).getProperties().getFirstTarget()==FirstTargetAttribute.POINT,"Ice Sheet requires an explicit native point");
  check(skill(978).getProperties().getFirstTarget()==FirstTargetAttribute.ME,"self trap keeps native caster-relative placement");
  check(skill(4152).getProperties().getFirstTarget()==FirstTargetAttribute.TARGET,"servant keeps native enemy target");
  check(skill(657).getCooldown()==1836 && skill(657).getCooldownDeltaLv()==-36,"native banner cooldown and rank adjustment retained");
  check(skill(755).getStartconditions()!=null && skill(755).getEndConditions()!=null,"dash keeps weapon and resource admission");
  check(skill(3329).getStartconditions()!=null,"Shadow Walk keeps native combat restriction");
  check(skill(2054).getStartconditions()!=null && skill(2054).getEndConditions()!=null,"Autoload keeps dual-pistol and MP restrictions");
  check(PlayerBotSkills.classify(skill(3544))==UNSUPPORTED,"party Cloaking Word cannot impose stealth and 90% snare on duties");
  check(PlayerBotSkills.questTransformation(skill(872)) && PlayerBotSkills.questTransformation(skill(4610)),"quest Mau retains its existing separate authorization");
 }
 private static void policies() {
  for(var family:List.of(TRAP,TOTEM,SERVANT,AREA,MOVEMENT,HIDE,PROC,ONETIME,EXCHANGE,RESET))
   check(!PlayerBotSkillAdapters.useful(family,false,false,100,100,100,6,false,true),"no idle "+family);
  check(!PlayerBotSkillAdapters.useful(HIDE,true,true,100,100,100,6,false,true),"hide cannot break follow, quests or recruitment duties");
  check(!PlayerBotSkillAdapters.exchange(1000,1000,1000,1000),"full resources never need an exchange");
  check(!PlayerBotSkillAdapters.exchange(10,1000,10,1000),"resource exchange cannot turn two empty pools into recovery");
  check(!PlayerBotSkillAdapters.exchange(300,1000,400,500),"apparently high MP percentage cannot authorize an unsafe absolute HP swap");
  check(PlayerBotSkillAdapters.exchange(600,2000,1500,2000),"native absolute exchange can rescue low HP and retain casting MP");
  check(PlayerBotSkillAdapters.reserve(skill(657),true,false,false,10),"save long cooldown banner on a nearly finished ordinary target");
  check(!PlayerBotSkillAdapters.reserve(skill(657),true,true,false,100),"emergency may spend long cooldown");
  check(!PlayerBotSkillAdapters.reserve(skill(657),true,false,true,100),"durable real fight may spend long cooldown");
 }
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]);
  DataManager.SKILL_DATA=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(root.resolve("data/static_data/skills/skill_templates.xml").toFile());
  DataManager.SKILL_CHARGE_DATA=(SkillChargeData)JAXBContext.newInstance(SkillChargeData.class).createUnmarshaller().unmarshal(root.resolve("data/static_data/skills/skill_charge.xml").toFile());
  var tree=(SkillTreeData)JAXBContext.newInstance(SkillTreeData.class).createUnmarshaller().unmarshal(root.resolve("data/static_data/skill_tree/skill_tree.xml").toFile());
  survey(tree);families();policies();
  System.out.println("OK: "+checks+" native skill adapter survey, targeting, restrictions and combat policy checks");
 }
 private PlayerBotSkillAdaptersCheck(){}
}
