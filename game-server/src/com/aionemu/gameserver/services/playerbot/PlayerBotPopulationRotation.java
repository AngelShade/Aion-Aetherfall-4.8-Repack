/* RandomPlayerbotFactory::CalculateTotalAccountCount and RandomPlayerbotMgr::
 * AssignAccountTypes/AddRandomBots, mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.random.RandomGenerator;

/** Imported factory/account-selection decisions; native custody and character execution stay in Aion. */
final class PlayerBotPopulationRotation {
    record Plan(int poolCapacity,int admissionSlots,int provisionSlots) {}
    static double ratio(boolean periodic,double configured) {
        if(!periodic)return 1;
        // Aion's persisted event document supports the full 3,000 * 4 reserve.
        if(!Double.isFinite(configured) || configured<=1 || configured>4)
            throw new IllegalArgumentException("Timed population rotation ratio must be >1 and <=4; saved identities retained");
        return configured;
    }
    static int poolCapacity(int maximum,int worldLimit,boolean periodic,double configured,int accountCapacity) {
        if(maximum<0 || maximum>PlayerBotPopulationPolicy.MAX_TARGET || worldLimit<0 || accountCapacity<1 || accountCapacity>100)
            throw new IllegalArgumentException("Invalid population factory bounds");
        // Upstream multiplies maxRandomBots, truncates to an integer, then rounds
        // the required account count up by available characters per account.
        int required=(int)(Math.min(maximum,worldLimit)*(float)ratio(periodic,configured));
        return Math.min(PlayerBotPopulationPolicy.MAX_RESERVE,((required+accountCapacity-1)/accountCapacity)*accountCapacity);
    }
    static Plan plan(int maximum,int worldLimit,boolean periodic,double configured,int accountCapacity,
                     int activeTarget,int registered,int admitted,int totalResidents,int budget) {
        if(activeTarget<0 || registered<0 || admitted<0 || totalResidents<0 || budget<1)
            throw new IllegalArgumentException("Invalid population admission snapshot");
        int capacity=poolCapacity(maximum,worldLimit,periodic,configured,accountCapacity);
        int slots=PlayerBotPopulationPolicy.admissionSlots(activeTarget,admitted,totalResidents,worldLimit,budget);
        // Provision the finite factory pool even after admissions fill the active target.
        // Aion deliberately queues only one native initialization job at a time.
        return new Plan(capacity,slots,registered<capacity?1:0);
    }
    static List<Integer> accounts(Collection<Integer> registered,boolean periodic,double configured,RandomGenerator random) {
        if(registered.stream().anyMatch(id->!PlayerBotPopulationPolicy.serverAccount(id)))
            throw new IllegalArgumentException("Rotation requires dedicated server accounts");
        var accounts=new ArrayList<>(new TreeSet<>(registered));
        if(!periodic)return List.copyOf(accounts);
        double ratio=ratio(true,configured);
        Collections.shuffle(accounts,new Random(random.nextLong()));
        // Exact pinned AddRandomBots arithmetic (uint32 conversion after division).
        int count=(int)((accounts.size()+(float)ratio-1)/(float)ratio);
        return List.copyOf(accounts.subList(0,Math.min(accounts.size(),count)));
    }
    static boolean eligible(boolean ready,boolean inPool,boolean resident,boolean active,long offlineUntil,long now) {
        return ready && inPool && !resident && !active && now>=offlineUntil;
    }
    private PlayerBotPopulationRotation() {}
}
