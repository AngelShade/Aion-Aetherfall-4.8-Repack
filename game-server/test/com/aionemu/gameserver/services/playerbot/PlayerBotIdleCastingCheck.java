package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.controllers.SummonController;
import com.aionemu.gameserver.controllers.movement.PlayerMoveController;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.calc.*;
import com.aionemu.gameserver.model.stats.container.*;
import com.aionemu.gameserver.services.antihack.AntiHackService;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.world.knownlist.*;

/** Production detector and native spirit-buff planning on isolated objects; no casts/World/DB/IDs. */
public final class PlayerBotIdleCastingCheck {
 static Unsafe unsafe;static int checks;static SkillData data;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class HumanMovementProbe extends RuntimeException {}
 static final class Actor extends PlayerBotClericCheck.Actor {
  Pet pet;boolean headless,probe;KnownList known;
  @Override public Summon getSummon(){return pet;}
  @Override public boolean isPlayerBot(){return headless;}
  @Override public KnownList getKnownList(){return known;}
  @Override public int getObjectId(){return 1;}
  @Override public int getWorldId(){return 210010000;}
  @Override public int getInstanceId(){return 1;}
  @Override public PlayerMoveController getMoveController(){if(probe)throw new HumanMovementProbe();return super.getMoveController();}
 }
 static final class Pet extends Summon {
  Actor master;int npc,speed;boolean casting;KnownList known;
  Pet(){super(0,null,null,null,null,0);}
  @Override public Player getMaster(){return master;}
  @Override public int getNpcId(){return npc;}
  @Override public boolean isDead(){return false;}
  @Override public boolean isCasting(){return casting;}
  @Override public boolean isBeingReleased(){return false;}
  @Override public boolean isEnemy(Creature other){return false;}
  @Override public boolean isInsidePvPZone(){return false;}
  @Override public KnownList getKnownList(){return known;}
  @Override public int getObjectId(){return 2;}
  @Override public int getWorldId(){return 210010000;}
  @Override public int getInstanceId(){return 1;}
  @Override public float getX(){return 0;}@Override public float getY(){return 0;}@Override public float getZ(){return 0;}
  @Override public com.aionemu.gameserver.model.templates.npc.NpcTemplate getObjectTemplate(){return new com.aionemu.gameserver.model.templates.npc.NpcTemplate();}
  @Override public SummonGameStats getGameStats(){return new SummonGameStats(this){
   @Override public Stat2 getAttackSpeed(){return new AdditionStat(StatEnum.ATTACK_SPEED,speed,null);}
  };}
 }
 static Actor actor()throws Exception {
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.pc=PlayerClass.SPIRIT_MASTER;a.headless=true;a.spawned=true;
  a.life=(PlayerBotSorcererCheck.Life)unsafe.allocateInstance(PlayerBotSorcererCheck.Life.class);a.life.hp=a.life.mp=1000;
  a.stats=(PlayerBotClericCheck.Stats)unsafe.allocateInstance(PlayerBotClericCheck.Stats.class);
  a.effects=new PlayerBotSorcererCheck.Effects(a);a.known=new KnownList(a);return a;
 }
 static PlayerBotSkills.Entry entry(int id){var s=data.getSkillTemplate(id);return new PlayerBotSkills.Entry(s,1,PlayerBotSkills.classify(s),25);}
 public static void main(String[] args)throws Exception {
  var uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=(Unsafe)uf.get(null);
  Path root=Path.of(args[0]);var context=JAXBContext.newInstance(SkillData.class,PetSkillData.class);
  data=(SkillData)context.createUnmarshaller().unmarshal(root.resolve("data/static_data/skills/skill_templates.xml").toFile());
  DataManager.SKILL_DATA=data;DataManager.MATERIAL_DATA=new MaterialData();
  DataManager.PET_SKILL_DATA=(PetSkillData)context.createUnmarshaller().unmarshal(root.resolve("data/static_data/pet_skills/pet_skills.xml").toFile());
  var bot=actor();bot.probe=true;
  Method suspicious=Skill.class.getDeclaredMethod("isSuspiciousClientHitTime",int.class,int.class,int.class,Player.class);suspicious.setAccessible(true);
  var skill=new Skill(data.getSkillTemplate(3779),bot,1,bot,null);
  for(var pc:PlayerClass.values()) {
   bot.pc=pc;bot.headless=true;
   check(AntiHackService.canMove(bot,1,2,3,(byte)0),"Headless "+pc+" never enters client movement auditing");
   check(!(boolean)suspicious.invoke(skill,0,2000,1,bot),"Headless "+pc+" has no forged client hit time");
   bot.headless=false;
   try{AntiHackService.canMove(bot,1,2,3,(byte)0);throw new AssertionError("Human bypassed movement checker");}
   catch(HumanMovementProbe expected){check(true,"Human "+pc+" still enters core checker without a connection-based exemption");}
   check(!(boolean)suspicious.invoke(skill,2000,2000,1,bot),"Unchanged legitimate human hit-time tolerance: "+pc);
   check((boolean)suspicious.invoke(skill,100,2000,1,bot),"Human "+pc+" suspicious accelerated hit time remains detected");
  }
  bot.pc=PlayerClass.SPIRIT_MASTER;bot.headless=true;bot.probe=false;
  var preparation=entry(3779);
  check(PlayerBotSpiritmaster.summonPreparation(preparation.template()),"Native Summoning Alacrity has summon-only cast-speed modifiers");
  check(PlayerBotSpiritmaster.idleUseful(bot,preparation,bot,false),"Missing spirit retains native summon preparation");
  var pet=(Pet)unsafe.allocateInstance(Pet.class);pet.master=bot;pet.speed=2000;bot.pet=pet;
  pet.known=new KnownList(pet){@Override public java.util.stream.Stream<KnownObject> stream(){return java.util.stream.Stream.of(new KnownObject(bot));}};
  check(!PlayerBotSpiritmaster.idleUseful(bot,preparation,bot,false),"Existing spirit stops purposeless idle summon-boost recasting");
  check(PlayerBotSpiritmaster.idleUseful(bot,preparation,bot,true),"Existing combat preparation policy retained");
  bot.pc=PlayerClass.SORCERER;check(PlayerBotSpiritmaster.idleUseful(bot,preparation,bot,false),"Unrelated class idle behavior retained");bot.pc=PlayerClass.SPIRIT_MASTER;
  for(int npc:new int[]{833256,833258,833260}) {
   pet.npc=npc;var order=entry(3531);var actual=PlayerBotSkills.actualTemplate(bot,order);
   check(PlayerBotSkills.classify(actual)==PlayerBotRules.SkillKind.BUFF,"Native wall order maps to actual protective aura: "+npc);
   check(PlayerBotSpiritmaster.idleUseful(bot,order,pet,false),"Missing PC-area aura remains useful: "+npc);
   check(PlayerBotSpiritmaster.idleUseful(bot,order,pet,true),"Missing PC-area aura retains useful combat support: "+npc);
   bot.effects.observed.add(new Effect(bot,bot,actual,actual.getLvl()));
   check(!PlayerBotSpiritmaster.idleUseful(bot,order,pet,false),"Existing aura on native PC beneficiary prevents idle recasting: "+npc);
   check(!PlayerBotSpiritmaster.idleUseful(bot,order,pet,true),"Existing PC aura also prevents combat buff recasting: "+npc);
   bot.effects.observed.clear();pet.casting=true;
   check(!PlayerBotSpiritmaster.idleUseful(bot,order,pet,false),"In-flight spirit cast cannot receive another idle order: "+npc);
   check(!PlayerBotSpiritmaster.idleUseful(bot,order,pet,true),"In-flight spirit cast cannot receive another combat order: "+npc);pet.casting=false;
  }
  var controller=new SummonController();controller.setOwner(pet);
  var last=SummonController.class.getDeclaredField("lastAttackMillis");last.setAccessible(true);last.setLong(controller,10000);
  check(controller.getNextAttackMillis()==12000,"Native attack deadline survives a short spirit spell");
  pet.speed=2400;check(controller.getNextAttackMillis()==12400,"Native deadline follows current slowed attack speed");
  pet.speed=1200;check(controller.getNextAttackMillis()==11200,"Native deadline follows current haste without a fixed delay");
  System.out.println("OK: "+checks+" native core bot/human detection and Spiritmaster idle spell/PC-aura checks; no native casts/World/DB/IDs");
 }
}
