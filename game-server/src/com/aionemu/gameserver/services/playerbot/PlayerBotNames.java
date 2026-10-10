/* RandomPlayerbotFactory::CreateRandomBotName, mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c, GPL-2.0-or-later.
 * Aion adaptation: code-generated pronounceable names and native name reservations.
 * See third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import java.util.function.Predicate;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.configs.main.NameConfig;
import com.aionemu.gameserver.model.Gender;
import com.aionemu.gameserver.services.NameRestrictionService;

/** Names are persisted in native players rows. No ID suffix or runtime name database. */
public final class PlayerBotNames {
 private static final String[] START=("al bel cal cel cor dar del dor el er fal fen gal gar hal har il ir jar jel kael kel "
  +"lar le len lor mal mel mer mir nal nel nor ol or pal per ral rel ren rol sal sel ser tal tel ter val vel ver vin zar zel").split(" ");
 private static final String[] MIDDLE="a e i o u ae ai ea ia io ara eri ori ala eli ira ora ava eve ina ona ula ena ani".split(" ");
 private static final String[] MALE="den dor din len lin lon ren rin ron ric rik ris vin ven van mir mar mer thas ther thin thor del dan der don ran ril rel val var vel nor nar nel sen sar sil son las lor".split(" ");
 private static final String[] FEMALE="na ne nia la lia ra ria sa sia tha thia va via elle ella wen wyn lyn lise nora nira lena lina lora mira mara mela rena rina rona sera sara sela fina fira fela dara dela dina dora".split(" ");
 public static final int ATTEMPTS=512;

 public static String candidate(int id,Gender gender,int attempt) {
  if(id<=0 || gender==null || attempt<0 || attempt>=ATTEMPTS)throw new IllegalArgumentException("Invalid name seed");
  var random=new SplittableRandom(((long)id<<32)^((long)attempt*0x9E3779B97F4A7C15L)^gender.ordinal());
  var ends=gender==Gender.MALE?MALE:FEMALE;
  String name=START[random.nextInt(START.length)]+MIDDLE[random.nextInt(MIDDLE.length)]+ends[random.nextInt(ends.length)];
  return Character.toUpperCase(name.charAt(0))+name.substring(1);
 }

 public static String choose(int id,Gender gender,Predicate<String> unavailable) {
  Objects.requireNonNull(unavailable);
  for(int attempt=0;attempt<ATTEMPTS;attempt++) {
   String name=candidate(id,gender,attempt);
   if(NameRestrictionService.isValidName(name) && !NameRestrictionService.isForbidden(name) && !unavailable.test(name))return name;
  }
  throw new IllegalStateException("No available valid generated character name; creation refused");
 }

 static String available(int id,Gender gender) {
  // Fail closed on SQL errors. Native character creation still enforces its unique name key.
  try(var c=DatabaseFactory.getConnection()) {
   return choose(id,gender,name->{try{return unavailable(c,name);}catch(SQLException e){throw new IllegalStateException("Cannot verify generated name availability",e);}});
  }catch(SQLException e){throw new IllegalStateException("Cannot open native character name repository",e);}
 }

 public static boolean unavailable(Connection c,String name)throws SQLException {
  try(var s=c.prepareStatement("SELECT 1 FROM players WHERE name=? UNION ALL SELECT 1 FROM old_names WHERE old_name=? AND renamed_date > NOW() - INTERVAL ? DAY AND ? > 0 LIMIT 1")) {
   s.setString(1,name);s.setString(2,name);s.setInt(3,NameConfig.RESERVE_OLD_NAME_DAYS);s.setInt(4,NameConfig.RESERVE_OLD_NAME_DAYS);
   try(var r=s.executeQuery()){return r.next();}
  }
 }

 /** Exact former factory output, for the one-time guarded migration only. */
 public static String legacy(int id) {
  if(id<=0)throw new IllegalArgumentException("Invalid character ID");
  StringBuilder suffix=new StringBuilder();long value=Integer.toUnsignedLong(id);
  do{suffix.append((char)('a'+value%26));value/=26;}while(value>0);
  return "Wander"+suffix.reverse();
 }
 public static boolean legacyWorld(int id,int account,String name,Integer poolAccount,Integer rosterAccount,boolean ready) {
  return id>0 && PlayerBotPopulationPolicy.account(account) && poolAccount!=null && rosterAccount!=null
   && account==poolAccount && account==rosterAccount && ready && legacy(id).equals(name);
 }

 /** Caller owns the offline transaction; never updates character progress or native identity. */
 public static long rename(Connection c,int id,int account,String oldName,String newName)throws SQLException {
  if(c.getAutoCommit() || !legacy(id).equals(oldName) || !PlayerBotPopulationPolicy.account(account)
   || !NameRestrictionService.isValidName(newName) || NameRestrictionService.isForbidden(newName) || unavailable(c,newName))
   throw new SQLException("Invalid or unavailable offline bot rename");
  try(var s=c.prepareStatement("UPDATE players p SET name=? WHERE p.id=? AND p.account_id=? AND p.name=? "
   +"AND EXISTS (SELECT 1 FROM playerbot_roster r WHERE r.player_id=p.id AND r.account_id=p.account_id AND r.ready=TRUE) "
   +"AND EXISTS (SELECT 1 FROM playerbot_population_members m JOIN playerbot_population_accounts a ON a.account_id=m.account_id WHERE m.player_id=p.id AND m.account_id=p.account_id)")) {
   s.setString(1,newName);s.setInt(2,id);s.setInt(3,account);s.setString(4,oldName);
   if(s.executeUpdate()!=1)throw new SQLException("Offline bot rename owner/name/readiness changed");
  }
  try(var s=c.prepareStatement("INSERT INTO old_names (player_id,old_name,new_name) VALUES (?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
   s.setInt(1,id);s.setString(2,oldName);s.setString(3,newName);
   if(s.executeUpdate()!=1)throw new SQLException("Native name history insert refused");
   try(var r=s.getGeneratedKeys()){if(!r.next() || r.getLong(1)<=0)throw new SQLException("Native name history identity missing");return r.getLong(1);}
  }
 }
 private PlayerBotNames(){}
}
