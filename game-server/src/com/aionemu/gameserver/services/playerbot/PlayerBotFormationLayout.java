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
        if (count < 1 || count > 5 || slot < 0 || slot >= count) throw new IllegalArgumentException("Invalid party formation slot");
        parse(formation);
        double front, side;
        if (formation.equals("line")) {
            // The first slot is the party's front; remaining members form its wings.
            if (slot == 0) { front = 3.4; side = 0; }
            else { front = 0; side = ((slot - 1) % 2 == 0 ? -1 : 1) * 3.4 * ((slot - 1) / 2 + 1); }
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
        List<Integer> ids = PlayerBotCombatPosition.formationOrder(owner);
        int slot = ids.indexOf(bot.getObjectId());
        if (slot < 0) { ids = List.of(bot.getObjectId()); slot = 0; }
        return PlayerBotSpacing.formation(owner,bot,point(owner.getX(), owner.getY(), owner.getZ(), heading, slot, ids.size(), selected(owner.getAccount().getId(), owner.getObjectId())));
    }
    private PlayerBotFormationLayout() {}
}
