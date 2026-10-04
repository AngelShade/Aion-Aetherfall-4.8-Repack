package com.aionemu.gameserver.services.playerbot;
import java.io.StringReader;
import java.nio.file.Path;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.SkillData;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.skillengine.model.Effect;

/** Native metadata and upstream priority contracts; actual target selection/casts use the runtime fixture. */
public final class PlayerBotEnemyUtilityCheck {
 static int checks;static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  var context=JAXBContext.newInstance(SkillData.class);
  var production=(SkillData)context.createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/skills/skill_templates.xml").toFile());
  for(int id:new int[]{305,3532,3550,3570,3740,3781}) {
   var skill=production.getSkillTemplate(id);check(PlayerBotSkills.classify(skill)==PlayerBotRules.SkillKind.DAMAGE,"Learned native purge participates in combat: "+id);
   check(PlayerBotEnemyUtility.purge(skill),"Native purge metadata recognized: "+id);
  }
  check(!PlayerBotEnemyUtility.damagePayload(production.getSkillTemplate(3532)),"Counterattack damage depends on an actual dispel; no arbitrary empty-target damage");
  check(PlayerBotEnemyUtility.damagePayload(production.getSkillTemplate(3550)),"Magic Implosion retains its native launched effect when the enemy has no removable buff");
  check(PlayerBotEnemyUtility.purgePriority(PlayerClass.SPIRIT_MASTER,0)==0,"Empty target does not trigger a purge");
  check(PlayerBotEnemyUtility.purgePriority(PlayerClass.SPIRIT_MASTER,4)==54,"Warlock Devour Magic purpose uses dispel band");
  check(PlayerBotEnemyUtility.purgePriority(PlayerClass.SORCERER,4)==44,"Mage Spellsteal purpose uses interrupt band with native removal");
  check(PlayerBotEnemyUtility.purgePriority(PlayerClass.RANGER,4)==65,"Hunter Tranq purpose precedes ordinary encounter attacks");
  var fixture=(SkillData)context.createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
    <skill_template skill_id="990201" name="buff" activation="PROVOKED" skilltype="MAGICAL" tslot="BUFF" dispel_category="BUFF" req_dispel_level="2"><effects><shield value="200" e="1"/></effects></skill_template>
    <skill_template skill_id="990202" name="npc buff" activation="PROVOKED" skilltype="MAGICAL" tslot="BUFF" dispel_category="NPC_BUFF" req_dispel_level="3"><effects><shield value="200" e="1"/></effects></skill_template>
    <skill_template skill_id="990203" name="debuff" activation="PROVOKED" skilltype="MAGICAL" tslot="DEBUFF" dispel_category="ALL" req_dispel_level="1"><effects><root e="1"/></effects></skill_template>
    <skill_template skill_id="990204" name="physical debuff" activation="PROVOKED" skilltype="MAGICAL" tslot="DEBUFF" dispel_category="DEBUFF_PHYSICAL" req_dispel_level="1"><effects><root e="1"/></effects></skill_template>
   </skill_data>
   """));
  var buff=new Effect(null,null,fixture.getSkillTemplate(990201),1);
  var npcBuff=new Effect(null,null,fixture.getSkillTemplate(990202),1);
  var ownDebuff=new Effect(null,null,fixture.getSkillTemplate(990203),1);
  var physical=new Effect(null,null,fixture.getSkillTemplate(990204),1);
  check(!PlayerBotEnemyUtility.eligible(buff,false,false,null,1),"Player buff dispel level restriction preserved");
  check(PlayerBotEnemyUtility.eligible(buff,false,false,null,2),"Sufficient native dispel level accepted");
  check(!PlayerBotEnemyUtility.eligible(npcBuff,false,false,null,3),"Ordinary purge cannot erase native NPC_BUFF");
  check(PlayerBotEnemyUtility.eligible(npcBuff,true,false,null,1),"Native NPC_BUFF dispel intentionally does not test player buff dispel level");
  check(!PlayerBotEnemyUtility.eligible(buff,true,false,null,3),"NPC-only purge cannot erase player buff category");
  check(PlayerBotEnemyUtility.eligible(ownDebuff,false,true,null,1),"Counter dispel count accounts for native own-debuff consumption");
  check(!PlayerBotEnemyUtility.eligible(physical,false,true,null,1),"Counter purge retains native category limits");
  check(PlayerBotEnemyUtility.severity(fixture.getSkillTemplate(990201))>PlayerBotEnemyUtility.severity(fixture.getSkillTemplate(990203)),"Shield protection outranks ordinary utility metadata");
  System.out.println("OK: "+checks+" production purge classifications, native category/level contracts and pinned class priority comparisons");
 }
}


