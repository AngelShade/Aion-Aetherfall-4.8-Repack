package ai.instance.fireTemple;

import java.util.function.IntBinaryOperator;
import com.aionemu.commons.utils.Rnd;

/** Time/HP decisions independent of native combat and casting. */
public final class KromedeEncounter {
	private final IntBinaryOperator random;
	private int step;
	private boolean climaxDone;
	private boolean accelerated;
	private long verdictAt;
	private long stepAt;
	private int selected;

	public KromedeEncounter() { this(Rnd::get); }
	public KromedeEncounter(IntBinaryOperator random) { this.random = random; }

	public void reset() {
		step = 0; climaxDone = false; accelerated = false; verdictAt = 0; stepAt = 0; selected = 0;
	}

	public int next(long now, int hp) {
		if (!accelerated && hp <= 50) {
			accelerated = true;
			if (verdictAt > 0) verdictAt = Math.min(verdictAt, now + interval());
		}
		if (step == 2 && !climaxDone && hp <= 25) {
			climaxDone = true; step = 3; stepAt = now;
		}
		if (now < stepAt) return 0;
		selected = switch (step) {
			case 0 -> 17047;
			case 1 -> 16847;
			case 2 -> now >= verdictAt ? 16674 : 0;
			case 3, 5 -> 16674;
			case 4 -> 17056;
			default -> throw new IllegalStateException("Invalid encounter step " + step);
		};
		return selected;
	}

	public void complete(int skillId, long now, int hp) {
		// Use the selected step: HP may cross a threshold during a native cast.
		if (selected != skillId || skillId == 0) throw new IllegalStateException("Unexpected completed Kromede skill " + skillId);
		selected = 0;
		if (step == 0 || step == 1 || step == 3 || step == 4) step++;
		else if (step == 5) step = 2;
		stepAt = now + (step == 4 ? 2500 : 300);
		if (hp <= 50) accelerated = true;
		if (step == 2) verdictAt = now + interval();
	}

	private int interval() { return accelerated ? random.applyAsInt(23000, 27000) : random.applyAsInt(30000, 35000); }
	public boolean isSequence() { return step != 2; }
}
