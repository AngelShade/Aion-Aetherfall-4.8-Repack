package com.aionemu.gameserver.services.playerbot;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Actual atomic file persistence and mixed party identities; no game/DB changes. */
public final class PlayerBotPresetsCheck {
 private static int count;
 private static void check(boolean value,String text){count++;if(!value)throw new AssertionError(text);}
 private static void rejects(Runnable action,String text){try{action.run();throw new AssertionError(text);}catch(IllegalArgumentException expected){count++;}}
 public static void main(String[] ignored)throws Exception {
  // Saved parties are one document per account in playerbot_saved_parties. The folder is where the old JSON files
  // lived; the store still takes it, and nothing is written there.
  var path=Path.of("target/playerbots-validation");var store=new PlayerBotPresets.Store(path);
  try(var table=PlayerBotMemoryMetadata.install().owner(12,1).temporary(13,1).temporary(14,1)) {
   for(int id=20;id<40;id++)table.temporary(id,2);
   check(store.load(1).presets().isEmpty(),"Fresh account is empty");
   var alt=new PlayerBotPresets.Member(12,"OwnedAlt",false,Role.HEALER,Order.STAY);var temporary=new PlayerBotPresets.Member(13,"TempTank",true,Role.TANK,Order.FOLLOW);
   var saved=store.saveParty(1,"Dungeon Team",List.of(alt,temporary));var reread=new PlayerBotPresets.Store(path).load(1);
   check(reread.presets().equals(List.of(saved)),"Mixed party/roles/orders survive process-like reopen");check(reread.saved().equals(Set.of(13)),"Only Temporary Bot marked saved; alt never converted");
   check(store.load(2).presets().isEmpty(),"Account separation");
   var updated=store.saveParty(1,"dungeon team",List.of(temporary,alt));check(updated.id().equals(saved.id()) && store.load(1).presets().size()==1,"Named update retains stable preset ID");
   store.saveBot(1,14);store.saveBot(1,14);check(store.load(1).saved().equals(Set.of(13,14)),"Saving existing bot never duplicates characters");
   for(int i=1;i<20;i++)store.saveParty(1,"Party "+i,List.of(temporary));
   try{store.saveParty(1,"Too many",List.of(temporary));throw new AssertionError("Limit bypass");}catch(IllegalArgumentException expected){count++;}
   var before=table.parties.get(1);try{store.saveParty(1,"",List.of(temporary));throw new AssertionError("Empty name allowed");}catch(IllegalArgumentException expected){count++;}
   check(table.parties.get(1).equals(before),"Invalid write retains the stored document");
   rejects(()->new PlayerBotPresets.Preset(saved.id(),"Duplicates",List.of(alt,alt)),"Duplicate character must not be spawned twice");
   rejects(()->new PlayerBotPresets.Preset(saved.id(),"Empty",List.of()),"Empty preset");
   rejects(()->new PlayerBotPresets.Preset(saved.id(),"Big",List.of(alt,temporary,alt,temporary,alt,temporary)),"Too many slots");
   PlayerBotPresets.checkRoom(2,3,3,5,97,100);count++;
   rejects(()->PlayerBotPresets.checkRoom(3,3,4,5,0,100),"Owner limit");
   rejects(()->PlayerBotPresets.checkRoom(1,3,4,5,0,100),"Keep human party members");
   rejects(()->PlayerBotPresets.checkRoom(1,1,2,5,100,100),"Global limit");
   store.remove(1,saved.id());check(store.load(1).saved().equals(Set.of(13,14)),"Removing preset keeps saved bots");
   // The native roster decides what a member is: an account character cannot be saved as a Temporary Bot.
   before=table.parties.get(1);
   try{store.saveParty(1,"Wrong kind",List.of(new PlayerBotPresets.Member(12,"OwnedAlt",true,Role.HEALER,Order.STAY)));throw new AssertionError("Account character saved as a Temporary Bot");}catch(java.io.IOException expected){count++;}
   check(table.parties.get(1).equals(before),"A refused member kind leaves the stored document alone");
   try(var executor=Executors.newFixedThreadPool(4)){var futures=new ArrayList<Future<?>>();for(int i=20;i<40;i++){int id=i;futures.add(executor.submit(()->{try{store.saveBot(2,id);}catch(Exception e){throw new RuntimeException(e);}}));}for(var future:futures)future.get(10,TimeUnit.SECONDS);}
   check(store.load(2).saved().size()==20,"Concurrent saves retain all changes");
   table.owner(3,3);table.parties.put(3,table.parties.get(1));try{store.load(3);throw new AssertionError("Cross-account document accepted");}catch(java.io.IOException expected){count++;}
   table.owner(4,4);table.parties.put(4,"{bad");try{store.load(4);throw new AssertionError("Corrupt document accepted");}catch(java.io.IOException expected){count++;}
   check(table.parties.get(4).equals("{bad"),"Corrupt document never reset silently");
   check(!Files.exists(path.resolve("account-1.json")) && !Files.exists(path.resolve("account-2.json")),"No preset file is written any more");
   System.out.println("OK: "+count+" saved-bot/mixed-preset persistence, ownership, validation, limits and concurrent-write checks");
  }
 }
}
