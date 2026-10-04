package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;

/** Spatial invariants and isolated settings persistence; never creates characters. */
public final class PlayerBotFormationLayoutCheck {
    static int checks;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        for (String shape : PlayerBotFormationLayout.NAMES) for (int count=1; count<=5; count++)
            for (double heading : new double[]{0, Math.PI/2, Math.PI, 2*Math.PI-.05}) {
                List<PlayerBotNavigation.Point> points = new ArrayList<>();
                for (int slot=0; slot<count; slot++) {
                    var p = PlayerBotFormationLayout.point(100,200,7,heading,slot,count,shape);
                    points.add(p); double radius = Math.hypot(p.x()-100,p.y()-200);
                    check(radius>=3.39 && radius<=6.81,"Owner's center remains empty; bounded formation radius");
                    check(p.z()==7,"Follow owner altitude");
                    if (shape.equals("circle") || shape.equals("spread")) check(Math.abs(radius-(shape.equals("circle")?3.8:6))<.001,"Exact ring radius");
                    var moved = PlayerBotFormationLayout.point(110,220,8,heading,slot,count,shape);
                    check(Math.abs(moved.x()-p.x()-10)<.001 && Math.abs(moved.y()-p.y()-20)<.001 && moved.z()==8,"Translation stays anchored to moving owner");
                }
                for (int i=0;i<count;i++) for (int j=i+1;j<count;j++)
                    check(Math.hypot(points.get(i).x()-points.get(j).x(),points.get(i).y()-points.get(j).y())>3,"Distinct companion slots");
                if ((shape.equals("circle") || shape.equals("spread")) && count>1)
                    check(Math.abs(points.stream().mapToDouble(p->p.x()-100).sum())<.001 && Math.abs(points.stream().mapToDouble(p->p.y()-200).sum())<.001,"Ring surrounds owner evenly");
            }
        check(PlayerBotFormationLayout.parse("BOX").equals("box"),"Case-insensitive selection");
        try { PlayerBotFormationLayout.parse("dismiss"); throw new AssertionError("Unknown selection accepted"); } catch (IllegalArgumentException expected) { checks++; }
        int account=2000000001, owner=2000000001; Path fixture=PlayerBotFormationLayout.path(owner);
        check(!Files.exists(fixture),"Fixture must be empty");
        try {
            check(PlayerBotFormationLayout.selected(account,owner).equals("circle"),"Default is centered Circle");
            check(PlayerBotFormationLayout.configure(account,owner,"spread").equals("spread"),"Select wide ring");
            Properties p=new Properties();try(var in=Files.newInputStream(fixture)){p.load(in);}
            check(p.getProperty("account").equals(Integer.toString(account)) && p.getProperty("formation").equals("spread"),"Owner and formation persisted");
            try {PlayerBotFormationLayout.selected(account+1,owner);throw new AssertionError("Foreign owner settings accepted");}catch(IllegalStateException expected){checks++;}
        } finally {Files.deleteIfExists(fixture);}
        check(!Files.exists(fixture),"Exact fixture settings cleanup");
        System.out.println("OK: "+checks+" centered layout, spacing, owner translation, heading, altitude, validation and isolated persistence checks.");
    }
}
