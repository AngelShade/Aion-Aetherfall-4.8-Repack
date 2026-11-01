package com.aionemu.gameserver.services;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.QuestsData;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.services.playerbot.PlayerBotJourneyEvidence;

/** Exercises the actual commit/recovery methods in a disposable empty schema. */
public final class PoetaJourneyDatabaseCheck {
	private static int checks;
	private static void check(boolean pass,String label) { checks++; if (!pass) throw new AssertionError(label); }
	private static long value(Connection c,String sql) throws SQLException {
		try (Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)) { r.next(); return r.getLong(1); }
	}
	private static String growth(String origin,String initial,String rank,long earned,int character) {
		return "{\"account\":\"9\",\"autoEquipRarity\":\"GOOD\",\"character\":\""+character+"\",\"combatRank\":\""+rank+"\",\"earnedLearningXp\":\""+earned+"\",\"initialCombatRank\":\""+initial+"\",\"origin\":\""+origin+"\",\"rolledAdvancedClass\":\"RANGER\",\"version\":\"1\"}";
	}
	@SuppressWarnings("unchecked") private static List<Map<String,Object>> rewards(Map<String,Object> state) { return (List<Map<String,Object>>) state.get("rewards"); }
	private static int doneCount(Map<String,Object> state) { return state.get("chapters").toString().split("done=true",-1).length-1; }
	private static String text(Connection c,String sql) throws SQLException {
		try (Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)) { r.next(); StringBuilder row=new StringBuilder(); for(int i=1;i<=r.getMetaData().getColumnCount();i++) { if(i>1) row.append('|'); row.append(r.getString(i)); } return row.toString(); }
	}
	private static void player(Connection c,int id) throws SQLException {
		player(c,id,Race.ELYOS,PlayerClass.WARRIOR);
	}
	private static void player(Connection c,int id,Race race,PlayerClass base) throws SQLException {
		PoetaJourneyService.update(c,"INSERT INTO players(id,name,account_id,account_name,x,y,z,heading,world_id,gender,race,player_class) VALUES(?, ?,1,'fixture',1,2,3,0,?,'FEMALE',?,?)",id,"Fixture"+id,PoetaJourneyRules.journey(race).starterWorld(),race.name(),base.name());
	}
	private static Object[] item(int id,int player) {
		Object[] v=new Object[29]; Arrays.fill(v,0);v[0]=id;v[1]=162001057;v[2]=5L;v[3]=null;v[5]=null;v[8]=player;v[12]=7;v[15]=162001057; return v;
	}
	public static void main(String[] args) throws Exception {
		Properties p=new Properties(); Path deploy=Path.of(args[0]);
		try(var in=Files.newInputStream(deploy.resolve("config/network/database.properties"))) { p.load(in); }
		try(var in=Files.newInputStream(deploy.resolve("config/mygs.properties"))) { p.load(in); }
		String url=p.getProperty("database.url").replace("${gameserver.timezone}","UTC"),user=p.getProperty("database.user"),password=p.getProperty("database.password");
		String schema="aion_journey_check_"+System.currentTimeMillis();check(schema.matches("aion_journey_check_[0-9]+"),"isolated schema name");
		QuestsData data=(QuestsData) JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/quest_data/quest_data.xml").toFile());
		List<QuestTemplate> quests=data.getQuestTemplates().stream().filter(q -> PoetaJourneyRules.skippedQuest(q,Race.ELYOS)).toList();
		try(Connection live=DriverManager.getConnection(url,user,password);Statement s=live.createStatement()) {
			String source=live.getCatalog();check(source.matches("[A-Za-z0-9_]+")&&!source.equals(schema),"source remains read-only");
			s.execute("CREATE DATABASE `"+schema+"`");
			try {
				for(String table:List.of("players","player_quests","player_titles","player_bind_point","inventory","mail","playerbot_ticket_receipts","playerbot_recruitment_operations","playerbot_metadata")) s.execute("CREATE TABLE `"+schema+"`."+table+" LIKE `"+source+"`."+table);
				try(Connection c=DriverManager.getConnection(url.replace("/"+source,"/"+schema),user,password);Statement ddl=c.createStatement()) {
					check(c.getCatalog().equals(schema),"test uses its own schema");
					String journeySchema=Files.readString(Path.of("game-server/config/journey/schema.sql"));
					for(String sql:journeySchema.split(";")) if(!sql.isBlank()) ddl.execute(sql);
					player(c,101);player(c,102);player(c,103);
					var mails=List.of(new PoetaJourneyService.MailAward(1001,item(2001,101),0),new PoetaJourneyService.MailAward(1002,null,250000));
					PoetaJourneyService.commitSkip(c,101,PlayerClass.GLADIATOR,100000,1,quests,Set.of(1,4,7),mails);
					check(value(c,"SELECT COUNT(*) FROM player_quests WHERE player_id=101 AND status='COMPLETE'")==41,"41 Poeta quests completed; Sanctum ceremony remains active");
					check(value(c,"SELECT quest_vars FROM player_quests WHERE player_id=101 AND quest_id=1007 AND status='START'")==1,"ceremony starts with Leah after Pernos teleport");
					check(PoetaJourneyService.welcomePending(c,101),"welcome survives map entry");
					PoetaJourneyService.acknowledge(c,101);c.commit();
					check(!PoetaJourneyService.welcomePending(c,101),"welcome closes only after acknowledgement");
					check(value(c,"SELECT reward FROM player_quests WHERE player_id=101 AND quest_id=1007")==0,"warrior ceremony group");
					check(value(c,"SELECT FIND_IN_SET('1007',completed_quests) FROM poeta_journey WHERE player_id=101")==0,"new skip receipt allows ceremony rewards at turn-in");
					check(value(c,"SELECT COUNT(*) FROM player_quests WHERE player_id=101 AND quest_id=1913 AND status='START'")==1,"onward quest active");
					check(value(c,"SELECT COUNT(*) FROM mail WHERE mail_recipient_id=101")==2,"item and currency mails");
					check(value(c,"SELECT COUNT(*) FROM inventory WHERE item_owner=101 AND item_location=7")==1,"mail attachment persisted");
					check(value(c,"SELECT SUM(attached_kinah_count) FROM mail WHERE mail_recipient_id=101")==250000,"mailed currency");
					check(value(c,"SELECT world_id FROM players WHERE id=101")==110010000,"Sanctum destination");
					check(value(c,"SELECT map_id FROM player_bind_point WHERE player_id=101")==110010000,"Sanctum return point");
					check(value(c,"SELECT COUNT(*) FROM player_titles WHERE player_id=101")==3,"quest titles preserved");
					boolean duplicate=false;try { PoetaJourneyService.commitSkip(c,101,PlayerClass.GLADIATOR,100000,1,quests,Set.of(1),mails); }catch(IllegalArgumentException e){duplicate=true;}
					check(duplicate && value(c,"SELECT COUNT(*) FROM mail WHERE mail_recipient_id=101")==2,"repeated click cannot duplicate rewards");
					var broken=List.of(new PoetaJourneyService.MailAward(1003,item(2002,102),0),new PoetaJourneyService.MailAward(1003,item(2003,102),0));
					boolean failed=false;try { PoetaJourneyService.commitSkip(c,102,PlayerClass.TEMPLAR,100000,1,quests,Set.of(1),broken); }catch(SQLException e){failed=true;}
					check(failed,"mid-bundle failure simulated");
					check(value(c,"SELECT COUNT(*) FROM inventory WHERE item_owner=102")==0 && value(c,"SELECT COUNT(*) FROM mail WHERE mail_recipient_id=102")==0,"failed attachments and letters rolled back");
					check(value(c,"SELECT COUNT(*) FROM player_quests WHERE player_id=102")==0 && value(c,"SELECT COUNT(*) FROM poeta_journey WHERE player_id=102")==0,"failed quest completion and decision rolled back");
					check(value(c,"SELECT world_id FROM players WHERE id=102")==210010000,"failed skip preserves starting position");
					for(int i=0;i<200;i++) PoetaJourneyService.update(c,"INSERT INTO mail(mail_unique_id,mail_recipient_id,sender_name,mail_title,mail_message,unread,attached_item_id,attached_kinah_count,express) VALUES(?,103,'fixture','fixture','fixture',1,0,0,0)",3000+i);c.commit();
					boolean full=false;try { PoetaJourneyService.commitSkip(c,103,PlayerClass.GLADIATOR,100000,1,quests,Set.of(),List.of(new PoetaJourneyService.MailAward(4001,null,1))); }catch(IllegalArgumentException e){full=true;}
					check(full && value(c,"SELECT COUNT(*) FROM player_quests WHERE player_id=103")==0,"full mailbox refuses the whole skip");
					PoetaJourneyService.update(c,"UPDATE players SET player_class='WARRIOR',exp=0,quest_expands=0,world_id=210010000 WHERE id=101");
					PoetaJourneyService.update(c,"UPDATE player_quests SET status='START',complete_count=0 WHERE player_id=101 AND quest_id=1007");
					PoetaJourneyService.update(c,"UPDATE poeta_journey SET needs_recovery=1 WHERE player_id=101");c.commit();
					check(PoetaJourneyService.recover(c,101),"post-commit refresh failure recovered");
					check(value(c,"SELECT exp FROM players WHERE id=101")==100000 && value(c,"SELECT world_id FROM players WHERE id=101")==110010000,"saved class progression and position restored");
					check(value(c,"SELECT complete_count FROM player_quests WHERE player_id=101 AND quest_id=1007")==0,"active ceremony is not in completed journal");
					check(value(c,"SELECT COUNT(*) FROM mail WHERE mail_recipient_id=101")==2,"recovery does not remail rewards");
					check(!PoetaJourneyService.recover(c,101),"recovery is one-shot");
					PoetaJourneyService.update(c,"UPDATE player_quests SET status='COMPLETE',quest_vars=0,complete_count=1 WHERE player_id=101 AND quest_id=1007");
					PoetaJourneyService.saveCeremony(c,101,PlayerClass.GLADIATOR);c.commit();
					check(value(c,"SELECT COUNT(*) FROM player_quests WHERE player_id=101 AND quest_id=1007 AND status='COMPLETE'")==1,"completed ceremony is not reopened or rewarded twice");
					PoetaJourneyService.update(c,"UPDATE poeta_journey SET completed_quests=CONCAT(completed_quests,',1007') WHERE player_id=101");
					check(value(c,"SELECT FIND_IN_SET('1007',completed_quests) FROM poeta_journey WHERE player_id=101")>0,"legacy mailed ceremony receipt remains distinguishable");
					PoetaJourneyService.update(c,"ALTER TABLE poeta_journey DROP COLUMN welcome_pending, DROP COLUMN journey_version");c.commit();
					PoetaJourneyService.migrate(c);
					check(value(c,"SELECT quest_vars FROM player_quests WHERE player_id=101 AND quest_id=1007 AND status='START'")==1,"earlier skip repaired to Sanctum step");
					check(PoetaJourneyService.welcomePending(c,101)&&value(c,"SELECT COUNT(*) FROM mail WHERE mail_recipient_id=101")==2,"repair opens welcome without remailing");
					PoetaJourneyService.update(c,"UPDATE player_quests SET status='COMPLETE',quest_vars=0,complete_count=1 WHERE player_id=101 AND quest_id=1007");c.commit();
					PoetaJourneyService.migrate(c);
					check(value(c,"SELECT COUNT(*) FROM player_quests WHERE player_id=101 AND quest_id=1007 AND status='COMPLETE'")==1,"later restarts preserve finished ceremony");
					List<QuestTemplate> asmodian=data.getQuestTemplates().stream().filter(q -> PoetaJourneyRules.skippedQuest(q,Race.ASMODIANS)).toList();
					int fixture=500;
					for(PlayerClass advanced:PlayerClass.values()) if(!advanced.isStartingClass()) {
						int id=fixture++;player(c,id,Race.ASMODIANS,advanced.getStartingClass());c.commit();
						boolean wrongRace=false;
						try { PoetaJourneyService.commitSkip(c,id,advanced,100000,1,quests,Set.of(),List.of()); }
						catch(IllegalArgumentException e) { wrongRace=true; }
						check(wrongRace && value(c,"SELECT COUNT(*) FROM player_quests WHERE player_id="+id)==0,"wrong faction transaction refused "+advanced);
						var letters=List.of(new PoetaJourneyService.MailAward(10000+id,item(20000+id,id),0),new PoetaJourneyService.MailAward(30000+id,null,100));
						PoetaJourneyService.commitSkip(c,id,advanced,100000,1,asmodian,Set.of(),letters);
						check(value(c,"SELECT COUNT(*) FROM player_quests WHERE player_id="+id+" AND status='COMPLETE'")==47,"47 Ishalgen quests completed "+advanced);
						check(value(c,"SELECT quest_vars FROM player_quests WHERE player_id="+id+" AND quest_id=2009 AND status='START' AND complete_count=0")==1,"ceremony begins with Heimdall after Munin "+advanced);
						check(value(c,"SELECT reward FROM player_quests WHERE player_id="+id+" AND quest_id=2009")==PoetaJourneyRules.ceremonyGroup(advanced),"Asmodian ceremony class group "+advanced);
						check(value(c,"SELECT COUNT(*) FROM player_quests WHERE player_id="+id+" AND quest_id="+PoetaJourneyRules.dispatchQuest(Race.ASMODIANS,advanced)+" AND status='START'")==1,"Altgard dispatch "+advanced);
						check(value(c,"SELECT COUNT(*) FROM player_quests WHERE player_id="+id+" AND quest_id IN (1000,1006,1007,1913)")==0,"no Elyos progression "+advanced);
						check(value(c,"SELECT world_id FROM players WHERE id="+id)==120010000 && value(c,"SELECT map_id FROM player_bind_point WHERE player_id="+id)==120010000,"Pandaemonium teleport and bind "+advanced);
						check(value(c,"SELECT COUNT(*) FROM mail WHERE mail_recipient_id="+id+" AND mail_title='Ishalgen rewards' AND mail_message LIKE '%Heimdall%' AND mail_message LIKE '%Altgard%'")==2,"Asmodian reward mail and directions "+advanced);
						check(value(c,"SELECT COUNT(*) FROM inventory WHERE item_owner="+id+" AND item_location=7")==1 && value(c,"SELECT FIND_IN_SET('2009',completed_quests) FROM poeta_journey WHERE player_id="+id)==0,"attachment persisted and ceremony rewards deferred "+advanced);
						PoetaJourneyService.update(c,"UPDATE players SET world_id=220010000,exp=0 WHERE id=?",id);
						PoetaJourneyService.update(c,"UPDATE poeta_journey SET needs_recovery=1 WHERE player_id=?",id);c.commit();
						check(PoetaJourneyService.recover(c,id) && value(c,"SELECT world_id FROM players WHERE id="+id)==120010000,"Asmodian recovery returns to correct capital "+advanced);
						check(value(c,"SELECT COUNT(*) FROM mail WHERE mail_recipient_id="+id)==2 && !PoetaJourneyService.recover(c,id),"Asmodian recovery never remails "+advanced);
						boolean repeated=false;try { PoetaJourneyService.commitSkip(c,id,advanced,100000,1,asmodian,Set.of(),letters); } catch(IllegalArgumentException e) { repeated=true; }
						check(repeated && value(c,"SELECT COUNT(*) FROM mail WHERE mail_recipient_id="+id)==2,"Asmodian duplicate claim blocked "+advanced);
					}
					String saved="SELECT (SELECT COUNT(*) FROM mail)+(SELECT COUNT(*) FROM inventory)+(SELECT COUNT(*) FROM player_quests)+(SELECT COUNT(*) FROM player_titles)+(SELECT COALESCE(SUM(exp),0) FROM players)";
					long before=value(c,saved);
					player(c,601);PoetaJourneyService.update(c,"UPDATE poeta_journey SET welcome_pending=1 WHERE player_id=500");c.commit();
					check(PoetaJourneyService.welcomePending(c,500)&&!PoetaJourneyService.continuationUnlocked(c,500),"skip finale waits for Proceed");
					String receipt="SELECT chosen_class,start_exp,quest_expands,completed_quests,chosen_at FROM poeta_journey WHERE player_id=500";
					String beforeReceipt=text(c,receipt);
					PoetaJourneyService.proceed(c,500,PlayerClass.GLADIATOR);
					check(PoetaJourneyService.continuationUnlocked(c,500)&&!PoetaJourneyService.welcomePending(c,500),"Proceed unlocks once and ends the finale");
					check(text(c,receipt).equals(beforeReceipt),"Proceed keeps the saved completion");
					PoetaJourneyService.proceed(c,500,PlayerClass.GLADIATOR);
					check(value(c,"SELECT COUNT(*) FROM poeta_journey_continuation WHERE player_id=500")==1&&!PoetaJourneyService.welcomePending(c,500),"repeated Proceed stays a single unlock");
					PoetaJourneyService.proceed(c,601,PlayerClass.GLADIATOR);
					check(PoetaJourneyService.continuationUnlocked(c,601)&&value(c,"SELECT COUNT(*) FROM poeta_journey WHERE player_id=601")==0,"character from before Journey unlocks without a journey row");
					player(c,602);PoetaJourneyService.update(c,"INSERT INTO poeta_journey(player_id,decision) VALUES(602,'PLAY')");c.commit();
					boolean closed=false;try { PoetaJourneyService.proceed(c,602,PlayerClass.WARRIOR); } catch(IllegalArgumentException e) { closed=true; }
					check(closed&&!PoetaJourneyService.continuationUnlocked(c,602),"unfinished story cannot Proceed");
					PoetaJourneyService.proceed(c,602,PlayerClass.GLADIATOR);
					check(PoetaJourneyService.continuationUnlocked(c,602)&&"PLAY".equals(text(c,"SELECT decision FROM poeta_journey WHERE player_id=602")),"played Ascension unlocks and keeps its decision");
					player(c,603);closed=false;try { PoetaJourneyService.proceed(c,603,PlayerClass.WARRIOR); } catch(IllegalArgumentException e) { closed=true; }
					check(closed&&!PoetaJourneyService.continuationUnlocked(c,603),"undecided starter cannot Proceed");
					check(value(c,saved)==before,"Proceed grants no mail, items, quests, titles or experience");
					PoetaJourneyService.update(c,"UPDATE poeta_journey SET welcome_pending=1 WHERE player_id=501");c.commit();
					try(var pool=java.util.concurrent.Executors.newFixedThreadPool(8)) {
						var clicks=new ArrayList<java.util.concurrent.Future<Void>>();
						for(int i=0;i<16;i++) { int click=i; clicks.add(pool.submit(() -> {
							try(Connection other=DriverManager.getConnection(url.replace("/"+source,"/"+schema),user,password)) {
								if(click%4==3) PoetaJourneyService.acknowledge(other,501); else PoetaJourneyService.proceed(other,501,PlayerClass.GLADIATOR);
							}
							return null;
						})); }
						for(var click:clicks) click.get();
					}
					check(value(c,"SELECT COUNT(*) FROM poeta_journey_continuation WHERE player_id=501")==1&&!PoetaJourneyService.welcomePending(c,501),"simultaneous Proceed clicks leave one unlock");
					for(String sql:journeySchema.split(";")) if(!sql.isBlank()) ddl.execute(sql);
					check(PoetaJourneyService.continuationUnlocked(c,500),"restart schema keeps unlocks");
					var empty=PlayerBotJourneyEvidence.read(c,9);
					check(empty.equals(new PlayerBotJourneyEvidence.Facts(0,0,0,0,0,0)),"new account has no evidence");
					check(doneCount(PoetaJourneyService.chapters(c,500,empty,true))==0,"no evidence completes no chapter");
					check(value(c,"SELECT COUNT(*) FROM poeta_journey_progress")==0,"reading progress records nothing unearned");
					var firstThree=PoetaJourneyRules.rewardChapters("midpoint",PoetaJourneyRules.chapters(true));
					boolean early=false;try { PoetaJourneyService.claim(c,500,"midpoint",101,firstThree); } catch(IllegalArgumentException e) { early=true; }
					check(early&&value(c,"SELECT COUNT(*) FROM player_titles WHERE player_id=500 AND title_id=101")==0,"unfinished chapters cannot claim");
					PoetaJourneyService.update(c,"INSERT INTO playerbot_ticket_receipts(receipt_key,account_id,kind,ticket_delta,credit_delta,tickets_after,credit_after) VALUES('run:a:9',9,'INSTANCE',2,0,2,0),('run:b:9',9,'INSTANCE',1,0,3,0),('recycle:9',9,'RECYCLE',0,33,3,33),('spend:9',9,'RECRUIT',-1,0,2,33),('run:c:8',8,'INSTANCE',3,0,3,0)");
					PoetaJourneyService.update(c,"INSERT INTO playerbot_recruitment_operations(account_id,request_key,state,advanced_class,auto_equip_rarity,combat_rank,character_id) VALUES(9,'a','COMPLETED','RANGER','GOOD','POOR',900),(9,'b','ROLLED','RANGER','GOOD','POOR',NULL),(8,'c','COMPLETED','RANGER','GOOD','POOR',901)");
					PoetaJourneyService.update(c,"INSERT INTO playerbot_metadata(player_id,account_id,section,revision,settings) VALUES(900,9,'loadouts',1,?),(901,8,'loadouts',1,?),(900,9,'quest-practice',1,?),(900,9,'growth',1,?),(902,9,'growth',1,?)",
						"{\"account\":\"9\",\"ref.0:10.TORSO\":\"7:110000001\"}","{\"account\":\"8\",\"ref.0:10.TORSO\":\"7:110000001\"}","{\"help\":\"n1:2:3,q4:5\"}",
						growth("GACHA","POOR","ORDINARY",0,900),growth("GACHA","POOR","POOR",0,902));
					c.commit();
					var facts=PlayerBotJourneyEvidence.read(c,9);
					check(facts.equals(new PlayerBotJourneyEvidence.Facts(3,1,1,2,2,1)),"evidence comes from each owner and this account only: "+facts);
					check(PoetaJourneyRules.chapters(true).stream().filter(chapter -> !chapter.done(facts)).map(PoetaJourneyRules.Chapter::id).toList().equals(List.of("fight")),"short run count leaves that chapter open");
					var midpoint=PoetaJourneyService.chapters(c,500,facts,true);c.commit();
					check(doneCount(midpoint)==5&&value(c,"SELECT COUNT(*) FROM poeta_journey_progress WHERE player_id=500 AND entry LIKE 'chapter:%'")==5,"play records five chapters");
					check(value(c,"SELECT COUNT(*) FROM poeta_journey_progress WHERE player_id=501")==0,"progress stays per character");
					check(Boolean.TRUE.equals(rewards(midpoint).get(0).get("ready"))&&Boolean.FALSE.equals(rewards(midpoint).get(1).get("ready")),"midpoint opens before final");
					long ticketsBefore=value(c,"SELECT COUNT(*) FROM playerbot_ticket_receipts"),goodsBefore=value(c,"SELECT COUNT(*) FROM mail")+value(c,"SELECT COUNT(*) FROM inventory");
					check(PoetaJourneyService.claim(c,500,"midpoint",101,firstThree),"midpoint title paid");
					check(!PoetaJourneyService.claim(c,500,"midpoint",101,firstThree),"repeat claim pays nothing");
					check(value(c,"SELECT COUNT(*) FROM player_titles WHERE player_id=500 AND title_id=101")==1,"title saved once");
					check(value(c,"SELECT COUNT(*) FROM playerbot_ticket_receipts")==ticketsBefore&&value(c,"SELECT COUNT(*) FROM mail")+value(c,"SELECT COUNT(*) FROM inventory")==goodsBefore,"claim pays no tickets, items or Kinah");
					boolean finalEarly=false;try { PoetaJourneyService.claim(c,500,"final",102,PoetaJourneyRules.chapters(true)); } catch(IllegalArgumentException e) { finalEarly=true; }
					check(finalEarly,"final title waits for every chapter");
					PoetaJourneyService.update(c,"DELETE FROM playerbot_recruitment_operations");PoetaJourneyService.update(c,"DELETE FROM playerbot_metadata");c.commit();
					check(doneCount(PoetaJourneyService.chapters(c,500,PlayerBotJourneyEvidence.read(c,9),true))==5,"losing a companion never undoes a chapter");c.commit();
					PoetaJourneyService.update(c,"INSERT INTO playerbot_ticket_receipts(receipt_key,account_id,kind,ticket_delta,credit_delta,tickets_after,credit_after) VALUES('run:d:9',9,'INSTANCE',1,0,3,33),('run:e:9',9,'INSTANCE',1,0,4,33),('run:f:9',9,'INSTANCE',1,0,5,33)");c.commit();
					check(doneCount(PoetaJourneyService.chapters(c,500,PlayerBotJourneyEvidence.read(c,9),true))==6,"more runs finish the last chapter");c.commit();
					try(var pool=java.util.concurrent.Executors.newFixedThreadPool(8)) {
						var claims=new ArrayList<java.util.concurrent.Future<Boolean>>();
						for(int i=0;i<16;i++)claims.add(pool.submit(() -> {
							try(Connection other=DriverManager.getConnection(url.replace("/"+source,"/"+schema),user,password)) { return PoetaJourneyService.claim(other,500,"final",102,PoetaJourneyRules.chapters(true)); }
						}));
						int paid=0;for(var claim:claims)if(claim.get())paid++;
						check(paid==1&&value(c,"SELECT COUNT(*) FROM player_titles WHERE player_id=500 AND title_id=102")==1,"simultaneous claims pay once");
					}
					boolean locked=false;try { PoetaJourneyService.claim(c,603,"midpoint",101,firstThree); } catch(IllegalArgumentException e) { locked=true; }
					check(locked,"no claim before Proceed");
					PoetaJourneyService.update(c,"DELETE FROM players WHERE id=601");c.commit();
					check(value(c,"SELECT COUNT(*) FROM poeta_journey_continuation WHERE player_id=601")==0,"deleting a character removes its unlock");
					PoetaJourneyService.update(c,"INSERT INTO poeta_journey_progress(player_id,entry) VALUES(602,'chapter:earn')");PoetaJourneyService.update(c,"DELETE FROM players WHERE id=602");c.commit();
					check(value(c,"SELECT COUNT(*) FROM poeta_journey_progress WHERE player_id=602")==0,"deleting a character removes its chapters");
				}
			} finally { s.execute("DROP DATABASE `"+schema+"`"); }
		}
		System.out.println("PASS: "+checks+" isolated database checks: atomic rewards/completion, all-or-nothing rollback, duplicate clicks, mailbox capacity, crash recovery, Proceed, chapters and title claims.");
	}
}
