package com.aionemu.gameserver.services.playerbot;
import java.nio.file.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.SkillData;
import com.aionemu.gameserver.skillengine.properties.*;

public final class PlayerBotHealingCheck {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  check(PlayerBotHealing.probe(25,5,20,false)==25.5,"Nearby player uses health plus distance penalty");
  check(PlayerBotHealing.probe(10,30,20,false)==40,"Out of spell range receives upstream thirty-point penalty");
  check(PlayerBotHealing.probe(10,5,20,true)==40,"Pet receives lower-priority health probe");
  check(PlayerBotHealing.probe(10,30,20,true)==40,"Pet penalty is not doubled for distance");
  check(PlayerBotHealing.probe(25,5,20,false)<PlayerBotHealing.probe(10,30,20,false),"Nearby emergency precedes an unnecessarily distant heal");
  check(PlayerBotHealing.probe(25,5,20,false)<PlayerBotHealing.probe(10,5,20,true),"An injured human precedes a pet");
  check(PlayerBotHealing.groupBonus(0,0)==0,"Empty or healthy groups do not trigger healing");
  check(PlayerBotHealing.groupBonus(1,0)==0,"A single injury does not receive an AoE bonus");
  check(PlayerBotHealing.groupBonus(3,0)>PlayerBotHealing.groupBonus(2,0),"Multiple actual injured players favor a group heal");
  check(PlayerBotHealing.groupBonus(0,3)<PlayerBotHealing.groupBonus(3,0),"Pets do not outweigh the same number of players");
  check(PlayerBotHealing.groupBonus(6,6)==8,"Group urgency bonus is bounded");
  var path=Path.of("game-server/data/static_data/skills/skill_templates.xml");
  var data=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(path.toFile());
  for(int id:new int[]{3978,3979,4176,4177,4178,4179,4180,4181,4195,4196,4197,4198,4199,4200,4201,4202}){
   var skill=data.getSkillTemplate(id);check(skill!=null && PlayerBotSkills.classify(skill)==PlayerBotRules.SkillKind.HEAL && skill.getProperties().getFirstTarget()==FirstTargetAttribute.ME && skill.getProperties().getTargetType()==TargetRangeAttribute.PARTY,"Production self-centred group heal participates in new aggregate planning: "+id);
  }
  for(int id:new int[]{4484,4485,4486}) {
   var skill=data.getSkillTemplate(id);var entry=new PlayerBotSkills.Entry(skill,skill.getLvl(),PlayerBotSkills.classify(skill),1);
   check(entry.kind()==PlayerBotRules.SkillKind.CLEANSE && PlayerBotHealing.managed(entry) && PlayerBotHealing.heals(entry),"Hybrid Bard cleansing/healing retains its classification and evaluates both native effects: "+id);
  }
  System.out.println("OK: "+checks+" upstream health/distance/pet/group comparisons and production self-centred heal templates; actual casts are native runtime acceptance");
 }
}
