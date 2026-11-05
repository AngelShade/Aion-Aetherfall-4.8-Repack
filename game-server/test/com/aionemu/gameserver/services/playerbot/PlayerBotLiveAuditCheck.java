package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.templates.npc.NpcRating;

/** Decisions behind the 5 November live audit repairs; no World, actors or SQL. */
public final class PlayerBotLiveAuditCheck {
 static int checks;
 static void check(boolean result,String text){checks++;if(!result)throw new AssertionError(text);}
 public static void main(String[] args) {
  // Camps: the camps that killed level 1-3 bots over and over.
  check(PlayerBotWorldPlacement.campMinimum(1)==1,"a level 1 camp takes a level 1 bot");
  check(PlayerBotWorldPlacement.campMinimum(2)==1,"a level 1 bot may face one level up");
  check(PlayerBotWorldPlacement.campMinimum(5)==4,"level 5 mobs wait until level 4");
  check(PlayerBotWorldPlacement.campMinimum(6)==5,"level 6 mobs wait until level 5");
  check(PlayerBotWorldPlacement.campMinimum(9)==8,"level 9 mobs wait until level 8");
  check(PlayerBotWorldPlacement.campMinimum(12)==10,"past ascension the margin widens");
  check(PlayerBotWorldPlacement.campMinimum(33)==30,"veterans may face three levels up");
  check(PlayerBotWorldPlacement.campMinimum(65)==62,"the top camps stay reachable");
  for(int mob=1;mob<=69;mob++) {
   int minimum=PlayerBotWorldPlacement.campMinimum(mob);
   check(minimum>=1 && minimum<=mob,"camp minimum is a real level at or below the mob "+mob);
   check(mob<=minimum+PlayerBotWorldPlacement.campMargin(minimum),"camp minimum respects its margin "+mob);
   check(minimum==1 || mob>minimum-1+PlayerBotWorldPlacement.campMargin(minimum-1),"camp minimum is the lowest allowed "+mob);
  }
  // Deaths: dying right after landing keeps the next revival out of the camps.
  check(PlayerBotPopulationPolicy.revivesInCity(10),"a Daeva goes to a city after a quick death");
  check(!PlayerBotPopulationPolicy.revivesInCity(9),"a starter character stays at the starter spawn");
  long now=10_000_000;
  check(PlayerBotPopulationPolicy.deathStreak(0,0,now)==0,"a bot never landed has no streak");
  check(PlayerBotPopulationPolicy.deathStreak(0,now-20000,now)==1,"a death 20 seconds after landing counts");
  check(PlayerBotPopulationPolicy.deathStreak(1,now-20000,now)==2,"streaks grow");
  check(PlayerBotPopulationPolicy.deathStreak(3,now-PlayerBotPopulationPolicy.QUICK_DEATH_MS,now)==0,"surviving the window clears the streak");
  check(PlayerBotPopulationPolicy.deathStreak(3,now+1000,now)==0,"a landing from the future is ignored");
  check(PlayerBotPopulationPolicy.returnsToField(0),"an ordinary death still returns to a camp");
  check(!PlayerBotPopulationPolicy.returnsToField(1),"a quick death does not go back to a camp");
  check(!PlayerBotPopulationPolicy.rebuildLabel("revive").contains("reset"),"a revival rebuild is not logged as a level reset");
  check(!PlayerBotPopulationPolicy.rebuildLabel("teleport").contains("reset"),"a relocation rebuild is not logged as a level reset");
  check(PlayerBotPopulationPolicy.rebuildLabel("reset").equals("level reset/retry"),"a real reset keeps its label");
  check(PlayerBotPopulationPolicy.rebuildLabel("").equals("level reset/retry"),"an old saved intent keeps its label");
  // Leaps: two per window, then the planner moves the bot to a fresh camp.
  check(PlayerBotWorldActivities.waitingLost(now,now-1),"a lost bot waits for its move");
  check(!PlayerBotWorldActivities.waitingLost(now,now-PlayerBotWorldActivities.LOST_WAIT_MS),"a lost bot cannot wait forever");
  check(!PlayerBotWorldActivities.waitingLost(now,0),"a bot that was never lost is not waiting");
  var budget=new PlayerBotLedgeLeap.Budget();
  check(!budget.spent(now),"a fresh bot may leap");
  budget.spend(now);
  check(!budget.spent(now+1000),"one leap leaves one more");
  budget.spend(now+1000);
  check(budget.spent(now+2000),"two leaps use the budget");
  check(budget.spent(now+PlayerBotLedgeLeap.Budget.WINDOW_MS-1),"the budget holds for the whole window");
  check(!budget.spent(now+PlayerBotLedgeLeap.Budget.WINDOW_MS),"the window ends");
  budget.spend(now);budget.spend(now+1);budget.clear();
  check(!budget.spent(now+2),"moving to a new camp restores the budget");
  budget.spend(now);
  budget.spend(now+PlayerBotLedgeLeap.Budget.WINDOW_MS);
  check(!budget.spent(now+PlayerBotLedgeLeap.Budget.WINDOW_MS+1),"a leap after the window starts a new one");
  // Goal hunts: nearly every gear goal names an elite.
  check(PlayerBotHunting.goalLevelFits(30,30,NpcRating.ELITE),"a bot may hunt an elite of its own level");
  check(PlayerBotHunting.goalLevelFits(30,28,NpcRating.ELITE),"a bot may hunt a lower elite");
  check(!PlayerBotHunting.goalLevelFits(30,31,NpcRating.ELITE),"a higher elite needs help");
  check(PlayerBotHunting.goalLevelFits(30,34,NpcRating.NORMAL),"normal mobs keep the +4 rule");
  check(!PlayerBotHunting.goalLevelFits(30,35,NpcRating.NORMAL),"normal mobs past +4 stay refused");
  check(!PlayerBotHunting.goalLevelFits(30,5,NpcRating.ELITE),"an elite that gives no XP is left alone");
  // Market: a world bot's budget has to reach what its gear costs.
  check(PlayerBotMarket.dailyAllowance(false,65,50000,1000)==49000,"a companion keeps its owner's budget");
  check(PlayerBotMarket.dailyAllowance(false,65,50000,60000)==0,"a companion never overspends");
  check(PlayerBotMarket.dailyAllowance(true,65,50000,0)==2000000,"a level 65 world bot has the full budget");
  check(PlayerBotMarket.dailyAllowance(true,30,50000,0)>400000,"a level 30 world bot can afford level 30 gear");
  check(PlayerBotMarket.dailyAllowance(true,1,50000,0)>=1000,"a new world bot has pocket money");
  check(PlayerBotMarket.dailyAllowance(true,65,50000,2500000)==0,"a world bot stops at its budget");
  // Healing and sleep.
  check(PlayerBotRecoveryPolicy.selfCombatLimit(PlayerBotRules.Role.MELEE,85)==60,"a fighter keeps hitting above 60 percent");
  check(PlayerBotRecoveryPolicy.selfCombatLimit(PlayerBotRules.Role.TANK,85)==60,"a tank keeps hitting above 60 percent");
  check(PlayerBotRecoveryPolicy.selfCombatLimit(PlayerBotRules.Role.HEALER,85)==85,"a healer keeps its limit");
  check(PlayerBotRecoveryPolicy.selfCombatLimit(PlayerBotRules.Role.MELEE,45)==45,"a lower limit is never raised");
  check(PlayerBotWorldActivity.lingering(now,now),"hate holds a bot awake at first");
  check(PlayerBotWorldActivity.lingering(now+PlayerBotWorldActivity.SETTLE_MS-1,now),"hate holds it for the whole wait");
  check(!PlayerBotWorldActivity.lingering(now+PlayerBotWorldActivity.SETTLE_MS,now),"hate alone cannot keep it awake forever");
  System.out.println("OK: "+checks+" live audit decision checks");
 }
 private PlayerBotLiveAuditCheck(){}
}
