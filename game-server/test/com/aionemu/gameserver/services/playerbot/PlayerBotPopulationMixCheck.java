package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.Race;

/** Saved XP snapshots and real admission policy: no SQL, World or character allocation. */
public final class PlayerBotPopulationMixCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  var candidates=new ArrayList<PlayerBotPopulationPolicy.Candidate>();var levels=new HashMap<Integer,Integer>();
  for(int i=1;i<=2000;i++){candidates.add(new PlayerBotPopulationPolicy.Candidate(i,i<=1000?Race.ELYOS:Race.ASMODIANS));
   levels.put(i,i%100<65?65:1+i%35);}
  var saved=Map.copyOf(levels);var online=Map.of(Race.ELYOS,Map.of(65,400),Race.ASMODIANS,Map.of(65,400));
  var selected=PlayerBotPopulationPolicy.admit(candidates,120,50,new Random(71),levels,online);
  check(selected.size()==120,"Level mix never reduces available admission budget");
  check(new HashSet<>(selected).size()==120,"Saved identities admitted once");
  check(selected.stream().filter(id->id<=1000).count()==60,"Exact faction quota retained");
  check(selected.stream().noneMatch(id->levels.get(id)==65),"Already overrepresented maximum level does not dominate fresh admission");
  check(levels.equals(saved) && online.get(Race.ELYOS).equals(Map.of(65,400)),"Native XP snapshots and caller counts remain unchanged");
  check(selected.equals(PlayerBotPopulationPolicy.admit(candidates,120,50,new Random(71),levels,online)),"Seeded fair selection reproducible");
  check(!selected.equals(PlayerBotPopulationPolicy.admit(candidates,120,50,new Random(72),levels,online)),"Equal-level characters remain randomized");
  var onlyMax=candidates.stream().filter(c->levels.get(c.character())==65).toList();
  check(PlayerBotPopulationPolicy.admit(onlyMax,120,50,new Random(7),levels,online).size()==120,"Depleted level bands fill from eligible reserve without culling or creating extra characters");
  var allElyos=candidates.stream().filter(c->c.race()==Race.ELYOS).toList();
  check(PlayerBotPopulationPolicy.admit(allElyos,120,0,new Random(7),levels,online).size()==120,"Pinned depleted-faction fallback retained");
  check(PlayerBotPopulationPolicy.admit(candidates,0,50,new Random(7),levels,online).isEmpty(),"No slots means no admission");
  check(PlayerBotPopulationPolicy.admit(candidates,120,50,new Random(7),Map.of(),online).isEmpty(),"Absent native XP is never guessed");
  check(PlayerBotPopulationPolicy.admit(candidates,3000,50,new Random(7),levels,online).size()==2000,"Finite saved reserve remains authoritative");
  var one=new ArrayList<PlayerBotPopulationPolicy.Candidate>();var oneLevels=new HashMap<Integer,Integer>();
  for(int level=1;level<=65;level++)for(int copy=0;copy<30;copy++){int id=level*100+copy;one.add(new PlayerBotPopulationPolicy.Candidate(id,Race.ELYOS));oneLevels.put(id,level);}
  var mix=PlayerBotPopulationPolicy.admit(one,1300,100,new Random(71),oneLevels,Map.of());
  var counts=new HashMap<Integer,Integer>();for(int id:mix)counts.merge(oneLevels.get(id),1,Integer::sum);
  check(counts.size()==65 && counts.values().stream().allMatch(n->n==20),"1300 admissions spread equally over 65 eligible saved levels");
  var changed=new HashMap<>(oneLevels);changed.replaceAll((id,l)->65);
  check(PlayerBotPopulationPolicy.admit(one,120,100,new Random(7),changed,Map.of()).stream().allMatch(id->changed.get(id)==65),"Fresh native earned levels outrank historical creation settings");
  var s=PlayerBotPopulationCheck.settings(1,65,0,0,true,false);int[] initial=new int[66];var rng=new Random(19);
  for(int n=0;n<65000;n++)initial[PlayerBotPopulationPolicy.level(s,1,rng)]++;
  check(Arrays.stream(initial,1,66).allMatch(n->n>850 && n<1150),"Zero endpoint weights use pinned uniform creation across the full native range");
  check(PlayerBotPopulationPolicy.level(s,1,new Random(19))==PlayerBotPopulationPolicy.level(s,65,new Random(19)),"Player level does not affect unsynchronized new world bots");
  var fixed=PlayerBotPopulationCheck.settings(1,65,0,0,false,false);
  check(PlayerBotPopulationPolicy.level(fixed,48,new Random(19))==7,"Owner fixed-level switch still controls new creation");
  System.out.println("OK: "+checks+" saved-level population mix checks; no World/DB/IDs");
 }
}
