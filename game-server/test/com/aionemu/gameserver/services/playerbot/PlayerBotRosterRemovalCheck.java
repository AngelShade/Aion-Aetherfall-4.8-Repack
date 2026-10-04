package com.aionemu.gameserver.services.playerbot;
import java.nio.file.*;
import java.util.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Isolated archive persistence and identity/preset checks; no native IDs or DB writes. */
public final class PlayerBotRosterRemovalCheck {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] ignored)throws Exception {
  var directory=Files.createTempDirectory(Path.of("target/playerbots-validation"),"roster-removal-");
  try {
   var bot=new PlayerBotRoster.Entry(13,"TempTank",true);var pending=new PlayerBotRoster.Entry(14,null,false);var entries=List.of(bot,pending);
   check(PlayerBotRosterRemoval.owned(entries,13)==bot,"Dedicated owned roster authorizes exact ID");
   try{PlayerBotRosterRemoval.owned(entries,12);throw new AssertionError("Alt removal allowed");}catch(IllegalArgumentException expected){checks++;}
   check(PlayerBotRosterRemoval.visible(directory,1,entries).equals(entries),"No archive preserves pending and ready entries");
   var alt=new PlayerBotPresets.Member(12,"OwnedAlt",false,Role.HEALER,Order.STAY);var temp=new PlayerBotPresets.Member(13,"TempTank",true,Role.TANK,Order.FOLLOW);
   var mixed=new PlayerBotPresets.Preset("1".repeat(32),"Mixed",List.of(alt,temp));var only=new PlayerBotPresets.Preset("2".repeat(32),"Temporary",List.of(temp));
   var doc=new PlayerBotPresets.Document(1,Set.of(13,14),List.of(mixed,only));
   PlayerBotRosterRemoval.archive(directory,1,13,"TempTank");
   String marker=Files.readString(directory.resolve("character-13.json"));PlayerBotRosterRemoval.archive(directory,1,13,"TempTank");
   check(Files.readString(directory.resolve("character-13.json")).equals(marker),"Repeat archive is idempotent");
   check(PlayerBotRosterRemoval.visible(directory,1,entries).equals(List.of(pending)),"Archived bot absent after fresh read");
   var clean=PlayerBotRosterRemoval.prune(directory,doc);
   check(clean.saved().equals(Set.of(14)),"Saved checkpoint reference removed, other pending bot kept");
   check(clean.presets().size()==1 && clean.presets().getFirst().members().equals(List.of(alt)),"Mixed preset retains exact owned-alt role/order; empty preset removed");
   check(clean.presets().getFirst().id().equals(mixed.id()),"Mixed preset stable identity retained");
   check(PlayerBotRosterRemoval.prune(directory,doc).equals(clean),"Stale file after interrupted cleanup cannot revive archived bot");
   try{PlayerBotRosterRemoval.removed(directory,2,13);throw new AssertionError("Cross-account archive accepted");}catch(java.io.IOException expected){checks++;}
   PlayerBotRosterRemoval.archive(directory,1,14,"Pending companion 14");
   check(PlayerBotRosterRemoval.visible(directory,1,entries).isEmpty(),"Pending creation may be archived without deleting persisted IDs");
   Files.writeString(directory.resolve("character-13.json"),"{bad");
   try{PlayerBotRosterRemoval.visible(directory,1,entries);throw new AssertionError("Corrupt record exposed bot");}catch(java.io.IOException expected){checks++;}
   check(Files.readString(directory.resolve("character-13.json")).equals("{bad"),"Corrupt marker retained, never reset");
   try(var files=Files.list(directory)){check(files.noneMatch(p->p.toString().endsWith(".tmp")),"Atomic archive writes leave no partial files");}
   System.out.println("OK: "+checks+" roster archive, pending creation, ownership, restart, mixed-preset and interrupted-write checks; no IDs released");
  }finally{try(var files=Files.list(directory)){for(var file:files.toList())Files.delete(file);}Files.delete(directory);}
 }
}
