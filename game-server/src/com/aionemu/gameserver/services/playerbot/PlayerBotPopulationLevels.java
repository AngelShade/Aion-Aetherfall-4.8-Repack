/* RandomBotLevelMgr::{ClampAndBalanceBrackets,ApplyBracketWeights,GetOrFlagPlayerBracket,
 * ProcessFactionDistribution,RedistributeSurplusBots,ComputeResetChance}, mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.random.RandomGenerator;

/** Pure bracket arithmetic. Character execution and human custody stay with native owners. */
final class PlayerBotPopulationLevels {
 record Band(int lower,int upper,int percent) {
  boolean valid(){return lower<=upper && percent>0;}
  boolean contains(int level){return lower<=level && level<=upper;}
 }
 record Actor(int id,int level,boolean safe) {}
 static List<Band> parse(String text,int minimum,int maximum) {
  if(minimum<1 || maximum>65 || minimum>maximum)throw new IllegalArgumentException("Invalid native level bounds");
  var result=new ArrayList<Band>();int previous=0;
  for(String part:text.split(",")) {
   String[] fields=part.trim().split("[-:]");
   if(fields.length!=3)throw new IllegalArgumentException("Use lower-upper:percent population brackets");
   int low=Integer.parseInt(fields[0]),high=Integer.parseInt(fields[1]),percent=Integer.parseInt(fields[2]);
   if(low<1 || high<low || low<=previous || percent<0 || percent>100 || result.size()>=32)
    throw new IllegalArgumentException("Population brackets must be ordered, nonoverlapping ranges with 0..100 weights");
   previous=high;low=Math.max(low,minimum);high=Math.min(high,maximum);
   result.add(new Band(low,high,low>high?0:percent));
  }
  if(result.stream().noneMatch(Band::valid))throw new IllegalArgumentException("No enabled native population bracket");
  return balance(result);
 }
 private static List<Band> balance(List<Band> source) {
  var bands=new ArrayList<>(source);int sum=bands.stream().mapToInt(Band::percent).sum();
  // Donor fills rounding/clamping deficits in range order. Also handle excess,
  // rather than retaining a configured total above 100 or spinning on all-zero ranges.
  for(int difference=100-sum;difference!=0;)for(int i=0;i<bands.size() && difference!=0;i++) {
   var b=bands.get(i);if(!b.valid() || difference<0 && b.percent()<=1)continue;
   int step=difference>0?1:-1;bands.set(i,new Band(b.lower(),b.upper(),b.percent()+step));difference-=step;
  }
  return List.copyOf(bands);
 }
 static int range(List<Band> bands,int level) {
  for(int i=0;i<bands.size();i++)if(bands.get(i).contains(level))return i;
  return -1;
 }
 static int closest(List<Band> bands,int level) {
  int selected=-1,distance=Integer.MAX_VALUE;
  for(int i=0;i<bands.size();i++) {
   var b=bands.get(i);if(!b.valid())continue;
   int delta=level<b.lower()?b.lower()-level:Math.max(0,level-b.upper());
   if(delta<distance){selected=i;distance=delta;}
  }
  return selected;
 }
 static List<Band> dynamic(List<Band> configured,int[] real,double weight) {
  if(real.length!=configured.size() || !Double.isFinite(weight) || weight<0)throw new IllegalArgumentException("Invalid population census");
  int total=Arrays.stream(real).sum();double[] weights=new double[real.length];double sum=0;
  for(int i=0;i<real.length;i++)if(configured.get(i).valid()) {
   weights[i]=1+weight/(total>0?total:1)*Math.log1p(real[i]);sum+=weights[i];
  }
  var result=new ArrayList<Band>();
  for(int i=0;i<weights.length;i++) {
   var b=configured.get(i);result.add(new Band(b.lower(),b.upper(),(int)Math.round(weights[i]/sum*100)));
  }
  // Preserve enabled very-small weights if rounding would otherwise disable them.
  for(int i=0;i<result.size();i++)if(configured.get(i).valid() && result.get(i).percent()==0) {
   var b=result.get(i);result.set(i,new Band(b.lower(),b.upper(),1));
  }
  return balance(result);
 }
 static Map<Integer,Integer> plan(List<Band> bands,List<Actor> actors,Map<Integer,Integer> alreadyPending) {
  int[] actual=new int[bands.size()],desired=new int[bands.size()];
  var buckets=new ArrayList<List<Actor>>();for(int i=0;i<bands.size();i++)buckets.add(new ArrayList<>());
  var plan=new LinkedHashMap<Integer,Integer>();
  for(var actor:actors) {
   Integer pending=alreadyPending.get(actor.id());int index=pending==null?range(bands,actor.level()):pending;
   if(index<0 || index>=bands.size()) {
    index=closest(bands,actor.level());if(index>=0)plan.put(actor.id(),index);
   } else if(pending==null)buckets.get(index).add(actor);
   if(index>=0)actual[index]++;
  }
  for(int i=0;i<bands.size();i++)desired[i]=(int)Math.round(bands.get(i).percent()/100d*actors.size());
  for(int from=0;from<bands.size();from++) {
   var source=buckets.get(from);source.sort(Comparator.comparing(Actor::safe).reversed().thenComparingInt(Actor::id));
   int next=0;
   for(int target=0;target<bands.size() && actual[from]>desired[from];target++) {
    if(!bands.get(target).valid())continue;
    while(actual[from]>desired[from] && actual[target]<desired[target] && next<source.size()) {
     var actor=source.get(next++);plan.put(actor.id(),target);actual[from]--;actual[target]++;
    }
   }
  }
  return Collections.unmodifiableMap(plan);
 }
 static int resetChance(int level,int maximum,int chance,boolean scaled) {
  if(chance<0 || chance>100 || maximum<0 || scaled && maximum==0)throw new IllegalArgumentException("Invalid reset chance");
  return scaled?Math.min(100,(int)(level/(float)maximum*chance)):chance;
 }
 static int randomizeLevel(PlayerBotPopulationPolicy.Settings settings,int current,int highestHuman,boolean downgrade,RandomGenerator rng) {
  if(current>=3 && (!downgrade || current<settings.maxLevel()))return current;
  // RandomizeFirst applies DisableRandomLevels after its downgrade branch.
  if(settings.randomLevels() && downgrade && current>=settings.maxLevel())return settings.minLevel();
  return PlayerBotPopulationPolicy.level(settings,highestHuman,rng);
 }
 private PlayerBotPopulationLevels() {}
}
