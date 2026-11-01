package com.aionemu.commons.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, reusable table of nonnegative integer weights. Weights are relative and need not add up to a percentage base. Each sample is
 * independent and with replacement; this class does not provide sampling without replacement, pity rules or persistence of committed outcomes.
 * Table order and weights are copied on construction. Outcome objects themselves are not copied.
 *
 * @param <T> the outcome type
 */
public final class WeightedRandom<T> {

	/**
	 * A non-null outcome with a nonnegative integer weight. Zero-weight entries remain visible in the policy but cannot be selected.
	 */
	public record Entry<T>(T value, long weight) {
		public Entry {
			Objects.requireNonNull(value, "value");
			if (weight < 0)
				throw new IllegalArgumentException("Weight must not be negative: " + weight);
		}
	}

	private final List<Entry<T>> entries;
	private final long[] cumulativeWeights;
	private final long totalWeight;

	private WeightedRandom(List<Entry<T>> entries) {
		this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
		cumulativeWeights = new long[this.entries.size()];
		long total = 0;
		for (int i = 0; i < this.entries.size(); i++) {
			try {
				total = Math.addExact(total, this.entries.get(i).weight());
			} catch (ArithmeticException e) {
				throw new IllegalArgumentException("Total weight exceeds Long.MAX_VALUE", e);
			}
			cumulativeWeights[i] = total;
		}
		if (total == 0)
			throw new IllegalArgumentException("At least one entry must have positive weight");
		totalWeight = total;
	}

	public static <T> WeightedRandom<T> of(List<Entry<T>> entries) {
		return new WeightedRandom<>(entries);
	}

	@SafeVarargs
	public static <T> WeightedRandom<T> of(Entry<T>... entries) {
		Objects.requireNonNull(entries, "entries");
		List<Entry<T>> copy = new ArrayList<>(entries.length);
		for (Entry<T> entry : entries)
			copy.add(entry);
		return new WeightedRandom<>(copy);
	}

	/**
	 * @return the immutable policy in its supplied order, including zero-weight entries
	 */
	public List<Entry<T>> entries() {
		return entries;
	}

	public long totalWeight() {
		return totalWeight;
	}

	/**
	 * Samples through the existing thread-local {@link Rnd} generator. No new generator or mutable policy state is introduced.
	 */
	public T sample() {
		return drawAt(Rnd.nextLong(totalWeight));
	}

	/**
	 * Resolves one deterministic zero-based offset in {@code [0, totalWeight())}. Each positive entry owns exactly {@code weight} consecutive
	 * offsets in table order. This supports exact boundary verification and replay when a caller has separately committed an offset.
	 *
	 * @throws IllegalArgumentException if the offset is outside the table
	 */
	public T drawAt(long offset) {
		if (offset < 0 || offset >= totalWeight)
			throw new IllegalArgumentException("Offset must be in [0, " + totalWeight + "): " + offset);
		int low = 0;
		int high = cumulativeWeights.length - 1;
		while (low < high) {
			int mid = low + (high - low) / 2;
			if (offset < cumulativeWeights[mid])
				high = mid;
			else
				low = mid + 1;
		}
		return entries.get(low).value();
	}
}
