package com.aionemu.gameserver.services.playerbot;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.SkillData;
import com.aionemu.gameserver.skillengine.effect.SignetBurstEffect;

public final class PlayerBotOffenseCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  var context=JAXBContext.newInstance(SkillData.class);
  var data=(SkillData)context.createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
    <skill_template skill_id="990401" activation="ACTIVE" stack="TEST_DOT" lvl="1" duration="1000" tslot="DEBUFF"><effects><spellatk value="100" e="1" checktime="3000" duration2="15000" effectid="77"/></effects></skill_template>
    <skill_template skill_id="990402" activation="ACTIVE" stack="TEST_DOT" lvl="2" duration="1000" tslot="DEBUFF"><effects><spellatk value="200" e="1" checktime="3000" duration2="15000" effectid="77"/></effects></skill_template>
    <skill_template skill_id="990403" activation="ACTIVE" stack="OTHER_DOT" lvl="1" tslot="DEBUFF"><effects><poison value="100" e="1" checktime="2000" duration2="10000" effectid="78"/></effects></skill_template>
    <skill_template skill_id="990404" activation="ACTIVE" stack="TEST_HYBRID" lvl="1" tslot="DEBUFF"><effects><skillatk value="100" e="1"/><bleed value="100" e="2" checktime="2000" duration2="10000"/></effects></skill_template>
    <skill_template skill_id="990405" activation="ACTIVE" stack="RUNE_TEST" lvl="1" tslot="DEBUFF"><effects><signet e="1" duration2="15000"/></effects></skill_template>
    <skill_template skill_id="990406" activation="ACTIVE" lvl="1"><effects><signetburst value="100" e="1" signet="RUNE_TEST" signetlvl="5"/></effects></skill_template>
   </skill_data>
   """));
  var dot=data.getSkillTemplate(990401);var high=data.getSkillTemplate(990402);var other=data.getSkillTemplate(990403);var hybrid=data.getSkillTemplate(990404);var rune=data.getSkillTemplate(990405);var burst=data.getSkillTemplate(990406);
  check(PlayerBotOffense.refresh(dot,1,List.of(),100),"Missing DoT is maintained");
  check(!PlayerBotOffense.refresh(dot,1,List.of(new PlayerBotOffense.Active(dot,1,8000)),100),"DoT does not overwrite its ongoing damage");
  check(PlayerBotOffense.refresh(dot,1,List.of(new PlayerBotOffense.Active(dot,1,4000)),100),"Last native tick plus cast duration permits refresh");
  check(!PlayerBotOffense.refresh(dot,1,List.of(new PlayerBotOffense.Active(high,1,1000)),100),"Low-rank refresh does not downgrade higher native rank");
  check(!PlayerBotOffense.refresh(dot,1,List.of(new PlayerBotOffense.Active(dot,2,1000)),100),"Low learned level does not replace a stronger learned level");
  check(PlayerBotOffense.refresh(dot,1,List.of(new PlayerBotOffense.Active(other,1,9000)),100),"Independent DoT families do not suppress each other");
  check(!PlayerBotOffense.refresh(dot,1,List.of(),24),"Nearly defeated enemy is finished with direct damage instead");
  check(PlayerBotOffense.refresh(hybrid,1,List.of(),20),"Hybrid damage remains useful against a nearly defeated enemy");
  check(PlayerBotOffense.runes(burst,List.of())==0,"Missing rune is not a finisher resource");
  check(PlayerBotOffense.runes(burst,List.of(new PlayerBotOffense.Active(rune,4,10000)))==4,"Native rune level replaces WoW combo points");
  check(PlayerBotOffense.runes(burst,List.of(new PlayerBotOffense.Active(rune,9,10000)))==5,"Native burst cap limits usable rune level");
  check(PlayerBotOffense.runes(burst,List.of(new PlayerBotOffense.Active(dot,5,10000)))==0,"Different native stack is not a rune");
  check(PlayerBotOffense.finisher(0,100,10000,1000,true)==0,"An available builder precedes a weak no-rune finisher");
  check(PlayerBotOffense.finisher(0,5,10000,1000,false)==8,"Native reduced-damage no-rune fallback remains available without a builder");
  check(PlayerBotOffense.finisher(4,100,10000,1000,true)==25,"Original four-point finisher band is preserved");
  check(PlayerBotOffense.finisher(2,24,10000,1000,true)==24,"Rune is spent before target death");
  check(PlayerBotOffense.finisher(2,100,2000,1000,true)==24,"Rune is spent before expiration");
  check(PlayerBotOffense.finisher(2,100,10000,1000,true)==0,"Available builder is preferred while rune has time");
  check(PlayerBotOffense.finisher(2,100,10000,1000,false)==12,"Build without an available builder still spends its rune");
  var production=(SkillData)context.createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/skills/skill_templates.xml").toFile());
  int periodic=0,finishers=0;
  for(var skill:production.getSkillTemplates()) {
   if(skill.getEffects()==null)continue;
   if(PlayerBotOffense.periodic(skill)) {periodic++;check(PlayerBotOffense.refreshWindow(skill)>=750,"Production periodic cast window is bounded: "+skill.getSkillId());}
   for(var effect:skill.getEffects().getEffects())if(effect instanceof SignetBurstEffect signet && signet.getSignet()!=null) {
    finishers++;check(PlayerBotOffense.runes(skill,List.of())==0,"Production finisher requires its native rune: "+skill.getSkillId());
   }
  }
  check(periodic>100 && finishers>10,"Checks cover real production families, not just fixtures");
  var signets=(com.aionemu.gameserver.dataholders.SignetDataTemplates)javax.xml.bind.JAXBContext.newInstance(
   com.aionemu.gameserver.dataholders.SignetDataTemplates.class).createUnmarshaller().unmarshal(
    Path.of("game-server/data/static_data/skills/signet_data_templates.xml").toFile());
  for(var family:com.aionemu.gameserver.skillengine.model.SignetEnum.values()) {
   var zero=signets.getSignetData(family,0);
   if(zero!=null)check(zero.getDamageMultiplier()>0,"Native zero-rune family retains reduced damage: "+family);
  }
  System.out.println("OK: "+checks+" offensive resource/refresh decisions and "+periodic+" production periodic templates / "+finishers+" finishers; actual casts remain native acceptance");
 }
}
