package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dao.PlayerAppearanceDAO;
import com.aionemu.gameserver.model.Gender;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PLAYER_INFO;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** Native cosmetic variation with the recruiting player's proportions retained. */
final class PlayerBotCompanionLooks {
    static PlayerAppearance create(Player owner, Gender gender, int character) {
        if (!PlayerBotConfig.GENERATED_RANDOM_LOOKS) return owner.getPlayerAppearance().copy();
        // Retain the complete, working native appearance as the proportion
        // template. Arbitrary detail bytes can deform the whole skeleton.
        // The catalog generator changes only native face/hair/colour choices.
        var base = owner.getPlayerAppearance().copy();
        return PlayerBotWorldLooks.create(base,owner.getRace(),gender,character);
    }

    static void recruited(Player owner, Player bot) {
        if (!PlayerBotConfig.GENERATED_RANDOM_LOOKS) return;
        try {
            int account=owner.getAccount().getId(),character=bot.getObjectId();
            var settings=PlayerBotMetadata.load(account,character,"looks",java.nio.file.Path.of("config/playerbots/looks-character-"+character+".properties"));
            if (!"4".equals(settings.getProperty("version"))) {
                var appearance = create(owner,bot.getGender(),character);
                if (!PlayerAppearanceDAO.store(character,appearance)) throw new IllegalStateException("Could not save companion appearance.");
                bot.setPlayerAppearance(appearance);
                settings.setProperty("account",Integer.toString(account));settings.setProperty("character",Integer.toString(character));settings.setProperty("version","4");
                PlayerBotMetadata.save(account,character,"looks",settings);
            }
        } catch (java.io.IOException error) { throw new IllegalStateException("Could not load companion appearance settings.",error); }
        matchHeight(owner,bot);
    }

    static void tick(PlayerBotSession session) {
        if (!PlayerBotConfig.GENERATED_RANDOM_LOOKS || session.world()) return;
        matchHeight(session.owner(),session.bot());
    }

    private static void matchHeight(Player owner,Player bot) {
        float height = owner.getPlayerAppearance().getHeightScale();
        if (Float.compare(height,bot.getPlayerAppearance().getHeightScale()) == 0) return;
        var appearance = bot.getPlayerAppearance().copy();
        appearance.setHeightScale(height);
        if (!PlayerAppearanceDAO.store(bot.getObjectId(),appearance))
            throw new IllegalStateException("Could not save companion height.");
        bot.setPlayerAppearance(appearance);
        if (bot.isSpawned()) PacketSendUtility.broadcastPacket(bot,new SM_PLAYER_INFO(bot));
    }

    private PlayerBotCompanionLooks() {}
}
