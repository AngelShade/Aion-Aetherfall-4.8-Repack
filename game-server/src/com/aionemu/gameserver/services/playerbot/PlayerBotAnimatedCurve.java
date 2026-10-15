package com.aionemu.gameserver.services.playerbot;

/**
 * Samples immutable, authored Controller826 keys without choosing an animation clock or converting units.
 * Mathematical adaptation of ControllerTCB.cpp, TCBSpline.h and CryCommon/Cry_Quat.h in the primary
 * OpenFarCry1/Far-Cry-1-CryEngine1 revision 38da5286bc420aa716662763cbde398473abe893:
 * https://github.com/OpenFarCry1/Far-Cry-1-CryEngine1/tree/38da5286bc420aa716662763cbde398473abe893
 * Relative angle-axis keys accumulate in authoring order; TCB controls quaternion log/exp tangents.
 * Controller flags and unproved Bezier tangent conventions are rejected rather than approximated.
 */
final class PlayerBotAnimatedCurve {

	private final int kind;
	private final double[] times;
	private final double[][] keys;
	private final double[][] values;
	private final double[][] incoming;
	private final double[][] outgoing;

	PlayerBotAnimatedCurve(int kind, int flags, double secondsPerTick, double[][] keys) {
		if (kind != 6 && kind != 9 && kind != 10)
			throw new IllegalArgumentException("Unsupported native animation controller: " + kind);
		if (flags != 0)
			throw new IllegalArgumentException("Unsupported native animation controller flags: " + flags);
		if (!Double.isFinite(secondsPerTick) || secondsPerTick <= 0 || keys == null || keys.length == 0 || keys.length > 100000)
			throw new IllegalArgumentException("Invalid native animation timing or key count");
		this.kind = kind;
		this.keys = new double[keys.length][];
		times = new double[keys.length];
		values = new double[keys.length][];
		incoming = new double[keys.length][];
		outgoing = new double[keys.length][];
		for (int i = 0; i < keys.length; i++) {
			if (keys[i] == null || keys[i].length != (kind == 9 ? 9 : 10))
				throw new IllegalArgumentException("Invalid native animation key layout");
			this.keys[i] = keys[i].clone();
			double[] row = this.keys[i];
			for (double component : row)
				if (!Double.isFinite(component))
					throw new IllegalArgumentException("Nonfinite native animation key");
			for (int component = 1; component < row.length; component++)
				if (Math.abs(row[component]) > Float.MAX_VALUE)
					throw new IllegalArgumentException("Animation key exceeds native float range");
			double tick = row[0];
			if (tick != Math.rint(tick) || tick < Integer.MIN_VALUE || tick > Integer.MAX_VALUE)
				throw new IllegalArgumentException("Invalid native animation key tick");
			times[i] = tick * secondsPerTick;
			if (!Double.isFinite(times[i]) || i > 0 && times[i] <= times[i - 1])
				throw new IllegalArgumentException("Unordered native animation key times");
			if (kind != 6 && (row[parameterOffset() + 3] < 0 || row[parameterOffset() + 4] < 0))
				throw new IllegalArgumentException("Unsupported negative native animation easing");
			if (kind == 10) {
				// The native full-revolution branch has distinct winding semantics, not shortest-arc SQUAD.
				if (Math.abs(row[4]) >= 2 * Math.PI - 0.00002)
					throw new IllegalArgumentException("Unsupported native animation rotation winding");
				double half = row[4] * 0.5;
				double sine = Math.sin(half);
				double[] relative = normalize(new double[] {row[1] * sine, row[2] * sine, row[3] * sine, Math.cos(half)});
				values[i] = i == 0 ? relative : multiply(values[i - 1], relative);
			} else {
				values[i] = new double[] {row[1], row[2], row[3]};
			}
			incoming[i] = new double[values[i].length];
			outgoing[i] = new double[values[i].length];
		}
		if (kind == 6)
			validateConstantBezier();
		else if (kind == 9)
			prepareVectorTangents();
		else if (keys.length > 1)
			prepareQuaternionTangents();
		for (int i = 0; i < keys.length; i++) {
			requireFinite(values[i]);
			requireFinite(incoming[i]);
			requireFinite(outgoing[i]);
		}
	}

