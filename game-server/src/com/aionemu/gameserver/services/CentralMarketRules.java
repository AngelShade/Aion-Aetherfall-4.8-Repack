package com.aionemu.gameserver.services;

import java.math.BigInteger;
import java.util.*;

/** Pure market arithmetic. Currency never passes through floating point. */
public final class CentralMarketRules {
	public static final long MAX_KINAH = 999_999_999_999_999L;
	public static final long HIGH_VALUE = 20_000_000_000L;
	public static final long REGISTRATION_DELAY = 15 * 60 * 1000L;
	public static final int MAX_ORDERS = 100;
	public static final long BASE_VOLUME = 50_000; // tenths of VT

	private CentralMarketRules() {}

	public static long maxQuantity(boolean stackable,long stackLimit) {
		return stackable ? Math.max(1,Math.min(1000,stackLimit)) : 1;
	}

	public static long total(long price, long count) {
		if (price <= 0 || count <= 0 || count > MAX_KINAH / price)
			throw new IllegalArgumentException("Invalid price or quantity.");
		return price * count;
	}

	public static long add(long balance, long amount) {
		if (amount < 0 || balance < 0 || balance > MAX_KINAH - amount)
			throw new IllegalArgumentException("Kinah limit reached.");
		return balance + amount;
	}

	public static long collect(long gross, boolean premium) {
		return collect(gross, premium ? 8450 : 6500);
	}

	/** @param returnBasisPoints share of the sale returned to the seller (10,000 = 100%) */
	public static long collect(long gross, int returnBasisPoints) {
		if (gross < 0 || gross > MAX_KINAH) throw new IllegalArgumentException("Invalid proceeds.");
		if (returnBasisPoints < 0 || returnBasisPoints > 10_000) throw new IllegalArgumentException("Invalid market return.");
		return BigInteger.valueOf(gross).multiply(BigInteger.valueOf(returnBasisPoints))
			.divide(BigInteger.valueOf(10_000)).longValueExact();
	}

	public static List<Long> ladder(long base, long floor, long ceiling) {
		long step = Math.max(1, base / 300); // 31 prices within +/-5%; spread stays below the best tax return
		TreeSet<Long> levels = new TreeSet<>();
		for (int i = -15; i <= 15; i++) levels.add(Math.max(floor, Math.min(ceiling, base + step * i)));
		return List.copyOf(levels);
	}

	public static long adjust(long base, long floor, long ceiling, long buys, long sells) {
		if (buys == sells) return base;
		long step = Math.max(1, base / 100);
		return Math.max(floor, Math.min(ceiling, base + (buys > sells ? step : -step)));
	}

	/** One willingness-to-pay draw: cheaper asks are reached more often, never per page request. */
	public static int demandIndex(int levels, int roll, int lowPercent, int highPercent) {
		if (levels < 1 || roll < 0 || roll >= 10_000) throw new IllegalArgumentException("Invalid demand draw.");
		int low = Math.clamp(lowPercent, 0, 100), high = Math.clamp(highPercent, 0, low);
		for (int i = levels - 1; i >= 0; i--) {
			int chance = levels == 1 ? low * 100 : low * 100 - (low - high) * 100 * i / (levels - 1);
			if (roll < chance) return i;
		}
		return -1;
	}

	public static long volume(String category) {
		return switch (category) {
			case "Weapons", "Armor" -> 100;
			case "Accessories" -> 50;
			case "Materials" -> 1;
			default -> 3;
		};
	}
}
