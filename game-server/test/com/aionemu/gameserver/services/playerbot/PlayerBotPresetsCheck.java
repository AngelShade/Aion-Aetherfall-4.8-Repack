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
  var path=Files.createTempDirectory(Path.of("target/playerbots-validation"),"party-presets-");var store=new PlayerBotPresets.Store(path);
  try {
   check(store.load(1).presets().isEmpty(),"Fresh account is empty");
   var alt=new PlayerBotPresets.Member(12,"OwnedAlt",false,Role.HEALER,Order.STAY);var temporary=new PlayerBotPresets.Member(13,"TempTank",true,Role.TANK,Order.FOLLOW);
   var saved=store.saveParty(1,"Dungeon Team",List.of(alt,temporary));var reread=new PlayerBotPresets.Store(path).load(1);
   check(reread.presets().equals(List.of(saved)),"Mixed party/roles/orders survive process-like reopen");check(reread.saved().equals(Set.of(13)),"Only Temporary Bot marked saved; alt never converted");
   check(store.load(2).presets().isEmpty(),"Account separation");
   var updated=store.saveParty(1,"dungeon team",List.of(temporary,alt));check(updated.id().equals(saved.id()) && store.load(1).presets().size()==1,"Named update retains stable preset ID");
   store.saveBot(1,14);store.saveBot(1,14);check(store.load(1).saved().equals(Set.of(13,14)),"Saving existing bot never duplicates characters");
   for(int i=1;i<20;i++)store.saveParty(1,"Party "+i,List.of(temporary));
   try{store.saveParty(1,"Too many",List.of(temporary));throw new AssertionError("Limit bypass");}catch(IllegalArgumentException expected){count++;}
   var before=Files.readString(path.resolve("account-1.json"));try{store.saveParty(1,"",List.of(temporary));throw new AssertionError("Empty name allowed");}catch(IllegalArgumentException expected){count++;}
   check(Files.readString(path.resolve("account-1.json")).equals(before),"Invalid write retains original file");
   rejects(()->new PlayerBotPresets.Preset(saved.id(),"Duplicates",List.of(alt,alt)),"Duplicate character must not be spawned twice");
   rejects(()->new PlayerBotPresets.Preset(saved.id(),"Empty",List.of()),"Empty preset");
   rejects(()->new PlayerBotPresets.Preset(saved.id(),"Big",List.of(alt,temporary,alt,temporary,alt,temporary)),"Too many slots");
   PlayerBotPresets.checkRoom(2,3,3,5,97,100);count++;
   rejects(()->PlayerBotPresets.checkRoom(3,3,4,5,0,100),"Owner limit");
   rejects(()->PlayerBotPresets.checkRoom(1,3,4,5,0,100),"Keep human party members");
   rejects(()->PlayerBotPresets.checkRoom(1,1,2,5,100,100),"Global limit");
   store.remove(1,saved.id());check(store.load(1).saved().equals(Set.of(13,14)),"Removing preset keeps saved bots");
   try(var executor=Executors.newFixedThreadPool(4)){var futures=new ArrayList<Future<?>>();for(int i=20;i<40;i++){int id=i;futures.add(executor.submit(()->{try{store.saveBot(2,id);}catch(Exception e){throw new RuntimeException(e);}}));}for(var future:futures)future.get(10,TimeUnit.SECONDS);}
   check(store.load(2).saved().size()==20,"Concurrent saves retain all changes");
   Files.copy(path.resolve("account-1.json"),path.resolve("account-3.json"));try{store.load(3);throw new AssertionError("Cross-account file accepted");}catch(java.io.IOException expected){count++;}
   Files.writeString(path.resolve("account-4.json"),"{bad");try{store.load(4);throw new AssertionError("Corrupt file accepted");}catch(java.io.IOException expected){count++;}
   check(Files.readString(path.resolve("account-4.json")).equals("{bad"),"Corrupt file never reset silently");
   check(Files.list(path).noneMatch(p->p.getFileName().toString().endsWith(".tmp")),"Atomic writes clean temporary files");
   System.out.println("OK: "+count+" saved-bot/mixed-preset persistence, ownership, validation, limits and concurrent-write checks");
  }finally{try(var files=Files.list(path)){for(var file:files.toList())Files.delete(file);}Files.delete(path);}
 }
}
