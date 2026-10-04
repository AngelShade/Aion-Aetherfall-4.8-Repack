package com.aionemu.gameserver.services.playerbot;
import java.lang.reflect.*;
import java.io.StringReader;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dataholders.SkillData;
import com.aionemu.gameserver.controllers.NpcController;
import com.aionemu.gameserver.controllers.attack.AggroList;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.HostileUpEffect;
import com.aionemu.gameserver.skillengine.model.Effect;

public final class PlayerBotTankCheck {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static class Bot extends Player {int id;Bot(){super(null,null);}@Override public int getObjectId(){return id;}}
 static class Mob extends Npc {AggroList hate;NpcController controller;Mob(){super(null,null,null);}@Override public NpcController getController(){return controller;}}
 public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(Unsafe)f.get(null);Bot tank=(Bot)u.allocateInstance(Bot.class),dps=(Bot)u.allocateInstance(Bot.class);tank.id=1;dps.id=2;
  Mob mob=(Mob)u.allocateInstance(Mob.class);mob.controller=new NpcController(){@Override public void onAddHate(Creature attacker,boolean first){}};mob.hate=new AggroList(mob){@Override protected boolean isAware(Creature c){return true;}};
  Field aggro=Creature.class.getDeclaredField("aggroList");aggro.setAccessible(true);aggro.set(mob,mob.hate);
  var data=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new StringReader("<skill_data><skill_template skill_id='900200' name='Native positive enmity' activation='ACTIVE' skilltype='PHYSICAL' skillsubtype='DEBUFF'><properties first_target='TARGET' target_relation='ENEMY' target_type='ONLYONE'/><effects><hostileup value='1000' delta='10' hoptype='SKILLLV' hopa='2' hopb='100'/></effects></skill_template><skill_template skill_id='900201' name='Threat reduction' activation='ACTIVE' skilltype='MAGICAL' skillsubtype='DEBUFF'><properties first_target='TARGET' target_relation='ENEMY' target_type='ONLYONE'/><effects><hostileup value='-1000'/></effects></skill_template></skill_data>"));
  var skill=data.getSkillTemplate(900200);check(PlayerBotTank.enmity(skill,3)==1136,"Native taunt and level-scaled effect hate are counted");check(PlayerBotTank.enmity(data.getSkillTemplate(900201),1)==0,"Threat reduction cannot be selected as tank enmity");
  mob.hate.addHate(tank,100);mob.hate.addHate(dps,900);check(PlayerBotTank.priority(1000,true,false,mob.hate.getHate(tank),mob.hate.getHate(dps))==70,"Loose enemy recovery outranks normal offense");
  var effect=new Effect(tank,mob,skill,3);effect.setTauntHate(1300);((HostileUpEffect)skill.getEffects().getEffects().getFirst()).applyEffect(effect);
  check(mob.hate.getHate(tank)==1400 && mob.hate.getHate(dps)==900,"Native HostileUp adds real hate without rewriting another party member's hate");
  check(PlayerBotTank.priority(1000,true,true,1400,900)==0,"Do not waste taunts while holding a safe lead");check(PlayerBotTank.priority(1000,true,true,1000,900)==55,"Reinforce a narrow enmity margin");check(PlayerBotTank.priority(1000,false,false,100,900)>30,"High-enmity attacks lead the opening and recovery rotation");
  check(PlayerBotTank.priority(0,true,false,0,0)==0,"Unsupported or reducing effects are not guessed as taunts");
  System.out.println("OK: "+checks+" native HostileUp effect/hate changes, opening and lost-aggro priorities and threat-reduction protection checks");
 }
}

