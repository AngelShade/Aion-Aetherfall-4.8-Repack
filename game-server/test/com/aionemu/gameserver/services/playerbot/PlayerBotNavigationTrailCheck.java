package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Pure route-progress checks; no player actors, world/database initialization or IDs. */
public final class PlayerBotNavigationTrailCheck {
    private static int checks;
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);checks++;}
    public static void main(String[] args) {
        Point old=new Point(351,541,951.7f),bot=new Point(333.34015f,543.85626f,951.60925f),owner=new Point(324.8792f,557.62787f,951.7954f);
        Deque<Point> trail=new ArrayDeque<>(List.of(old,bot,owner));
        check(Math.hypot(old.x()-bot.x(),old.y()-bot.y())>3,"old unvisited head survives legacy head-only consumption");
        PlayerBotNavigationTrail.consume(trail,bot);
        check(trail.size()==1 && trail.peekFirst()==owner,"newest reached breadcrumb consumes stale prefix, preserving owner destination");
        for(int partySlot=0;partySlot<5;partySlot++) {
            Point follower=new Point(bot.x()+partySlot*.12f,bot.y(),bot.z());
            trail=new ArrayDeque<>(List.of(old,bot,owner));PlayerBotNavigationTrail.consume(trail,follower);
            check(trail.size()==1 && trail.peekFirst()==owner,"clustered formation slot advances across trail prefix");
        }
        Point revisit=new Point(bot.x(),bot.y(),bot.z()),after=new Point(320,560,951.8f);
        trail=new ArrayDeque<>(List.of(bot,old,revisit,after));PlayerBotNavigationTrail.consume(trail,bot);
        check(trail.size()==1 && trail.peekFirst()==after,"newest revisit uses identity and discards entire earlier loop");
        Point upstairs=new Point(bot.x(),bot.y(),bot.z()+5);
        trail=new ArrayDeque<>(List.of(upstairs,owner));PlayerBotNavigationTrail.consume(trail,bot);
        check(trail.size()==2,"another elevation layer is not considered reached");
        trail=new ArrayDeque<>(List.of(old,owner));PlayerBotNavigationTrail.consume(trail,bot);
        check(trail.size()==2,"unreached route points remain available around corners");
        trail=new ArrayDeque<>(List.of(old,bot));PlayerBotNavigationTrail.consume(trail,bot);
        check(trail.isEmpty(),"fully reached history becomes empty for normal owner fallback");
        PlayerBotNavigationTrail.consume(trail,bot);check(trail.isEmpty(),"empty route is stable");
        Point invalid=new Point(Float.NaN,0,0);trail=new ArrayDeque<>(List.of(invalid,owner));PlayerBotNavigationTrail.consume(trail,bot);
        check(trail.size()==2,"nonfinite point is never consumed as a proximity match");
        for(float progress:new float[]{0,.25f,1,2.99f}) {
            Point close=new Point(bot.x()+progress,bot.y(),bot.z());trail=new ArrayDeque<>(List.of(old,close,owner));PlayerBotNavigationTrail.consume(trail,bot);
            check(trail.size()==1 && trail.peekFirst()==owner,"normal visited tolerance advances independently of old head");
        }
        System.out.println("OK: "+checks+" generic breadcrumb progress checks; actual reported coordinates included");
    }
}
