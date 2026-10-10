package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.configs.main.NameConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.Util;
import com.aionemu.gameserver.world.container.PlayerContainer;
import com.aionemu.gameserver.world.exceptions.DuplicateAionObjectException;

/** Actual central player index on inert actors; no World, IDs, SQL or native casts. */
public final class PlayerBotNameLookupCheck {
 static Unsafe unsafe;static int checks;
 static class Actor extends Player {
  int id;String name;Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public String getName(boolean tag){return name;}
  @Override public String toString(){return "Offline name fixture "+id;}
 }
 static Actor actor(int id,String name)throws Exception {
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.id=id;a.name=name;return a;
 }
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void rejects(Runnable task,String why){boolean refused=false;try{task.run();}catch(DuplicateAionObjectException expected){refused=true;}check(refused,why);}
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  boolean custom=NameConfig.ALLOW_CUSTOM_NAMES;Locale locale=Locale.getDefault();
  try {
   NameConfig.ALLOW_CUSTOM_NAMES=false;var index=new PlayerContainer();int id=1999999021;
   for(String name:new String[]{"LeMuse","MagicDps","Healerul","MixedHuman"}) {
    var a=actor(id++,name);index.add(a);
    for(String query:new String[]{name,name.toLowerCase(Locale.ROOT),name.toUpperCase(Locale.ROOT),Util.convertName(name)})
     check(index.get(query)==a,"All central name consumers resolve exact/lower/upper/native packet spelling: "+name);
    check(index.get(a.id)==a,"Object-ID lookup preserves identity");
    check(a.getName().equals(name),"Display/persisted name is never rewritten by indexing");
    index.remove(a);check(index.get(name)==null && index.get(a.id)==null,"Removal clears both indexes");
   }
   check(index.get((String)null)==null && index.get("")==null && index.get("missing")==null,"Absent names fail closed");
   var first=actor(id++,"LeMuse");index.add(first);var collision=actor(id++,"LEMUSE");
   rejects(()->index.add(collision),"Case collision cannot replace an existing character");
   check(index.get("lemuse")==first && index.get(collision.id)==null,"Rejected name collision leaves no partial ID registration");
   var sameId=actor(first.id,"OtherName");rejects(()->index.add(sameId),"Duplicate object ID cannot replace original");
   check(index.get(first.id)==first && index.get("OtherName")==null,"Rejected ID collision leaves original indexes intact");
   index.remove(sameId);check(index.get("LEMUSE")==first,"Stale removal cannot erase the current actor");
   first.name="LEMUSE";index.updateCachedPlayerName("LeMuse",first);
   check(index.get("lemuse")==first && index.get("LEMUSE")==first,"Case-only rename retains its index");
   first.name="NewName";index.updateCachedPlayerName("LEMUSE",first);
   check(index.get("lemuse")==null && index.get("NEWNAME")==first,"Real rename retires old alias and publishes new name");
   var occupied=actor(id++,"TakenName");index.add(occupied);first.name="TAKENNAME";
   rejects(()->index.updateCachedPlayerName("NewName",first),"Conflicting rename never steals an occupied name");
   check(index.get("takenname")==occupied && index.get("newname")==first,"Failed rename retains both original indexed identities");
   index.remove(first);check(index.get("newname")==null && index.get("takenname")==occupied,"Removal uses registered key even after a rejected rename");
   first.name="Ghost";index.updateCachedPlayerName("NewName",first);
   check(index.get("Ghost")==null && index.get(first.id)==null,"Late rename cannot re-register a logged-out actor");
   var replacement=actor(first.id,"Replacement");index.add(replacement);first.name="TakenName";
   index.updateCachedPlayerName("NewName",first);index.remove(first);
   check(index.get("replacement")==replacement && index.get("takenname")==occupied,"Stale rename/removal cannot corrupt replacement identities");
   Locale.setDefault(Locale.forLanguageTag("tr-TR"));var turkish=actor(id++,"ILION");index.add(turkish);
   check(index.get("ilion")==turkish,"Name identity does not depend on Windows language");
   check(index.get("IliOn")==turkish,"Mixed-case queries use stable locale");
   check(index.get(Util.convertName("ILION"))==turkish,"Native packet normalization uses the same locale as the index");
   check(Util.convertName("MAGICDPS").equals("Magicdps"),"Name formatting never substitutes a language-specific dotless letter");
   NameConfig.ALLOW_CUSTOM_NAMES=true;
   check(Util.convertName("LeMuse").equals("LeMuse"),"Custom display-name policy remains unchanged");
   check(index.get("TAKENNAME")==occupied,"The name index also handles custom-name mode consistently");
   var race=new PlayerContainer();var left=actor(id++,"RaceName");var right=actor(id++,"RACENAME");
   var start=new CountDownLatch(1);var results=new ConcurrentLinkedQueue<Actor>();var errors=new ConcurrentLinkedQueue<Throwable>();
   var threads=new ArrayList<Thread>();
   for(var a:List.of(left,right))threads.add(new Thread(()->{try{start.await();race.add(a);results.add(a);}catch(DuplicateAionObjectException expected){}catch(Throwable error){errors.add(error);}}));
   threads.forEach(Thread::start);start.countDown();for(var t:threads)t.join();
   check(errors.isEmpty() && results.size()==1,"Concurrent case-equivalent admissions have exactly one winner");
   var winner=results.iterator().next();var loser=winner==left?right:left;
   check(race.get("racename")==winner && race.get(winner.id)==winner && race.get(loser.id)==null,"Concurrent collision cannot leave split indexes");
   race.remove(loser);check(race.get("RACENAME")==winner,"Losing actor removal cannot erase winner");
   check(race.getAllPlayers().size()==1,"Population enumeration excludes failed registration");
   race.remove(winner);check(race.get("RaceName")==null && race.getAllPlayers().isEmpty(),"Winner removal clears central index");
   check(replacement.name.equals("Replacement") && occupied.name.equals("TakenName"),"Human/bot names and native IDs remain unchanged");
   System.out.println("OK: "+checks+" central player-name lookup/packet case/rename/collision/lifecycle checks; no World/DB/IDs/casts");
  } finally {NameConfig.ALLOW_CUSTOM_NAMES=custom;Locale.setDefault(locale);}
 }
}
