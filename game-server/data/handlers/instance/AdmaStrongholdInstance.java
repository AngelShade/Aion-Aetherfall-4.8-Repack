package instance;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.AdmaStrongholdHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.spawnengine.SpawnEngine;

/**
 * @author xTz, Tibald
 */
@InstanceID(320130000)
public class AdmaStrongholdInstance extends GeneralInstanceHandler implements AdmaStrongholdHandler {
    private java.util.List<SpawnTemplate> coffinSpawns = java.util.List.of();
    private SpawnTemplate boySpawn;
    private boolean destroyed;
    private Npc betrayer;
    private boolean betrayerDefeated;

    private boolean registered() {
        return !destroyed && World.getInstance().getWorldMap(instance.getMapId()).getWorldMapInstance(instance.getInstanceId()) == instance;
    }

    @Override public synchronized void resetCoffins(Npc boss) {
        if (!registered() || boss.isDead() || boss.getLifeStats().isAboutToDie() || !boss.isSpawned()) return;
        instance.getNpcs(280942,280950,281055,281056,281057,281058)
            .forEach(npc -> npc.getController().deleteIfAliveOrCancelRespawn());
        for (var spawn : coffinSpawns) SpawnEngine.spawnObject(spawn, instance.getInstanceId());
    }

    @Override public synchronized Npc revealBetrayer(Npc boy) {
        if (!registered() || betrayerDefeated || boy != betrayer || boy.getNpcId() != 214700 || !boy.isSpawned()) return null;
        Npc real = (Npc) spawn(214701,boy.getX(),boy.getY(),boy.getZ(),boy.getHeading());
        betrayer = real;
        return real;
    }

    @Override public synchronized void onDie(Npc npc) {
        if (npc == betrayer && npc.getNpcId() == 214701) betrayerDefeated = true;
        super.onDie(npc);
    }

    @Override public synchronized void resetBetrayer(Npc real) {
        if (!registered() || betrayerDefeated || real != betrayer || boySpawn == null || real.isDead()
            || real.getLifeStats().isAboutToDie() || !real.isSpawned() || real.getWorldMapInstance() != instance) return;
        boolean aliveBoy = instance.getNpcs(214700).stream().anyMatch(npc -> npc.isSpawned() && !npc.isDead());
        if (!aliveBoy) betrayer = (Npc) SpawnEngine.spawnObject(boySpawn, instance.getInstanceId());
        real.getController().deleteIfAliveOrCancelRespawn();
    }

    @Override public synchronized void onInstanceDestroy() {
        destroyed = true; boySpawn = null; betrayer = null; coffinSpawns = java.util.List.of(); super.onInstanceDestroy();
    }

	public AdmaStrongholdInstance(WorldMapInstance instance) {
		super(instance);
	}

    @Override
    public void handleUseItemFinish(Player player, Npc npc) {
        int skill = switch (npc.getNpcId()) { case 700396 -> 18464; case 700397 -> 18465; default -> 0; };
        if (skill == 0 || player.isDead() || !npc.isSpawned() || npc.isDead()
            || player.getWorldMapInstance() != instance || !PositionUtil.isInTalkRange(player, npc)) return;
        // Real native power100/effect-ID cleanses; the use channel and skill target checks remain authoritative.
        SkillEngine.getInstance().getSkill(npc, skill, 1, npc).useSkill();
    }

	@Override
	public void onInstanceCreate() {
        coffinSpawns = instance.getNpcs(280942,280950,281055,281056,281057,281058).stream().map(Npc::getSpawn).toList();
        betrayer = instance.getNpcs(214700).stream().findFirst().orElse(null);
        boySpawn = betrayer == null ? null : betrayer.getSpawn();
		if (Rnd.nextBoolean()) {
			switch (Rnd.get(1, 3)) {
				case 1 -> spawn(205224, 477.5849f, 398.20898f, 187.49918f, (byte) 50);
				case 2 -> spawn(205224, 347.76538f, 559.4453f, 180.33097f, (byte) 105);
				case 3 -> spawn(205224, 528.25116f, 555.0943f, 189.49f, (byte) 78);
			}
		}
	}
}