	double[] sample(double seconds) {
		if (!Double.isFinite(seconds))
			throw new IllegalArgumentException("Nonfinite native animation sample time");
		int last = times.length - 1;
		if (seconds <= times[0] || kind == 6)
			return values[0].clone();
		if (seconds >= times[last])
			return values[last].clone();
		int low = 0, high = last;
		while (high - low > 1) {
			int middle = (low + high) >>> 1;
			if (times[middle] <= seconds)
				low = middle;
			else
				high = middle;
		}
		double u = ease((seconds - times[low]) / (times[high] - times[low]),
			keys[low][parameterOffset() + 4], keys[high][parameterOffset() + 3]);
		if (kind == 10)
			return normalize(slerp(slerp(values[low], values[high], u), slerp(outgoing[low], incoming[high], u), 2 * u * (1 - u)));
		double square = u * u, cube = square * u;
		double[] result = new double[3];
		for (int axis = 0; axis < 3; axis++)
			result[axis] = (2 * cube - 3 * square + 1) * values[low][axis] + (3 * square - 2 * cube) * values[high][axis]
				+ (cube - 2 * square + u) * outgoing[low][axis] + (cube - square) * incoming[high][axis];
		requireFinite(result);
		return result;
	}

	private int parameterOffset() {
		return kind == 10 ? 5 : 4;
	}

	private void validateConstantBezier() {
		// All three exported CryBez3 tracks are constant scales with zero incoming/outgoing tangents.
		// Their exact constant value needs no assumption about slope units, handles or time scaling.
		for (double[] key : keys) {
			for (int axis = 0; axis < 3; axis++)
				if (key[axis + 1] != values[0][axis] || key[axis + 4] != 0 || key[axis + 7] != 0)
					throw new IllegalArgumentException("Unsupported varying native Bezier controller or tangent convention");
		}
	}

	private void prepareVectorTangents() {
		int last = keys.length - 1;
		if (last == 0)
			return;
		if (last == 1) {
			for (int axis = 0; axis < 3; axis++) {
				double difference = values[1][axis] - values[0][axis];
				outgoing[0][axis] = (1 - keys[0][4]) * difference;
				incoming[1][axis] = (1 - keys[1][4]) * difference;
			}
			return;
		}
		for (int i = 1; i < last; i++) {
			double tension = keys[i][4], continuity = keys[i][5], bias = keys[i][6];
			double before = 2 * (times[i] - times[i - 1]) / (times[i + 1] - times[i - 1]);
			double after = 2 * (times[i + 1] - times[i]) / (times[i + 1] - times[i - 1]);
			before += Math.abs(continuity) * (1 - before);
			after += Math.abs(continuity) * (1 - after);
			double a = 0.5 * (1 - tension) * (1 + bias), b = 0.5 * (1 - tension) * (1 - bias);
			for (int axis = 0; axis < 3; axis++) {
				double previous = values[i][axis] - values[i - 1][axis], next = values[i + 1][axis] - values[i][axis];
				incoming[i][axis] = before * (a * (1 - continuity) * previous + b * (1 + continuity) * next);
				outgoing[i][axis] = after * (a * (1 + continuity) * previous + b * (1 - continuity) * next);
			}
		}
		for (int axis = 0; axis < 3; axis++) {
			// CryAnimation's endpoint rule is also present in matching CryAnimation.dll, RVA1450f0..145169;
			// the different CryMovie endpoint expression must not replace it.
			outgoing[0][axis] = 1.5 * (1 - keys[0][4]) * (values[1][axis] - values[0][axis] - incoming[1][axis]);
			incoming[last][axis] = -1.5 * (1 - keys[last][4]) * (values[last - 1][axis] - values[last][axis] + outgoing[last - 1][axis]);
		}
	}

