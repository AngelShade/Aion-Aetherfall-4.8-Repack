package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Path;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.SkillKind;

/** Native recorded Devotion and final Session usefulness; no native cast, World, IDs or SQL. */
public final class PlayerBotNonCombatBuffCheck {
 static int checks;
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 public static void main(String[] args)throws Exception {
  var old=DataManager.SKILL_DATA;var material=DataManager.MATERIAL_DATA;
  var data=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(Path.of(args[0],"data/static_data/skills/skill_templates.xml").toFile());
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(Unsafe)f.get(null);
  DataManager.SKILL_DATA=data;DataManager.MATERIAL_DATA=new MaterialData();PlayerBotSupportCooperationCheck.unsafe=u;PlayerBotSupportCooperationCheck.data=data;
  try {
   var bot=PlayerBotSupportCooperationCheck.actor(1999999291);bot.pc=PlayerClass.ASSASSIN;
   var session=PlayerBotSupportCooperationCheck.session(bot,bot);var entry=PlayerBotSupportCooperationCheck.entry(3235);
   check(entry.kind()==SkillKind.BUFF,"Observed native Devotion is classified as a buff");
   check(entry.template().getEffects().getEffects().getFirst().getDuration2()==5000 && entry.template().getCooldown()==300,"Native attack boost lasts5s with30s cooldown; no invented timings");
   var ctor=Class.forName(PlayerBotSession.class.getName()+"$CastAction").getDeclaredConstructor(PlayerBotSession.class,PlayerBotSkills.Entry.class,Creature.class,List.class);ctor.setAccessible(true);
   var action=(PlayerBotEngine.Action)ctor.newInstance(session,entry,bot,List.of());
   check(!action.isUseful(),"Native Devotion cannot stop idle travel without a living combat enemy; installed055 reproduces unwanted admission");
   var recipient=PlayerBotSession.class.getDeclaredMethod("recipient",PlayerBotSkills.Entry.class,List.class,Npc.class,boolean.class);recipient.setAccessible(true);
   check(recipient.invoke(session,entry,List.of(bot),null,false)==null,"Actual noncombat selection skips the same native attack burst before admission");
   var enemy=(PlayerBotQuestObjectivesCheck.Contact)u.allocateInstance(PlayerBotQuestObjectivesCheck.Contact.class);enemy.spawned=true;enemy.world=bot.world;enemy.instance=bot.instance;
   bot.hostile=true;var oldKnown=bot.seen;bot.seen=new com.aionemu.gameserver.world.knownlist.KnownList(bot){@Override public boolean sees(VisibleObject o){return o==enemy;}};
   var fighting=(PlayerBotEngine.Action)ctor.newInstance(session,entry,bot,List.of(enemy));
   check(fighting.isUseful(),"Fresh actual combat enemy retains the native boost at final CastAction admission");
   enemy.spawned=false;check(!fighting.isUseful(),"A stale combat list cannot spend the burst after its enemy despawns");bot.hostile=false;bot.seen=oldKnown;
   check(PlayerBotCombatBuffs.useful(PlayerClass.ASSASSIN,PlayerBotRules.Role.MELEE,entry.template(),100,100),"Native Devotion remains a useful actual-combat boost");
   check(PlayerBotCombatBuffs.nonCombatUseful(data.getSkillTemplate(3877)),"Observed native Penance keeps real periodic mana recovery and HP cost");
   check(PlayerBotCombatBuffs.nonCombatUseful(data.getSkillTemplate(3779)),"Spirit summon preparation remains available out of combat");
   check(bot.life.hp==1000 && bot.life.mp==1000 && bot.effects.observed.isEmpty(),"Usefulness probes do not pay resources or apply/remove effects");
   for(var skill:data.getSkillTemplates())if(PlayerBotChanter.mantra(skill))check(PlayerBotCombatBuffs.nonCombatUseful(skill),"Native toggle mantra retains idle support preparation");
  }finally{DataManager.SKILL_DATA=old;DataManager.MATERIAL_DATA=material;}
  System.out.println("OK: "+checks+" native idle burst rejection, combat retention, recovery and mantra custody checks");
 }
 private PlayerBotNonCombatBuffCheck(){}
}
