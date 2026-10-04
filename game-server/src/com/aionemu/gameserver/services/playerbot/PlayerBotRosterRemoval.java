package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.player.PlayerLeaveWorldService;
import com.aionemu.gameserver.world.World;

/** Removal is an archive, never a character-slot conversion or an ID release.
 * Native rows, item custody and reserved IDs remain intact, including pending creations.
 */
public final class PlayerBotRosterRemoval {
 private static final Path DIRECTORY=Path.of("config/playerbots/removed");
 private static Path path(Path directory,int id){if(id<=0)throw new IllegalArgumentException("Invalid Temporary Bot.");return directory.resolve("character-"+id+".json");}
 static boolean removed(Path directory,int account,int id)throws IOException {
  Path file=path(directory,id);
  long size;try{size=Files.size(file);}catch(NoSuchFileException e){return false;}
  try {
   if(size>4096)throw new IOException("Invalid archived companion record.");
   var json=JSON.parseObject(Files.readString(file));
   if(json.getIntValue("version")!=1 || json.getIntValue("account")!=account || json.getIntValue("id")!=id)throw new IOException("Archived companion ownership mismatch.");
   return true;
  }catch(RuntimeException e){throw new IOException("Cannot verify archived companion; its record was preserved.",e);}
 }
 static void archive(Path directory,int account,int id,String name)throws IOException {
  if(account<=0)throw new IllegalArgumentException("Invalid account.");
  if(removed(directory,account,id))return;
  Files.createDirectories(directory);Path temporary=Files.createTempFile(directory,"archive-",".tmp");
  try {
   Files.writeString(temporary,JSON.toJSONString(Map.of("version",1,"account",account,"id",id,"name",name,"removedAt",System.currentTimeMillis())));
   try{Files.move(temporary,path(directory,id),StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,path(directory,id));}
  }finally{Files.deleteIfExists(temporary);}
 }
 static List<PlayerBotRoster.Entry> visible(Path directory,int account,List<PlayerBotRoster.Entry> entries)throws IOException {
  var visible=new ArrayList<PlayerBotRoster.Entry>();for(var entry:entries)if(!removed(directory,account,entry.id()))visible.add(entry);return List.copyOf(visible);
 }
 static List<PlayerBotRoster.Entry> visible(int account,List<PlayerBotRoster.Entry> entries){
  try{return visible(DIRECTORY,account,entries);}catch(IOException e){throw new IllegalStateException("Cannot verify removed Temporary Bots",e);}
 }
 static PlayerBotPresets.Document prune(Path directory,PlayerBotPresets.Document document)throws IOException {
  var saved=new HashSet<Integer>();for(int id:document.saved())if(!removed(directory,document.account(),id))saved.add(id);
  var presets=new ArrayList<PlayerBotPresets.Preset>();
  for(var preset:document.presets()) {
   var members=new ArrayList<PlayerBotPresets.Member>();for(var member:preset.members())if(!member.temporary() || !removed(directory,document.account(),member.id()))members.add(member);
   if(!members.isEmpty())presets.add(new PlayerBotPresets.Preset(preset.id(),preset.name(),members));
  }
  return new PlayerBotPresets.Document(document.account(),saved,presets);
 }
 static PlayerBotPresets.Document prune(PlayerBotPresets.Document document)throws IOException{return prune(DIRECTORY,document);}
 static PlayerBotRoster.Entry owned(List<PlayerBotRoster.Entry> entries,int id){return entries.stream().filter(e->e.id()==id).findFirst().orElseThrow(()->new IllegalArgumentException("Choose a Temporary Bot from your own roster. Account characters cannot be removed here."));}
 static String remove(Player owner,int id,PlayerBotPresets.Store store) {
  var service=PlayerBotService.getInstance();synchronized(service) {
   int account=owner.getAccount().getId();var entry=owned(PlayerBotRoster.list(account),id);
   if(id==owner.getObjectId())throw new IllegalArgumentException("Your current character cannot be removed.");
   var active=service.companions(owner).stream().filter(s->s.bot().getObjectId()==id).findFirst().orElse(null);
   if(active!=null)service.dismiss(owner,active.bot().getName());
   try(var lease=PlayerBotLease.acquire(id)) {
    if(lease==null || World.getInstance().isInWorld(id) || PlayerLeaveWorldService.isLeavingWorld(id))throw new IllegalArgumentException("This Temporary Bot is active or waiting for a save retry. Remove it after the save succeeds.");
    synchronized(store) {
     var original=store.load(account);String name=entry.name()==null ? "Pending companion "+id : entry.name();
     archive(DIRECTORY,account,id,name);
     // The marker is authoritative after a crash between file writes; load() also
     // prunes archived IDs, so an old preset cannot resurrect a removed companion.
     try{store.write(prune(original));}catch(IOException e){org.slf4j.LoggerFactory.getLogger(PlayerBotRosterRemoval.class).warn("Archived companion {} removed; saved-party file cleanup deferred",id,e);}
     return name+" removed from your roster and saved parties. Archived progress is kept.";
    }
   }catch(IOException e){throw new IllegalArgumentException("Could not archive this Temporary Bot: "+e.getMessage(),e);}
  }
 }
 private PlayerBotRosterRemoval(){}
}
