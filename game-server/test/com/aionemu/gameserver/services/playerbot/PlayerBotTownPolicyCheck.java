package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Path;
import java.io.StringReader;
import java.util.List;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.SkillData;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.MaterialData;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/** Real captured native spell metadata and purpose gates, without World/IDs/casts/SQL. */
public final class PlayerBotTownPolicyCheck {
 private static int checks;
 private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
 private static SkillTemplate synthetic(String payload,int cooldown)throws Exception {
  return ((SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new StringReader(
   "<skill_data><skill_template skill_id=\"991987\" activation=\"ACTIVE\" cooldown=\""+cooldown+"\" skilltype=\"MAGICAL\">"
   +"<properties first_target=\"ME\" target_relation=\"FRIEND\" target_type=\"ONLYONE\"/><effects>"+payload
   +"</effects></skill_template></skill_data>"))).getSkillTemplate(991987);
 }
 private static void sessionChecks(SkillData data)throws Exception {
  var previous=DataManager.SKILL_DATA;var material=DataManager.MATERIAL_DATA;
  var helperData=PlayerBotSupportCooperationCheck.data;
  var unsafeType=Class.forName("sun.misc.Unsafe");var field=unsafeType.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=field.get(null);
  var helperUnsafe=PlayerBotSupportCooperationCheck.class.getDeclaredField("unsafe");var oldUnsafe=helperUnsafe.get(null);helperUnsafe.set(null,unsafe);
  DataManager.SKILL_DATA=data;DataManager.MATERIAL_DATA=new MaterialData();PlayerBotSupportCooperationCheck.data=data;
  try {
   var bot=PlayerBotSupportCooperationCheck.actor(1999999391);var session=PlayerBotSupportCooperationCheck.session(bot,bot);
   var recipient=PlayerBotSession.class.getDeclaredMethod("recipient",PlayerBotSkills.Entry.class,List.class,Npc.class,boolean.class);recipient.setAccessible(true);
   var constructor=Class.forName(PlayerBotSession.class.getName()+"$CastAction").getDeclaredConstructor(PlayerBotSession.class,PlayerBotSkills.Entry.class,Creature.class,List.class);constructor.setAccessible(true);
   var enemy=(PlayerBotQuestObjectivesCheck.Contact)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,PlayerBotQuestObjectivesCheck.Contact.class);
   enemy.spawned=true;enemy.world=bot.world;enemy.instance=bot.instance;
   var known=bot.seen;bot.seen=new com.aionemu.gameserver.world.knownlist.KnownList(bot){@Override public boolean sees(VisibleObject other){return other==enemy;}};
   for(int id:new int[]{1755,1439,3468,3048,3007}) {
    var entry=PlayerBotSupportCooperationCheck.entry(id);bot.hostile=false;
    check(recipient.invoke(session,entry,List.of(bot),null,false)==null,"Actual Session rejects the captured idle preparation before expensive admission: "+id);
    var idle=(PlayerBotEngine.Action)constructor.newInstance(session,entry,bot,List.of());
    check(!idle.isUseful(),"Actual final CastAction rejects idle preparation: "+id);
    bot.hostile=true;enemy.spawned=true;
    var combat=(PlayerBotEngine.Action)constructor.newInstance(session,entry,bot,List.of(enemy));
    check(combat.isUseful(),"Actual final CastAction retains preparation with a fresh living native enemy: "+id);
    enemy.spawned=false;check(!combat.isUseful(),"Final admission rejects a stale despawned threat: "+id);
   }
   bot.hostile=false;bot.seen=known;
   check(bot.life.hp==1000 && bot.life.mp==1000 && bot.effects.observed.isEmpty(),"Purpose/Session probes neither spend resources nor create auras");
  }finally{DataManager.SKILL_DATA=previous;DataManager.MATERIAL_DATA=material;PlayerBotSupportCooperationCheck.data=helperData;helperUnsafe.set(null,oldUnsafe);}
 }
 public static void main(String[] args)throws Exception {
  var data=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(Path.of(args[0],"data/static_data/skills/skill_templates.xml").toFile());
  // This first direct predicate also reproduces installed057's missing defensive gate.
  check(!PlayerBotCombatBuffs.nonCombatUseful(data.getSkillTemplate(1755)),"Captured native Word of Protection defensive15s burst is not idle maintenance");
  for(int id:new int[]{1755,1439,3468,3048,620,3890,3235}) {
   var skill=data.getSkillTemplate(id);check(skill!=null,"Captured native spell exists");
   check(!PlayerBotTownPolicy.buffUseful(skill,false,false),"Native combat-only preparation is rejected while idle: "+id);
   check(PlayerBotTownPolicy.buffUseful(skill,true,false),"Actual combat retains the same native spell: "+id);
   check(PlayerBotTownPolicy.buffUseful(skill,false,true),"Required native quest spell bypasses only the purpose gate: "+id);
  }
  var counter=data.getSkillTemplate(3048);check(counter.getEffects().getEffects().getFirst().getDuration2()==10000 && counter.getCooldown()==50,"Native counterattack buff lasts10s but cooldown5s; duration<cooldown cannot identify every burst");
  for(int id:new int[]{2997,3001,3007,3009}) {
   var skill=data.getSkillTemplate(id);check(skill.isStance() && skill.isToggle(),"Captured native stance metadata");
   check(!PlayerBotTownPolicy.buffUseful(skill,false,false),"Idle cannot rebuild a cancel-on-cast combat stance: "+id);
   check(PlayerBotTownPolicy.buffUseful(skill,true,false),"Combat stance remains useful in combat");
  }
  for(int id:new int[]{809,796,3124,4538,2737,3877,4373,3779,3888,1663}) {
   var skill=data.getSkillTemplate(id);check(skill!=null,"Native maintenance spell exists");
   check(PlayerBotTownPolicy.buffUseful(skill,false,false),"Lasting, movement, mana, summon and casting/recovery preparations remain: "+id);
  }
  for(var skill:data.getSkillTemplates())if(PlayerBotChanter.mantra(skill))check(PlayerBotTownPolicy.buffUseful(skill,false,false),"All native mantra maintenance remains");
  check(!PlayerBotTownPolicy.buffUseful(null,true,true),"Missing template is never useful");
  String attack="<statup e=\"1\" duration2=\"15000\"><change stat=\"PHYSICAL_ATTACK\" value=\"20\"/></statup>";
  check(!PlayerBotCombatBuffs.nonCombatUseful(synthetic(attack,50)),"Short native attack payload remains combat-only when cooldown is shorter");
  check(PlayerBotCombatBuffs.nonCombatUseful(synthetic(attack.replace("15000","60000"),50)),"Explicit Aion sub-minute boundary retains lasting60s maintenance");
  check(PlayerBotCombatBuffs.nonCombatUseful(synthetic(attack,0)),"No invented cooldown for native zero-cooldown preparation");
  check(PlayerBotCombatBuffs.nonCombatUseful(synthetic(attack+"<mpheal e=\"2\" duration2=\"15000\" value=\"10\"/>",50)),"Mixed real resource benefit is preserved");
  check(PlayerBotCombatBuffs.nonCombatUseful(synthetic(attack+"<statup e=\"2\" duration2=\"15000\"><change stat=\"SPEED\" value=\"10\"/></statup>",50)),"Mixed native movement benefit is preserved");
  check(PlayerBotCombatBuffs.nonCombatUseful(synthetic("<boostskillcost e=\"1\" duration2=\"15000\" value=\"-30\"/>",50)),"Cost-only preparation is not assumed to be offense");
  check(PlayerBotCombatBuffs.nonCombatUseful(synthetic(attack+"<hide e=\"2\" duration2=\"15000\"/>",50)),"Unclassified additional purpose is preserved conservatively");
  check(PlayerBotCombatBuffs.nonCombatUseful(synthetic(attack.replace("</statup>","<subeffect skill_id=\"3877\"/></statup>"),300)),"An unclassified native subeffect cannot be mistaken for a pure combat preparation");
  check(!PlayerBotTownPolicy.serviceUseful(true,true,false,false),"Vereamir's preacceptance profession preparation cannot replace his committed active quest hunt");
  check(PlayerBotTownPolicy.serviceUseful(true,true,true,false),"Active quest-owned design/material/training custody remains eligible");
  check(PlayerBotTownPolicy.serviceUseful(true,true,false,true),"Necessary native full-bag care can interrupt travel");
  check(PlayerBotTownPolicy.serviceUseful(true,false,false,false),"An idle autonomous actor can prepare a valid new work order");
  check(PlayerBotTownPolicy.serviceUseful(false,true,false,false),"Companion service control retains existing owner-scoped admission");
  sessionChecks(data);
  System.out.println("OK: "+checks+" native town preparation, combat/quest retention, recovery and service-purpose checks");
 }
 private PlayerBotTownPolicyCheck() {}
}