	private void prepareQuaternionTangents() {
		int last = keys.length - 1;
		for (int i = 0; i <= last; i++) {
			double[] previous = i > 0 ? logDifference(values[i - 1], values[i]) : logDifference(values[i], values[i + 1]);
			double[] next = i < last ? logDifference(values[i], values[i + 1]) : previous;
			double tension = keys[i][5], continuity = keys[i][6], bias = keys[i][7];
			double before = 1, after = 1;
			if (i > 0 && i < last) {
				before = 2 * (times[i] - times[i - 1]) / (times[i + 1] - times[i - 1]);
				after = 2 * (times[i + 1] - times[i]) / (times[i + 1] - times[i - 1]);
				before += Math.abs(continuity) * (1 - before);
				after += Math.abs(continuity) * (1 - after);
			}
			double weight = 0.5 * (1 - tension);
			double outPrevious = weight * (1 + continuity) * (1 + bias) * after;
			double outNext = weight * (1 - continuity) * (1 - bias) * after - 1;
			double inPrevious = 1 - weight * (1 - continuity) * (1 + bias) * before;
			double inNext = -weight * (1 + continuity) * (1 - bias) * before;
			double[] outLog = new double[3], inLog = new double[3];
			for (int axis = 0; axis < 3; axis++) {
				outLog[axis] = 0.5 * (outPrevious * previous[axis] + outNext * next[axis]);
				inLog[axis] = 0.5 * (inPrevious * previous[axis] + inNext * next[axis]);
			}
			outgoing[i] = multiply(values[i], exponential(outLog));
			incoming[i] = multiply(values[i], exponential(inLog));
		}
	}

	private static double ease(double u, double from, double to) {
		double sum = from + to;
		if (sum == 0 || u == 0 || u == 1)
			return u;
		if (sum > 1) {
			from /= sum;
			to /= sum;
		}
		double weight = 1 / (2 - from - to);
		if (u < from)
			return weight * u * u / from;
		if (u < 1 - to)
			return weight * (2 * u - from);
		return 1 - weight * (1 - u) * (1 - u) / to;
	}

	private static double[] logDifference(double[] from, double[] to) {
		double sign = dot(from, to) < 0 ? -1 : 1;
		double[] difference = multiply(new double[] {-from[0], -from[1], -from[2], from[3]},
			new double[] {sign * to[0], sign * to[1], sign * to[2], sign * to[3]});
		double length = Math.hypot(Math.hypot(difference[0], difference[1]), difference[2]);
		double factor = length > 1e-7 ? Math.atan2(length, difference[3]) / length : 0;
		return new double[] {difference[0] * factor, difference[1] * factor, difference[2] * factor};
	}

	private static double[] exponential(double[] logarithm) {
		double length = Math.hypot(Math.hypot(logarithm[0], logarithm[1]), logarithm[2]);
		double factor = length > 1e-4 ? Math.sin(length) / length : 1;
		return new double[] {logarithm[0] * factor, logarithm[1] * factor, logarithm[2] * factor,
			length > 1e-4 ? Math.cos(length) : 1 - length * length};
	}

	private static double[] slerp(double[] from, double[] to, double u) {
		double cosine = dot(from, to), sign = cosine < 0 ? -1 : 1;
		cosine = Math.abs(cosine);
		double a = 1 - u, b = u;
		if (cosine < 0.999) {
			double angle = Math.acos(Math.min(1, cosine)), denominator = Math.sin(angle);
			a = Math.sin((1 - u) * angle) / denominator;
			b = Math.sin(u * angle) / denominator;
		}
		double[] result = new double[4];
		for (int i = 0; i < 4; i++)
			result[i] = a * from[i] + b * sign * to[i];
		return result;
	}

	private static double[] multiply(double[] a, double[] b) {
		return new double[] {a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
			a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
			a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
			a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
	}

	private static double[] normalize(double[] quaternion) {
		double length = Math.sqrt(dot(quaternion, quaternion));
		if (!Double.isFinite(length) || length == 0)
			throw new IllegalArgumentException("Invalid native animation quaternion");
		for (int i = 0; i < 4; i++)
			quaternion[i] /= length;
		return quaternion;
	}

	private static void requireFinite(double[] value) {
		for (double component : value)
			if (!Double.isFinite(component))
				throw new IllegalArgumentException("Nonfinite native animation interpolation");
	}
}
