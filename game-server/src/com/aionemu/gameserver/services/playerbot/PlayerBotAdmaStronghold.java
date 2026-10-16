package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.function.BooleanSupplier;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Reuses visible native encounter targets and object channels; never grants a free cleanse or room-wide pull. */
final class PlayerBotAdmaStronghold {
    private static final int MAP = 320130000;
    private record MechanicAction(String name, BooleanSupplier useful, BooleanSupplier execution) implements Action {
        public boolean isUseful() { return useful.getAsBoolean(); }
        public boolean isPossible() { return true; }
        public boolean execute() { return execution.getAsBoolean(); }
    }
    static boolean scenery(Npc npc) {
        return npc.getWorldId() == MAP && (npc.getNpcId() == 281026 || npc.getNpcId() == 281027 || npc.getNpcId() == 281073);
    }
    static boolean coffin(Npc npc) {
        return switch (npc.getNpcId()) { case 280942,280950,281055,281056,281057,281058 -> true; default -> false; };
    }
    static boolean mechanicEnemy(Player bot, Npc npc, List<Player> party) {
        if (bot.getWorldId() != MAP || !PlayerBotTargetEligibility.visible(bot,npc)) return false;
        if (coffin(npc)) return bot.getKnownList().stream().map(known -> known.get()).anyMatch(object -> object instanceof Npc boss
            && boss.getNpcId() == 214696 && PlayerBotTargetEligibility.visible(bot,boss) && !boss.isDead()
            && boss.getLifeStats().getHpPercentage() <= 25 && party.stream().anyMatch(player -> boss.getAggroList().isHating(player)
                || player.getSummon() != null && boss.getAggroList().isHating(player.getSummon())));
        if (npc.getNpcId() != 280933 && npc.getNpcId() != 280949) return false;
        return npc.getCreator() instanceof Npc boss && boss.getNpcId() == 214696
            && PlayerBotTargetEligibility.visible(bot,boss) && party.stream().anyMatch(player -> boss.getAggroList().isHating(player)
                || player.getSummon() != null && boss.getAggroList().isHating(player.getSummon()));
    }
    static boolean attackable(Npc npc) {
        if (npc.getWorldId() != MAP || !coffin(npc)) return true;
        // An early coffin strike has disputed retail triggers. Do not start it automatically before the late phase.
        return npc.getWorldMapInstance().getNpcs(214696).stream().anyMatch(boss -> boss.getNpcId() == 214696 && boss.isSpawned() && !boss.isDead()
            && boss.getLifeStats().getHpPercentage() <= 25 && boss.getAggroList().stream().anyMatch(info -> info.getHate() > 0));
    }
    static Npc target(Player bot, Role role, int commanded, List<Npc> enemies) {
        if (bot.getWorldId() != MAP || commanded != 0) return null;
        Npc boss = enemies.stream().filter(n -> n.getNpcId() == 214696).findFirst().orElse(null);
        if (role == Role.TANK && boss != null) return boss;
        Npc page = enemies.stream().filter(n -> n.getNpcId() == 280933 || n.getNpcId() == 280949)
            .min(Comparator.comparingInt((Npc n) -> n.getNpcId() == 280949 ? 0 : 1)
                .thenComparingInt(n -> n.getLifeStats().getCurrentHp()).thenComparingInt(Npc::getObjectId)).orElse(null);
        if (page != null) return page;
        return boss != null && boss.getLifeStats().getHpPercentage() <= 25
            ? enemies.stream().filter(PlayerBotAdmaStronghold::coffin).filter(PlayerBotAdmaStronghold::attackable)
                .min(Comparator.comparingDouble(n -> PositionUtil.getDistance(bot,n))).orElse(null) : null;
    }
    private static int device(Player bot) {
        return bot.getEffectController().hasAbnormalEffect(18462) ? 700396
            : bot.getEffectController().hasAbnormalEffect(18463) ? 700397 : 0;
    }
    static boolean channeling(PlayerBotSession session) {
        Player bot = session.bot();
        return PlayerBotPartyBehavior.state(session).order == Order.FOLLOW && !session.closing() && !bot.isDead()
            && bot.getWorldId() == MAP && bot.getTarget() instanceof Npc npc && npc.getNpcId() == device(bot)
            && npc.isSpawned() && !npc.isDead() && npc.getWorldMapInstance() == bot.getWorldMapInstance()
            && bot.getKnownList().sees(npc) && PositionUtil.isInRange(session.owner(),npc,40)
            && PositionUtil.isInTalkRange(bot,npc) && GeoService.getInstance().canSee(bot,npc);
    }
    static Trigger recovery(PlayerBotSession session, PlayerBotNavigation navigation, List<Npc> enemies) {
        Player bot = session.bot();
        if (bot.getWorldId() != MAP || bot.isDead() || session.closing()
            || enemies.stream().noneMatch(n -> n.getNpcId() == 214695)) return null;
        int id = device(bot); if (id == 0) return null;
        List<Npc> devices = new ArrayList<>();
        bot.getKnownList().forEachNpc(npc -> {
            if (npc.getNpcId() == id && npc.isSpawned() && !npc.isDead()
                && npc.getWorldId() == bot.getWorldId() && npc.getInstanceId() == bot.getInstanceId()
                && PositionUtil.isInRange(session.owner(),npc,40)) devices.add(npc);
        });
        Npc npc = devices.stream().min(Comparator.comparingDouble(n -> PositionUtil.getDistance(bot,n))).orElse(null);
        if (npc == null) return null;
        BooleanSupplier useful = () -> PlayerBotPartyBehavior.state(session).order == Order.FOLLOW && !session.closing()
            && !bot.isDead() && device(bot) == id && npc.isSpawned() && !npc.isDead()
            && npc.getWorldId() == MAP && bot.getWorldMapInstance() == npc.getWorldMapInstance()
            && bot.getKnownList().sees(npc) && PositionUtil.isInRange(session.owner(),npc,40);
        return new Trigger(() -> true, new MechanicAction("use Adma recovery object", useful, () -> {
            if (!useful.getAsBoolean()) return false;
            if (!PositionUtil.isInTalkRange(bot,npc) || !PositionUtil.isInRange(bot,npc,2,false)
                || !GeoService.getInstance().canSee(bot,npc)) return navigation.approach(npc,2);
            if (!DialogService.isInteractionAllowed(bot,npc)) return false;
            navigation.stop(); bot.setTarget(npc); npc.getController().onDialogRequest(bot); return true;
        }), () -> ENCOUNTER + 15);
    }
    private PlayerBotAdmaStronghold() {}
}
