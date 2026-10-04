package com.aionemu.gameserver.services.playerbot;
import java.io.StringReader;
import java.nio.file.Path;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.SkillData;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

public final class PlayerBotDefenseCheck {
 static int checks;static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  var context=JAXBContext.newInstance(SkillData.class);var data=(SkillData)context.createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/skills/skill_templates.xml").toFile());
  var skin=data.getSkillTemplate(1155);var root=data.getSkillTemplate(1328);var shot=data.getSkillTemplate(950);var arrow=data.getSkillTemplate(1022);var resist=data.getSkillTemplate(3272);
  check(PlayerBotSkills.classify(resist)==SkillKind.DEFENSE,"Native Spelldodging no longer discarded");
  check(PlayerBotSkills.classify(data.getSkillTemplate(3328))==SkillKind.DEFENSE,"Native Aethertwisting no longer discarded");
  check(PlayerBotSkills.classify(data.getSkillTemplate(1009))==SkillKind.CLEANSE,"Hybrid Nature's Resolve keeps native cleanse behavior");
  check(PlayerBotSkills.classify(root)==SkillKind.CONTROL && !PlayerBotSkills.canInterrupt(root),"Native Root is movement control, not a spell interrupt");
  check(PlayerBotSkills.classify(shot)==SkillKind.DAMAGE,"Native snaring attack keeps its damage category");
  check(PlayerBotDefense.policy(PlayerClass.SORCERER,skin,1,100,100,true,false,false)==0,"Healthy unpressured Mage does not waste a barrier");
  check(PlayerBotDefense.policy(PlayerClass.SORCERER,skin,1,100,100,true,true,false)==29,"Being attacked triggers the original Frost Mage barrier band");
  check(PlayerBotDefense.policy(PlayerClass.SORCERER,skin,1,64,100,true,false,false)==29,"Medium health triggers the original barrier before panic");
  check(PlayerBotDefense.policy(PlayerClass.SORCERER,skin,1,65,100,true,false,false)==0,"Medium-health threshold is bounded");
  check(PlayerBotDefense.policy(PlayerClass.SORCERER,skin,1,64,100,false,false,false)==0,"No new noncombat defensive spam");
  check(PlayerBotDefense.policy(PlayerClass.SORCERER,skin,1,34,100,false,false,false)==91,"Previously installed panic policy retained");
  check(PlayerBotDefense.policy(PlayerClass.SORCERER,skin,1,0,100,true,true,true)==0,"Dead target has no defensive priority");
  check(PlayerBotDefense.policy(PlayerClass.RANGER,data.getSkillTemplate(1923),1,44,100,true,true,false)==35,"Hunter low-health defensive band retained in Ranger adaptation");
  check(PlayerBotDefense.policy(PlayerClass.RANGER,data.getSkillTemplate(1923),1,45,100,true,true,false)==0,"Hunter low-health threshold is bounded");
  check(PlayerBotDefense.policy(PlayerClass.ASSASSIN,resist,1,24,100,true,true,true)==27,"Cloak purpose retains original critical-health priority");
  check(PlayerBotDefense.policy(PlayerClass.ASSASSIN,resist,1,25,100,true,true,true)==0,"Resistance charges preserve the critical-health threshold");
  check(PlayerBotDefense.policy(PlayerClass.ASSASSIN,resist,1,24,100,true,true,false)==0,"Pure magical-resistance charges are not wasted on melee pressure");
  check(PlayerBotDefense.policy(PlayerClass.TEMPLAR,skin,1,50,100,true,true,false)==0,"Tank does not gain foreign Mage proactive shield policy");
  check(PlayerBotDefense.peelPolicy(PlayerClass.SORCERER,Role.RANGED,root,5,true,false,false)==50,"Close Mage root uses Frost Nova purpose and band");
  check(PlayerBotDefense.peelPolicy(PlayerClass.SORCERER,Role.RANGED,root,5.01,true,false,false)==0,"Mage root does not reach-pull a distant mob");
  check(PlayerBotDefense.peelPolicy(PlayerClass.SORCERER,Role.RANGED,root,2,false,false,false)==0,"Mage does not peel an enemy assigned to another party member");
  check(PlayerBotDefense.peelPolicy(PlayerClass.SORCERER,Role.TANK,root,2,true,false,false)==0,"Assigned tank does not kite its own enemy");
  check(PlayerBotDefense.peelPolicy(PlayerClass.SORCERER,Role.MELEE,root,2,true,false,false)==0,"Assigned melee role does not gain ranged peel behavior");
  check(PlayerBotDefense.peelPolicy(PlayerClass.SORCERER,Role.RANGED,root,2,true,true,false)==0,"Already immobilized enemy receives no repeated root");
  check(PlayerBotDefense.peelPolicy(PlayerClass.RANGER,Role.RANGED,shot,15,true,false,false)==20,"Hunter aggro snare is useful across legal native attack range");
  check(PlayerBotDefense.peelPolicy(PlayerClass.RANGER,Role.RANGED,shot,15,true,false,true)==0,"Existing snare does not receive a duplicate");
  check(PlayerBotDefense.peelPolicy(PlayerClass.RANGER,Role.RANGED,arrow,4,true,false,false)==21,"Close native Ranger root uses stronger immobilization purpose");
  check(PlayerBotDefense.peelPolicy(PlayerClass.RANGER,Role.RANGED,arrow,6,true,false,false)==0,"Root is reserved for close Ranger pressure");
  check(PlayerBotDefense.peelPolicy(PlayerClass.TEMPLAR,Role.TANK,root,2,true,false,false)==0,"Tank threat management remains separate");
  var fixture=(SkillData)context.createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
    <skill_template skill_id="990301" name="pure snare" activation="ACTIVE" skilltype="MAGICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE" first_target_range="20"/><effects><snare e="1" duration2="10000"><change stat="SPEED" func="PERCENT" value="-50"/></snare></effects></skill_template>
    <skill_template skill_id="990302" name="mana protection" activation="ACTIVE" skilltype="MAGICAL"><properties first_target="ME" target_relation="FRIEND" target_type="ONLYONE"/><effects><mpshield value="2000" mp_value="50" e="1"/></effects></skill_template>
    <skill_template skill_id="990303" name="physical avoidance" activation="ACTIVE" skilltype="MAGICAL"><properties first_target="ME" target_relation="FRIEND" target_type="ONLYONE"/><effects><alwaysdodge value="1" e="1" duration2="10000"/></effects></skill_template>
   </skill_data>
   """));
  check(PlayerBotSkills.classify(fixture.getSkillTemplate(990301))==SkillKind.CONTROL,"Pure native snare participates in control planning");
  check(PlayerBotDefense.policy(PlayerClass.MAGE,fixture.getSkillTemplate(990302),1,44,50,true,true,false)==85,"Learned native mana shield uses original low-health band");
  check(PlayerBotDefense.policy(PlayerClass.MAGE,fixture.getSkillTemplate(990302),1,44,0,true,true,false)==0,"Mana shield is not newly enabled with empty mana");
  check(PlayerBotDefense.policy(PlayerClass.ASSASSIN,fixture.getSkillTemplate(990303),1,44,100,true,true,false)==29,"Rogue low-health evasion band retained");
  check(PlayerBotDefense.policy(PlayerClass.ASSASSIN,fixture.getSkillTemplate(990303),1,45,100,true,true,false)==0,"Rogue low-health threshold bounded");
  System.out.println("OK: "+checks+" native defense/control classifications, pinned class bands and tactical threshold comparisons");
 }
}
