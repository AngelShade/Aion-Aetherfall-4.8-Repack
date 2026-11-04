package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import com.aionemu.commons.configuration.Property;
import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.questEngine.model.*;

/** World-free production decisions. Run with java -ea; no actors or game/database services. */
public final class PlayerBotWardenExpansionCheck {
 private static int checks;
 static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
 static void defaults(Class<?> type)throws Exception {
  for(var field:type.getFields()) {var p=field.getAnnotation(Property.class);if(p==null)continue;String v=p.defaultValue();
   if(field.getType()==int.class)field.setInt(null,Integer.parseInt(v));else if(field.getType()==boolean.class)field.setBoolean(null,Boolean.parseBoolean(v));
   else if(field.getType()==long.class)field.setLong(null,Long.parseLong(v));else if(field.getType()==String.class)field.set(null,v);
  }
 }
 private static String row() {
  var points=new ArrayList<String>();for(int i=0;i<=200;i++)points.add((i*8)+",20,10");
  return "beat:test\t210020000\tELYOS\t25\t203000\tFirst Outpost\tSecond Outpost\t1600\t6\t"+String.join(";",points);
 }
 private static void rejected(String row) {
  try{PlayerBotWardenBeats.parse(row,null);throw new AssertionError("Accepted invalid beat");}catch(IllegalArgumentException expected){checks++;}
 }
 private static void beats(Path work)throws Exception {
  var beat=PlayerBotWardenBeats.parse(row(),null);check(beat.length()==1600,"Measured beat length");check(beat.route().points().size()==201,"Dense road samples retained");
  for(float distance:new float[]{0,80,100,125,149,150,175})check(Math.abs(beat.route().distance(800,20+distance,10)-distance)<.01,"Indexed corridor includes escort leash "+distance);
  check(beat.route().distance(800,20,35)==25,"Stacked floors use 3D distance");
  rejected(row().replace("\t1600\t","\tNaN\t"));rejected(row().replace("\t6\t","\t4\t"));rejected(row().replace("\tELYOS\t","\tNONE\t"));
  rejected(row().replace("Second Outpost","First Outpost"));rejected(row().replace("8,20,10","80,20,10"));rejected(row().replace("8,20,10","NaN,20,10"));
  Path input=work.resolve("beat-rejection.tsv");Files.writeString(input,row()+"\ninvalid\n"+row()+"\n");
  check(PlayerBotWardenBeats.load(input,m->null).size()==1,"Loader skips malformed/duplicate rows without losing valid beats");
  var columns=row().split("\t",-1);columns[0]="beat:reversed";columns[9]=String.join(";",Arrays.asList(columns[9].split(";")).reversed());
  String reverse=String.join("\t",columns);Path geometries=work.resolve("beat-geometry-rejection.tsv");
  Files.writeString(geometries,row()+"\n"+row().replace("beat:test","beat:other")+"\n"+reverse+"\n");
  check(PlayerBotWardenBeats.load(geometries,m->null).size()==1,"Repeated physical road geometry is rejected across IDs and both directions");
  check(PlayerBotWardenBeats.geometry(beat.route()).equals(PlayerBotWardenBeats.geometry(PlayerBotWardenBeats.parse(reverse,null).route())),"Reverse geometry has one native road identity");
  check(beat.route().map()!=110010000 && beat.route().map()!=120010000,"Synthetic field beat does not authorize a capital");
 }
 private static void migration()throws Exception {
  var beat=PlayerBotWardenBeats.parse(row(),null).route();var p=new Properties();int account=PlayerBotPopulationPolicy.account(Race.ELYOS,0);
  p.setProperty("account",""+account);p.setProperty("character","100001");p.setProperty("career","WARDEN");p.setProperty("rank","PEDDLER");
  p.setProperty("route","old-walker");p.setProperty("point","14");p.setProperty("visitor","1234");p.setProperty("escortUntil","99999");
  check(PlayerBotRoadDuty.migrate(p,new PlayerBotPatrolRoutes.Route("old-walker",beat.map(),beat.race(),beat.level(),beat.npc(),"Old",beat.points(),beat.nearby(),true)),"Even a guardable old walker migrates to a beat");
  check(p.getProperty("career").equals("WARDEN") && !p.containsKey("route") && !p.containsKey("visitor"),"Migration retains stable identity and clears old escort");
  check(!PlayerBotRoadDuty.migrate(p,null),"Migration is idempotent");p.setProperty("route",beat.id());
  check(!PlayerBotRoadDuty.migrate(p,beat),"Saved beat survives restart");
  PlayerBotMetadata.validate(account,100001,"road-duty",PlayerBotMetadata.values(p));checks++;
  for(String key:List.of("fairWarned","escortWarned")) {p.setProperty(key,"true");PlayerBotMetadata.validate(account,100001,"road-duty",PlayerBotMetadata.values(p));checks++;p.setProperty(key,"yes");
   try{PlayerBotMetadata.validate(account,100001,"road-duty",PlayerBotMetadata.values(p));throw new AssertionError("Invalid warning accepted");}catch(java.io.IOException expected){checks++;}p.remove(key);}
  for(String key:List.of("stopUntil","escortUntil","waitUntil","distressAt")) {
   for(String value:List.of("0","9223372036854775807")) {p.setProperty(key,value);PlayerBotMetadata.validate(account,100001,"road-duty",PlayerBotMetadata.values(p));checks++;}
   for(String value:List.of("-1","later","9223372036854775808")) {
    p.setProperty(key,value);try{PlayerBotMetadata.validate(account,100001,"road-duty",PlayerBotMetadata.values(p));throw new AssertionError("Invalid timer accepted: "+key+"="+value);}catch(java.io.IOException expected){checks++;}
   }p.remove(key);
  }
 }
 private static void catalogue(Path work)throws Exception {
  var beats=PlayerBotWardenBeats.load(Path.of("game-server/config/playerbots/warden-beats.tsv"),m->null);
  check(beats.size()==33,"Frozen catalogue retains thirty-three structurally valid beats");
  check(beats.values().stream().map(b->b.route().map()).distinct().count()==12,"Frozen catalogue retains twelve maps");
  check(beats.values().stream().noneMatch(b->b.route().map()==110010000 || b.route().map()==120010000),"Both capital maps produce zero catalogue beats");
  var spaces=PlayerBotWardenBeats.roadSpaces(beats);var lines=new ArrayList<String>();lines.add("beat\tlength\tcapacity\tspace\tsharedLength\tsharedCapacity");
  for(var b:new TreeMap<>(beats).values()) {
   var space=spaces.get(b.route().id());lines.add(b.route().id()+"\t"+b.length()+"\t"+PlayerBotWardenBeats.capacity(b.length())+"\t"+space.id()+"\t"+space.length()+"\t"+PlayerBotWardenBeats.capacity(space.length()));
  }
  Files.write(work.resolve("capacity-native.tsv"),lines);
 }
 private static void origins()throws Exception {
  int account=PlayerBotPopulationPolicy.account(Race.ELYOS,0);var values=new Properties();values.setProperty("career","WARDEN");values.setProperty("account",""+account);values.setProperty("character","100001");
  for(String key:List.of("escortStartX","escortStartY","escortStartZ"))values.setProperty(key,"-12.5");
  PlayerBotMetadata.validate(account,100001,"road-duty",PlayerBotMetadata.values(values));checks++;
  for(String invalid:List.of("NaN","Infinity","text")) {
   values.setProperty("escortStartX",invalid);try{PlayerBotMetadata.validate(account,100001,"road-duty",PlayerBotMetadata.values(values));throw new AssertionError("Invalid escort origin");}catch(java.io.IOException expected){checks++;}
  }
  values.remove("escortStartX");try{PlayerBotMetadata.validate(account,100001,"road-duty",PlayerBotMetadata.values(values));throw new AssertionError("Partial escort origin");}catch(java.io.IOException expected){checks++;}
 }
 private static void gait() {
  check(PlayerBotRoadDuty.walkDecision(true,false,false,false,false,true,true,0),"Ordinary patrol walks");
  for(int mask=0;mask<128;mask++) {
   boolean patrol=(mask&1)!=0,combat=(mask&2)!=0,support=(mask&4)!=0,escort=(mask&8)!=0,suspended=(mask&16)!=0,alive=(mask&32)!=0,autonomous=(mask&64)!=0;
   boolean expected=patrol && !combat && !support && !escort && !suspended && alive && autonomous;
   check(PlayerBotRoadDuty.walkDecision(patrol,combat,support,escort,suspended,alive,autonomous,40)==expected,"Gait admission across patrol/combat/support/escort/suspension/death/custody exits "+mask);
  }
  check(!PlayerBotRoadDuty.walkDecision(true,false,false,false,false,true,true,40.01),"Distant lane recovery runs");
  check(PlayerClass.TEMPLAR.createStatsTemplate(30).getWalkSpeed()==1.5f,"Real native base walk speed");
  check(PlayerClass.TEMPLAR.createStatsTemplate(30).getRunSpeed()==6,"Real native base run speed");
 }
 private static void services() {
  var aid=new PlayerBotWardens.Aid();aid.human=1;aid.next=2000;PlayerBotWardens.responseEnded(aid);
  check(aid.human==0 && aid.next==2000,"Response completion preserves the one-second observation deadline for rescue, ward and report");
  var route=PlayerBotWardenBeats.parse(row(),null).route();
  var start=new PlayerBotNavigation.Point(800,20,10);
  check(PlayerBotWardens.reachedEnd(route,start,new PlayerBotNavigation.Point(0,20,10)),"Traveller can finish at the first end regardless of Warden direction");
  check(PlayerBotWardens.reachedEnd(route,start,new PlayerBotNavigation.Point(1600,20,10)),"Traveller can finish at the second end");
  check(!PlayerBotWardens.reachedEnd(route,new PlayerBotNavigation.Point(45,20,10),new PlayerBotNavigation.Point(0,20,10)),"Starting near an end cannot finish before fifty metres");
  check(PlayerBotWardens.reachedEnd(route,new PlayerBotNavigation.Point(50,20,10),new PlayerBotNavigation.Point(0,20,10)),"Fifty-metre boundary admits beat end");
  check(!PlayerBotWardens.reachedEnd(route,start,new PlayerBotNavigation.Point(100,20,10)),"Mid-road travel cannot finish escort");
  int[] answers={0};var requester=new ResponseRequester(null);
  var response=new RequestResponseHandler<Creature>(null){@Override public void acceptRequest(Creature from,Player to){answers[0]++;}};
  check(PlayerBotWardens.ask(123,requester,response),"Native Road Bounty request occupies its slot");
  check(!PlayerBotWardens.ask(123,requester,response),"Unanswered question blocks duplicate with owner state");
  check(PlayerBotWardens.expireQuestion(123,requester,response),"Timeout clears owner state and native slot");
  check(!requester.respond(PlayerBotWardens.QUESTION,1) && answers[0]==0,"Expired answer cannot start escort");
  var newer=new RequestResponseHandler<Creature>(null){@Override public void acceptRequest(Creature from,Player to){answers[0]++;}};
  check(PlayerBotWardens.ask(123,requester,newer),"Re-invite reuses both owner and native request slots");
  check(!PlayerBotWardens.expireQuestion(123,requester,response),"Old timer preserves newer question");
  check(!requester.remove(PlayerBotWardens.QUESTION,response),"Native conditional removal preserves newer handler");
  check(requester.respond(PlayerBotWardens.QUESTION,1) && answers[0]==1,"New native question remains answerable");
  PlayerBotWardens.expireQuestion(123,requester,newer);
  check(!PlayerBotWardens.fair(19,81,20),"Below twenty percent is ineligible");check(PlayerBotWardens.fair(20,80,20),"Exactly twenty percent pays fully");
  check(PlayerBotWardens.fair(1,0,20),"Unassisted kills are untouched");check(!PlayerBotWardens.fair(0,100,20),"No human effort gives no credit");
  check(PlayerBotWardens.distress(true,true,true,59,false,60,100,1000,1000),"Boundary distress admitted");
  check(!PlayerBotWardens.distress(true,true,true,60,false,60,100,1000,1000),"Healthy ordinary fight does not draw Wardens");
  check(PlayerBotWardens.distress(true,true,true,100,true,60,100,1000,1000),"Elite fight draws help even at full HP");
  check(!PlayerBotWardens.distress(true,true,true,1,true,60.01,100,1000,1000),"No remote distress");
  check(!PlayerBotWardens.distress(true,true,true,1,true,60,100,1000,1001),"Distress cooldown enforced");
  check(PlayerBotWardens.rescue(true,true,false,false,4,7000,1000),"Clear corpse after six seconds admitted");
  check(!PlayerBotWardens.rescue(false,true,false,false,4,7000,1000),"PvP/environment deaths excluded");
  check(!PlayerBotWardens.rescue(true,true,true,false,4,7000,1000),"Hostile player blocks rescue");check(!PlayerBotWardens.rescue(true,true,false,true,4,7000,1000),"Monster pile blocks rescue");
  check(!PlayerBotWardens.rescue(true,true,false,false,4,6999,1000),"Arrival delay enforced");
  check(PlayerBotRoadBounty.counts(210020000,210020000,true,100),"Bounty corridor boundary admitted");
  check(!PlayerBotRoadBounty.counts(210020000,210040000,true,0),"Bounty stays on its accepted map");check(!PlayerBotRoadBounty.counts(210020000,210020000,false,0),"Gray monsters give no bounty credit");
  var q=new QuestState(PlayerBotRoadBounty.QUEST,QuestStatus.START);q.setQuestVar((210020000/10000)<<12);
  for(int kill=1;kill<=15;kill++){q.setQuestVarById(1,kill);check(q.getQuestVarById(1)==kill && (q.getQuestVars().getQuestVars()>>>12)*10000==210020000,"Native six-bit quest count and accepted map survive packing "+kill);}
  for(int company=1;company<=100;company++)check(PlayerBotWardenCompanies.name("Elysea Reshanta Road Wardens",company).matches("[a-zA-Z ]{2,32}"),"Native company overflow name "+company);
  check(PlayerBotWardenCompanies.name("Eltnen Road Wardens",2).equals("Eltnen Second Company"),"Player-facing second company name");
  check(PlayerBotLegionRepository.companyLevel(120)==4 && PlayerBotLegionRepository.companyLevel(240)==8,"Native legion level follows capacity");
 }
 public static void main(String[] args)throws Exception {
  if(!PlayerBotWardenExpansionCheck.class.desiredAssertionStatus())throw new IllegalArgumentException("Use -ea");
  defaults(PlayerBotWardenConfig.class);defaults(PlayerBotMerchantConfig.class);defaults(LegionConfig.class);Path work=Path.of(args[0]);Files.createDirectories(work);
  var config=new Properties();try(var in=Files.newInputStream(Path.of("game-server/config/playerbots/road-wardens.properties"))){config.load(in);}
  for(var field:PlayerBotWardenConfig.class.getFields()){var property=field.getAnnotation(Property.class);if(property!=null)check(property.defaultValue().equals(config.getProperty(property.key())),"Shipped Warden setting matches its native default: "+property.key());}
  check(PlayerBotWardenConfig.SPACING==40 && PlayerBotWardenConfig.BEAT_CAPACITY_METRES==150,"Physical gap and admission density have distinct defaults");
  check(PlayerBotWardenBeats.capacity(299)==1 && PlayerBotWardenBeats.capacity(300)==2 && PlayerBotWardenBeats.capacity(1)==1,"Beat capacity floors metres with minimum one");
  var original=PlayerBotWardenBeats.parse(row(),null);var overlap=PlayerBotWardenBeats.parse(row().replace("beat:test","beat:overlap"),null);
  var spaces=PlayerBotWardenBeats.roadSpaces(Map.of(original.route().id(),original,overlap.route().id(),overlap));
  check(spaces.get("beat:test").equals(spaces.get("beat:overlap")) && spaces.get("beat:test").length()==1600,"Overlapping exact native segments share one physical capacity budget");
  var raised=PlayerBotWardenBeats.parse(row().replace("beat:test","beat:raised").replace(",20,10",",20,30"),null);
  spaces=PlayerBotWardenBeats.roadSpaces(Map.of(original.route().id(),original,raised.route().id(),raised));
  check(!spaces.get("beat:test").id().equals(spaces.get("beat:raised").id()),"Stacked floors retain distinct road budgets");
  beats(work);catalogue(work);migration();origins();gait();services();System.out.println("OK: "+checks+" world-free Warden expansion checks");
 }
}
