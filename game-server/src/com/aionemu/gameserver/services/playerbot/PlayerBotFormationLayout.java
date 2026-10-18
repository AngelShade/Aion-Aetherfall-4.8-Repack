package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** Owner-centred travel formations. Combat positioning remains class controlled. */
public final class PlayerBotFormationLayout {
    private record Preference(int account, String formation) {}
    private static final Map<Integer, Preference> PREFERENCES = new ConcurrentHashMap<>();
    private static final Map<String, List<PlayerBotNavigation.Point>> SLOTS = new ConcurrentHashMap<>();
    static final Set<String> NAMES = Set.of("circle", "box", "line", "spread");

    static String parse(String value) {
        String name = value.toLowerCase(Locale.ROOT);
        if (!NAMES.contains(name)) throw new IllegalArgumentException("Choose Circle, Box, Line or Spread.");
        return name;
    }
    static Path path(int owner) { return Path.of("config", "playerbots", "formation-character-" + owner + ".properties"); }
    static String selected(int account, int owner) {
        Preference preference = PREFERENCES.computeIfAbsent(owner, id -> {
            try {
                Properties p = PlayerBotMetadataConfiguration.load(account,id,"formation",path(id));
                if (p.isEmpty()) return new Preference(account, "circle");
                if (Integer.parseInt(p.getProperty("account")) != account || Integer.parseInt(p.getProperty("character")) != id)
                    throw new IOException("Formation settings owner mismatch");
                return new Preference(account, parse(p.getProperty("formation")));
            } catch (Exception e) { throw new IllegalStateException("Cannot load companion formation", e); }
        });
        if (preference.account != account) throw new IllegalStateException("Formation settings owner mismatch");
        return preference.formation;
    }
    static synchronized String configure(int account, int owner, String value) {
        String formation = parse(value);
        selected(account, owner);
        Properties p = new Properties(); p.setProperty("account", Integer.toString(account));
        p.setProperty("character", Integer.toString(owner)); p.setProperty("formation", formation);
        Path file = path(owner);
        try {
            PlayerBotMetadataConfiguration.save(account,owner,"formation",p);
        } catch (IOException e) { throw new IllegalStateException("Cannot save companion formation", e); }
        PREFERENCES.put(owner, new Preference(account, formation));
        return formation;
    }
    /** Returns false for every existing command, so its installed handler stays intact. */
    public static boolean command(Player owner, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("distance")) {
            try {
                if (args.length != 2) throw new IllegalArgumentException("Use .bot distance <2-12 meters>.");
                float follow = Float.parseFloat(args[1]);
                int count = PlayerBotSpacing.configureParty(owner, follow);
                PacketSendUtility.sendMessage(owner, "Follow distance: " + follow + " m for " + count + " companions. Individual attack spacing is retained.");
            } catch (RuntimeException e) {
                PacketSendUtility.sendMessage(owner, e instanceof IllegalArgumentException ? e.getMessage() : "Companion distance could not be saved.");
            }
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("formation")) return false;
        try {
            if (args.length != 2) throw new IllegalArgumentException("Use .bot formation circle|box|line|spread.");
            String name = configure(owner.getAccount().getId(), owner.getObjectId(), args[1]);
            PacketSendUtility.sendMessage(owner, "Party formation: " + name + ". You are the center; companions regroup while following after combat.");
        } catch (RuntimeException e) {
            PacketSendUtility.sendMessage(owner, e instanceof IllegalArgumentException ? e.getMessage() : "Companion formation could not be saved.");
        }
        return true;
    }
    static PlayerBotNavigation.Point point(float x, float y, float z, double heading, int slot, int count, String formation) {
        if (count < 1 || count > PlayerBotRaidPolicy.MAX_COMPANIONS || slot < 0 || slot >= count) throw new IllegalArgumentException("Invalid party/alliance formation slot");
        parse(formation);
        double front, side;
        if (formation.equals("line")) {
            int row = count > 5 ? slot / 5 : 0;
            int column = count > 5 ? slot % 5 : slot;
            // The first slot is the party's front; remaining members form its wings.
            if (column == 0) { front = row == 0 ? 3.4 : -3.4 * row; side = 0; }
            else { front = -3.4 * row; side = ((column - 1) % 2 == 0 ? -1 : 1) * 3.4 * ((column - 1) / 2 + 1); }
        } else {
            double angle = (formation.equals("box") ? -Math.PI / 4 : 0) + slot * Math.PI * 2 / count;
            double radius = formation.equals("spread") ? 6 : 3.8;
            front = Math.cos(angle) * radius; side = Math.sin(angle) * radius;
            if (formation.equals("box")) {
                double divisor = Math.max(Math.abs(Math.cos(angle)), Math.abs(Math.sin(angle)));
                front /= divisor; side /= divisor;
            }
        }
        return new PlayerBotNavigation.Point(x + (float)(Math.cos(heading)*front - Math.sin(heading)*side),
                y + (float)(Math.sin(heading)*front + Math.cos(heading)*side), z);
    }
    static PlayerBotNavigation.Point destination(Player owner, Player bot, double heading) {
        // Assign the forward slot to a tank, then melee; IDs break ties stably.
        List<Integer> ids = PlayerBotCombatPosition.formationOrder(owner,bot);
        int slot = ids.indexOf(bot.getObjectId());
        if (slot < 0) { ids = List.of(bot.getObjectId()); slot = 0; }
        String formation = selected(owner.getAccount().getId(), owner.getObjectId());
        var point = rolePoint(heading, slot, ids.size(), formation);
        var rest = PlayerBotSpacing.formation(owner,bot,new PlayerBotNavigation.Point(owner.getX()+point.x(), owner.getY()+point.y(), owner.getZ()));
        if (!owner.getMoveController().isInMove() || owner.isFlying()) return rest;
        // Like WoW followers, travel behind the leader instead of racing to the
        // formation's front slots; the chosen formation is restored on stopping.
        double spacing = Math.hypot(rest.x()-owner.getX(), rest.y()-owner.getY());
        return travel(owner, heading, slot, ids.size(), spacing);
    }
    /** Two-file column behind the leader, ordered tank-first like the rest slots. */
    static PlayerBotNavigation.Point travel(Player owner, double heading, int slot, int count, double spacing) {
        int row = slot / 2;
        double side = count == 1 || count % 2 == 1 && slot == count - 1 ? 0 : (slot % 2 == 0 ? -1 : 1) * Math.min(1.6, Math.max(.9, spacing * .45));
        double front = -(Math.max(1.5, spacing * .75) + row * 2.2);
        return new PlayerBotNavigation.Point(owner.getX() + (float)(Math.cos(heading)*front - Math.sin(heading)*side),
            owner.getY() + (float)(Math.sin(heading)*front + Math.cos(heading)*side), owner.getZ());
    }
    static int roleRank(PlayerBotRules.Role role) {
        return switch (role) { case TANK -> 0; case MELEE -> 1; case RANGED -> 2; case SUPPORT -> 3; case HEALER -> 4; };
    }
    /** Rank roles front to back, then use fixed character IDs within each role.
     * Geometry ordering is independent of movement, target and health. */
    static PlayerBotNavigation.Point rolePoint(double heading, int rank, int count, String formation) {
        if(count<1 || count>PlayerBotRaidPolicy.MAX_COMPANIONS || rank<0 || rank>=count)
            throw new IllegalArgumentException("Invalid formation rank");
        parse(formation);
        var slots=SLOTS.computeIfAbsent(formation+":"+count, key -> orderedSlots(count,formation));
        var p=slots.get(rank);
        return new PlayerBotNavigation.Point((float)(Math.cos(heading)*p.x()-Math.sin(heading)*p.y()),
            (float)(Math.sin(heading)*p.x()+Math.cos(heading)*p.y()),0);
    }
    private static List<PlayerBotNavigation.Point> orderedSlots(int count, String formation) {
        List<PlayerBotNavigation.Point> slots = new ArrayList<>(count);
        for (int index=0; index<count; index++) {
            var p=point(0,0,0,0,index,count,formation);
            // A travelling line has successive rows instead of parking every
            // healer/ranged companion alongside the tank's wings.
            if (formation.equals("line") && index>0)
                p=new PlayerBotNavigation.Point(p.x()-1.2f*((index+1)/2),p.y(),0);
            slots.add(p);
        }
        slots.sort(Comparator.comparingDouble((PlayerBotNavigation.Point p)->-Math.round(p.x()*1000)).thenComparingDouble(PlayerBotNavigation.Point::y));
        return List.copyOf(slots);
    }
    static double lineExtent(int count) {
        double radius = 0;
        for (int slot = 0; slot < count; slot++) {
            var p = rolePoint(0, slot, count, "line");
            radius = Math.max(radius, Math.hypot(p.x(), p.y()));
        }
        return radius;
    }
    private PlayerBotFormationLayout() {}
}
