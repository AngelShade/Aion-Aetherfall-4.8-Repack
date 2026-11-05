package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.craft.Profession;

/** Pure trade policy; limits never grant money, materials or proficiency. */
final class PlayerBotProfessionPolicy {
 record Style(String name,int weight,long limit,long reserve,long lot) {}
 static Map<Integer,Integer> weights(String text) {
  var out=new LinkedHashMap<Integer,Integer>();
  for(String part:text.split(",")){var p=part.trim().split(":");if(p.length!=2)throw new IllegalArgumentException("Invalid profession weights");
   int id=Integer.parseInt(p[0]),weight=Integer.parseInt(p[1]);if(Profession.getBySkillId(id)==null || weight<=0 || out.put(id,weight)!=null)throw new IllegalArgumentException("Invalid native profession weight");}
  if(out.size()!=Profession.values().length)throw new IllegalArgumentException("Every native trade needs a weight");return Collections.unmodifiableMap(out);
 }
 static List<Style> styles(String text,boolean gathering) {
  var out=new ArrayList<Style>();var names=new HashSet<String>();
  for(String part:text.split(",")){var p=part.trim().split(":");if(p.length!=(gathering?5:2))throw new IllegalArgumentException("Invalid profession styles");
   String name=p[0];int weight=Integer.parseInt(p[1]);long limit=gathering?Long.parseLong(p[2]):0,reserve=gathering?Long.parseLong(p[3]):0,lot=gathering?Long.parseLong(p[4]):0;
   if(!name.matches("[a-z_]{1,24}") || !names.add(name) || weight<=0 || gathering && (limit<1 || reserve<0 || reserve>=limit || lot<1 || lot>limit))throw new IllegalArgumentException("Invalid trade style bounds");
   out.add(new Style(name,weight,limit,reserve,lot));
  }
  if(out.isEmpty())throw new IllegalArgumentException("No trade styles");return List.copyOf(out);
 }
 static Style style(List<Style> styles,long seed){long total=styles.stream().mapToLong(Style::weight).sum(),pick=Math.floorMod(mix(seed),total);for(var style:styles){if(pick<style.weight())return style;pick-=style.weight();}throw new AssertionError();}
 static long mix(long value){value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;return value^(value>>>31);}
 static int choose(Map<Integer,Integer> weights,Map<Integer,Integer> counts,int character) {
  long total=1L+counts.values().stream().mapToLong(Integer::longValue).sum(),sum=weights.values().stream().mapToLong(Integer::longValue).sum();int best=0;long score=Long.MIN_VALUE,tie=0;
  for(var e:weights.entrySet()){long deficit=total*e.getValue()-sum*counts.getOrDefault(e.getKey(),0),hash=mix((long)character<<32|e.getKey());
   if(deficit>score || deficit==score && Long.compareUnsigned(hash,tie)>0){score=deficit;tie=hash;best=e.getKey();}}
  return best;
 }
 static long allowance(long money,long reserve,long daily,long spent,int percent){return PlayerBotMarket.budget(money,reserve,percent,Math.max(0,daily-spent));}
 static long harvest(long held,long limit,long nativeCount){return held>=0 && nativeCount>0 && nativeCount<=limit-held?nativeCount:0;}
 static long sale(long held,long reserve,long lot,long daily,long offered){return Math.max(0,Math.min(Math.min(held-reserve,lot),daily-offered));}
 static boolean profit(long cost,long gross,int success,int margin){if(cost<0 || gross<=0 || success<1 || success>100)return false;
  try{return Math.multiplyExact(gross,6500)/10000>=Math.floorDiv(Math.addExact(Math.multiplyExact(cost,100L+margin),success-1),success);}catch(ArithmeticException overflow){return false;}}
 static final int BAG_ROOM=5;
 /** Cube slots a full reset leaves free so the factory can always equip the rebuilt character. */
 static final int FACTORY_ROOM=16;
 static boolean zeroXpStop(int zeroXp,int zeroAt,int points,int limit){return zeroAt==points && zeroXp>=limit;}
 /** Practice only counts if it earns XP, stays under the hourly attempts and fits what is left of the daily loss budget. */
 static boolean practice(int points,int required,long attempts,int perHour,long cost,long lossLeft){return points>=required && points-required<=40 && attempts<perHour && cost<=Math.max(0,lossLeft);}
 /** The market gets first chance at a crafter's inputs. After the wait the bot may fetch them itself, whether or not an order was ever posted. */
 static boolean fallback(long marketWait,long now){return marketWait>0 && now>=marketWait;}
 /** Gathering attempts earned since the last turn: one per interval, the rate a player manages including travel between nodes. */
 static long rolls(long elapsed,long interval,int batch){return Math.min(batch,elapsed/Math.max(1,interval));}
 /** Lowest unit price that returns a unit's cost after the market keeps its share. */
 static long floor(long cost,long held){if(cost<=0 || held<=0)return 0;long unit=Math.max(1,cost/held);return Math.floorDiv(unit*10000+6499,6500);}
 static long costAfterOffer(long cost,long held,long quantity){if(cost<=0 || held<=0)return 0;return Math.max(0,cost-Math.min(cost,cost/held*quantity));}
 private PlayerBotProfessionPolicy(){}
}
