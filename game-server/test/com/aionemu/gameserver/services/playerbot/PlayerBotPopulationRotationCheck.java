package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import java.lang.reflect.Field;
import com.aionemu.gameserver.model.*;

/** Imported factory/cohort/expiry decisions plus actual file and admission transitions; no world/DB/native IDs. */
public final class PlayerBotPopulationRotationCheck {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void refused(Runnable action){try{action.run();throw new AssertionError("Invalid rotation admitted");}catch(IllegalArgumentException e){checks++;}}
    private static Field field(String name)throws Exception{var f=PlayerBotService.class.getDeclaredField(name);f.setAccessible(true);return f;}
    public static void main(String[] args)throws Exception {
        check(PlayerBotPopulationPolicy.creationDeficit(12,0,806,806)==0,"Old active-target-only bound reproduces missing reserve for 93 absences");
        check(PlayerBotPopulationRotation.poolCapacity(1000,1000,true,2,20)==2000,"Pinned maximum-times-ratio factory pool");
        check(PlayerBotPopulationRotation.poolCapacity(1000,1000,false,2,20)==1000,"Permanent mode provisions maximum without rotation multiplier");
        check(PlayerBotPopulationRotation.poolCapacity(1000,300,true,2,20)==600,"Native world capacity bounds factory basis");
        check(PlayerBotPopulationRotation.poolCapacity(33,1000,true,2,20)==80,"Native account count rounds up as pinned factory");
        check(PlayerBotPopulationRotation.poolCapacity(1000,1000,true,2.5,20)==2500,"Fractional configured reserve ratio");
        check(PlayerBotPopulationRotation.poolCapacity(0,1000,true,2,20)==0,"Disabled maximum needs no factory pool");
        check(PlayerBotPopulationRotation.poolCapacity(1000,1000,true,4,3)==4002,"Pinned account rounding now fits below expanded native event ceiling");
        for(double ratio:List.of(Double.NaN,Double.POSITIVE_INFINITY,0d,1d,4.1d))refused(()->PlayerBotPopulationRotation.poolCapacity(1000,1000,true,ratio,20));
        refused(()->PlayerBotPopulationRotation.poolCapacity(3001,3000,true,2,20));refused(()->PlayerBotPopulationRotation.poolCapacity(1000,1000,true,2,0));
        var missing=PlayerBotPopulationRotation.plan(1000,1000,true,2,20,806,806,713,713,12);
        check(missing.poolCapacity()==2000 && missing.admissionSlots()==12 && missing.provisionSlots()==1,"Actual reported 806/713 snapshot prepares bounded persistent reserve and admissions");
        var full=PlayerBotPopulationRotation.plan(1000,1000,true,2,20,806,806,806,806,12);
        check(full.admissionSlots()==0 && full.provisionSlots()==1,"Factory continues while current active target is full");
        var ready=PlayerBotPopulationRotation.plan(1000,1000,true,2,20,806,2000,806,806,12);
        check(ready.provisionSlots()==0 && ready.admissionSlots()==0,"Completed reserve never recreates characters on a full pass");
        check(PlayerBotPopulationRotation.plan(1000,1000,true,2,20,806,2100,713,713,12).provisionSlots()==0,"Older excess IDs retained without further creation");
        check(PlayerBotPopulationRotation.plan(1000,1000,true,2,20,104,2000,100,996,12).admissionSlots()==4,"Manual residents keep exact native world capacity");
        check(PlayerBotPopulationRotation.plan(1000,1000,true,2,20,100,2000,100,1000,12).admissionSlots()==0,"Full world has no admission burst");
        for(boolean readyFlag:List.of(false,true))check(PlayerBotPopulationRotation.eligible(readyFlag,true,false,false,1000,1000)==readyFlag,"Only complete native roster rows enter");
        check(!PlayerBotPopulationRotation.eligible(true,false,false,false,0,1000),"Foreign roster row excluded");
        check(!PlayerBotPopulationRotation.eligible(true,true,true,false,0,1000),"Pending residence not double-admitted");
        check(!PlayerBotPopulationRotation.eligible(true,true,false,true,0,1000),"Online identity not double-admitted");
        check(!PlayerBotPopulationRotation.eligible(true,true,false,false,1001,1000),"Saved cooldown remains effective before expiry");
        check(PlayerBotPopulationRotation.eligible(true,true,false,false,1000,1000),"Exact expiry allows native saved-character reuse");
        var accounts=new ArrayList<Integer>();for(int i=0;i<102;i++)accounts.add(-1000000-i);
        check(PlayerBotPopulationRotation.accounts(accounts,true,2,new Random(1)).size()==51,"Pinned account subset arithmetic");
        check(PlayerBotPopulationRotation.accounts(accounts,false,2,new Random(1)).size()==102,"Permanent mode uses every dedicated account");
        check(PlayerBotPopulationRotation.accounts(accounts,true,2,new Random(1)).equals(PlayerBotPopulationRotation.accounts(accounts,true,2,new Random(1))),"Seeded cohorts reproducible");
        check(!PlayerBotPopulationRotation.accounts(accounts,true,2,new Random(1)).equals(PlayerBotPopulationRotation.accounts(accounts,true,2,new Random(2))),"Later passes rotate account cohorts");
        check(PlayerBotPopulationRotation.accounts(accounts.subList(0,6),true,2.5,new Random(1)).size()==3,"Fractional pinned account subset conversion");
        refused(()->PlayerBotPopulationRotation.accounts(List.of(1),true,2,new Random(1)));
        var dir=Files.createTempDirectory(Path.of("."),"rotation-check-");
        try {
            var store=PlayerBotPopulationTestStorage.population(dir.resolve("pool.json"));var rng=new Random(171);
            // Provision a finite synthetic pool using the actual imported plan and actual serializer.
            for(int index=0;index<80;index++) {
                var plan=PlayerBotPopulationRotation.plan(40,1000,true,2,10,40,index,40,40,12);
                check(plan.provisionSlots()==1,"One factory initialization while active target already full");
                var race=(index/10)%2==0?Race.ELYOS:Race.ASMODIANS;int account=PlayerBotPopulationPolicy.account(race,index/20);
                var pool=store.load().get(account);if(pool==null)pool=new PlayerBotWorldPopulation.Pool(account,"fixture",(byte)0,1,race,Gender.MALE,PlayerBotWorldLooks.neutral(),10,List.of());
                store.put(pool.append(100+index));
            }
            var persisted=PlayerBotPopulationTestStorage.population(dir.resolve("pool.json")).load();
            byte[] savedPool=com.alibaba.fastjson2.JSON.toJSONBytes(new TreeMap<>(store.load()));
            Set<Integer> identities=new HashSet<>();persisted.values().forEach(p->identities.addAll(p.characters()));
            check(identities.size()==80 && persisted.size()==8,"Both faction pools persist all fixture identities across restart");
            Map<Integer,PlayerClass> classes=new HashMap<>();persisted.values().forEach(p->p.characters().forEach(id->classes.put(id,PlayerBotWorldPopulation.planned(p,id))));
            Set<Integer> active=new HashSet<>();Map<Integer,Long> cooldown=new HashMap<>();long now=10000;
            for(int round=0;round<100;round++) {
                now+=1000;final long clock=now;
                var departures=active.stream().sorted().limit(4).toList();for(int id:departures){active.remove(id);cooldown.put(id,now+5000);}
                for(int pass=0;pass<100 && active.size()<40;pass++) {
                    var plan=PlayerBotPopulationRotation.plan(40,1000,true,2,10,40,identities.size(),active.size(),active.size(),12);
                    check(plan.provisionSlots()==0,"Repeated rotations cannot allocate replacement identities");
                    var candidates=new ArrayList<PlayerBotPopulationPolicy.Candidate>();
                    for(int account:PlayerBotPopulationRotation.accounts(persisted.keySet(),true,2,rng)) {
                        var pool=persisted.get(account);for(int id:pool.characters())if(PlayerBotPopulationRotation.eligible(true,true,false,active.contains(id),cooldown.getOrDefault(id,0L),clock))
                            candidates.add(new PlayerBotPopulationPolicy.Candidate(id,pool.race()));
                    }
                    var admissions=PlayerBotPopulationPolicy.admit(candidates,plan.admissionSlots(),50,rng);
                    check(admissions.stream().allMatch(id->!active.contains(id) && cooldown.getOrDefault(id,0L)<=clock),"Native candidate gates preserve active identity and unexpired breaks");active.addAll(admissions);
                }
                check(active.size()==40,"Finite cohort passes refill the active target from eligible reserve");
                check(identities.containsAll(active) && identities.size()==80,"Rotation uses the same saved pool");
            }
            var reread=PlayerBotPopulationTestStorage.population(dir.resolve("pool.json")).load();
            check(Arrays.equals(savedPool,com.alibaba.fastjson2.JSON.toJSONBytes(new TreeMap<>(store.load()))) && reread.keySet().equals(persisted.keySet())
                && reread.entrySet().stream().allMatch(e->e.getValue().characters().equals(persisted.get(e.getKey()).characters())),"Rotating activity does not rewrite saved factory records/builds");
            check(classes.entrySet().stream().allMatch(e->reread.values().stream().filter(p->p.characters().contains(e.getKey())).anyMatch(p->PlayerBotWorldPopulation.planned(p,e.getKey())==e.getValue())),"Saved planned classes never rerolled by rotation");
            // Exercise the actual service's failed-entry transition without native world/SQL.
            var constructor=PlayerBotService.class.getDeclaredConstructor();constructor.setAccessible(true);var service=constructor.newInstance();
            var roster=PlayerBotPopulationTestStorage.roster(dir.resolve("residents.properties"));field("worldRoster").set(service,roster);
            var global=new PlayerBotWorldRoster.Resident(-1000000,99,210030000,1,2,3,(byte)0);
            var manual=new PlayerBotWorldRoster.Resident(7,98,210030000,1,2,3,(byte)0);
            var residentMap=new TreeMap<Integer,PlayerBotWorldRoster.Resident>();residentMap.put(99,global);residentMap.put(98,manual);roster.save(residentMap);field("residents").set(service,residentMap);field("nextServerPopulation").setLong(service,60000);
            service.failedWorldAdmission(global,10000);
            check(!roster.load().containsKey(99) && roster.load().containsKey(98),"Failed global entry releases durable admission while manual residency remains");
            var retries=(Map<?,?>)field("nextWorldAdmission").get(service);
            check(retries.get(99).equals(40000L) && field("nextServerPopulation").getLong(service)==0,"Failure preserves completion-based 30s retry and wakes other admissions");
            service.failedWorldAdmission(manual,10000);check(roster.load().containsKey(98),"Owned/manual pool retains its existing native entry policy");
            field("shuttingDown").setBoolean(service,true);residentMap=new TreeMap<>();residentMap.put(99,global);roster.save(residentMap);field("residents").set(service,residentMap);
            service.failedWorldAdmission(global,20000);check(roster.load().containsKey(99),"Shutdown retains recorded residence for next native restart");
        } finally {try(var paths=Files.walk(dir)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        System.out.println("OK: "+checks+" factory/account rotation/reuse/admission checks; 100 rotations; no World/DB/native IDs");
    }
}
