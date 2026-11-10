package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.model.Gender;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance;

/** Race and gender matched creation looks for paid recruits. The native appearance row owns the result. */
final class PlayerBotTicketLooks {

    private static final Logger LOG = LoggerFactory.getLogger(PlayerBotTicketLooks.class);
    private static final Path PRESETS = Path.of("config/playerbots/look-presets.txt");

    private record Kind(Race race, Gender gender) {}

    private static final class Catalogue {
        static final Map<Kind, List<PlayerAppearance>> LOOKS = load();
    }

    /** A paid recruit is its own person: its gender comes from its reserved identity, not from its owner. */
    static Gender gender(int characterId) {
        return new Random(0x47454E444552L ^ (long) characterId * 0x9E3779B97F4A7C15L).nextBoolean() ? Gender.FEMALE : Gender.MALE;
    }

    static PlayerAppearance create(Race race, Gender gender, int characterId) {
        var looks = Catalogue.LOOKS.get(new Kind(race, gender));
        if (looks == null || looks.isEmpty())
            return PlayerBotWorldLooks.create(PlayerBotWorldLooks.neutral(), race, gender, characterId);
        var random = new Random(0x5449434B45544CL ^ (long) characterId * 0x9E3779B97F4A7C15L);
        return looks.get(random.nextInt(looks.size())).copy();
    }

    private static Map<Kind, List<PlayerAppearance>> load() {
        var looks = new HashMap<Kind, List<PlayerAppearance>>();
        try {
            for (String line : Files.readAllLines(PRESETS)) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                var columns = line.split("\\s+");
                if (columns.length != 12) throw new IllegalArgumentException("Expected 12 preset fields");
                var kind = new Kind(Race.valueOf(columns[0]), Gender.valueOf(columns[1]));
                if (kind.race() != Race.ELYOS && kind.race() != Race.ASMODIANS)
                    throw new IllegalArgumentException("Preset race must be playable");
                var appearance = new PlayerAppearance();
                appearance.setFace(unsigned(columns[2], 255));
                appearance.setHair(unsigned(columns[3], 255));
                appearance.setDeco(unsigned(columns[4], 255));
                appearance.setTattoo(unsigned(columns[5], 255));
                appearance.setSkinRGB(unsigned(columns[6], 0xFFFFFF));
                appearance.setHairRGB(unsigned(columns[7], 0xFFFFFF));
                appearance.setLipRGB(unsigned(columns[8], 0xFFFFFF));
                appearance.setEyeRGB(unsigned(columns[9], 0xFFFFFF));
                float height = Float.parseFloat(columns[10]);
                if (!(height >= 0.8f && height <= 1.25f)) throw new IllegalArgumentException("Invalid preset height");
                appearance.setHeightScale(height);
                applyDetail(appearance, HexFormat.of().parseHex(columns[11]));
                looks.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(appearance);
            }
            if (looks.size() != 4) throw new IllegalArgumentException("Missing player race and gender presets");
            looks.replaceAll((kind, values) -> List.copyOf(values));
            return Map.copyOf(looks);
        } catch (IOException | IllegalArgumentException e) {
            LOG.warn("Could not load ticket recruit looks from {}. Using the native world bot appearance generator.", PRESETS, e);
            return Map.of();
        }
    }

    private static int unsigned(String value, int max) {
        int number = Integer.parseInt(value);
        if (number < 0 || number > max) throw new IllegalArgumentException("Preset value out of range");
        return number;
    }

    private static void applyDetail(PlayerAppearance appearance, byte[] detail) {
        // The custom preset detail starts at JawLine in AbstractCharacterEditPacket.
        // Slots 37 and 42 are reserved. Face contour and expression are outside this block.
        if (detail.length != 43 || detail[37] != 0 || detail[42] != 0)
            throw new IllegalArgumentException("Invalid preset detail layout");
        appearance.setJawLine(Byte.toUnsignedInt(detail[0]));
        appearance.setForehead(Byte.toUnsignedInt(detail[1]));
        appearance.setEyeHeight(Byte.toUnsignedInt(detail[2]));
        appearance.setEyeSpace(Byte.toUnsignedInt(detail[3]));
        appearance.setEyeWidth(Byte.toUnsignedInt(detail[4]));
        appearance.setEyeSize(Byte.toUnsignedInt(detail[5]));
        appearance.setEyeShape(Byte.toUnsignedInt(detail[6]));
        appearance.setEyeAngle(Byte.toUnsignedInt(detail[7]));
        appearance.setBrowHeight(Byte.toUnsignedInt(detail[8]));
        appearance.setBrowAngle(Byte.toUnsignedInt(detail[9]));
        appearance.setBrowShape(Byte.toUnsignedInt(detail[10]));
        appearance.setNose(Byte.toUnsignedInt(detail[11]));
        appearance.setNoseBridge(Byte.toUnsignedInt(detail[12]));
        appearance.setNoseWidth(Byte.toUnsignedInt(detail[13]));
        appearance.setNoseTip(Byte.toUnsignedInt(detail[14]));
        appearance.setCheek(Byte.toUnsignedInt(detail[15]));
        appearance.setLipHeight(Byte.toUnsignedInt(detail[16]));
        appearance.setMouthSize(Byte.toUnsignedInt(detail[17]));
        appearance.setLipSize(Byte.toUnsignedInt(detail[18]));
        appearance.setSmile(Byte.toUnsignedInt(detail[19]));
        appearance.setLipShape(Byte.toUnsignedInt(detail[20]));
        appearance.setChinHeight(Byte.toUnsignedInt(detail[21]));
        appearance.setCheekBones(Byte.toUnsignedInt(detail[22]));
        appearance.setEarShape(Byte.toUnsignedInt(detail[23]));
        appearance.setHeadSize(Byte.toUnsignedInt(detail[24]));
        appearance.setNeck(Byte.toUnsignedInt(detail[25]));
        appearance.setNeckLength(Byte.toUnsignedInt(detail[26]));
        appearance.setShoulderSize(Byte.toUnsignedInt(detail[27]));
        appearance.setTorso(Byte.toUnsignedInt(detail[28]));
        appearance.setChest(Byte.toUnsignedInt(detail[29]));
        appearance.setWaist(Byte.toUnsignedInt(detail[30]));
        appearance.setHips(Byte.toUnsignedInt(detail[31]));
        appearance.setArmThickness(Byte.toUnsignedInt(detail[32]));
        appearance.setHandSize(Byte.toUnsignedInt(detail[33]));
        appearance.setLegThickness(Byte.toUnsignedInt(detail[34]));
        appearance.setFootSize(Byte.toUnsignedInt(detail[35]));
        appearance.setFacialRate(Byte.toUnsignedInt(detail[36]));
        appearance.setArmLength(Byte.toUnsignedInt(detail[38]));
        appearance.setLegLength(Byte.toUnsignedInt(detail[39]));
        appearance.setShoulders(Byte.toUnsignedInt(detail[40]));
        appearance.setFaceShape(Byte.toUnsignedInt(detail[41]));
    }

    private PlayerBotTicketLooks() {}
}
