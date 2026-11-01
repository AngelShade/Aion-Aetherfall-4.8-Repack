package com.aionemu.gameserver.questEngine.task.checker;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.utils.PositionUtil;

/**
 * @author ATracer, Neon
 */
public class CoordinateDestinationChecker extends DestinationChecker {

	protected final float x;
	protected final float y;
	protected final float z;
	private final float[][] additionalDestinations;

	public CoordinateDestinationChecker(Creature follower, float x, float y, float z) {
		this(follower, x, y, z, new float[0][]);
	}

	public CoordinateDestinationChecker(Creature follower, float x, float y, float z, float[]... additionalDestinations) {
		super(follower);
		this.x = x;
		this.y = y;
		this.z = z;
		this.additionalDestinations = additionalDestinations;
	}

	@Override
	public boolean check() {
		if (PositionUtil.isInRange(follower, x, y, z, 20))
			return true;
		for (float[] destination : additionalDestinations) {
			if (PositionUtil.isInRange(follower, destination[0], destination[1], destination[2], 20))
				return true;
		}
		return false;
	}
}
