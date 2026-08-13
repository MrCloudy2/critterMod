package dev.rok.crittermod.data;

import java.util.Locale;

/**
 * One of the 37 Critter species catchable in the Critter Safari.
 *
 * @param name       exact in-game name as it appears in chat, e.g. {@code "Mantis Shrimp"}
 * @param biome      the biome this species spawns in
 * @param rarity     Hypixel rarity, used only for display ordering/colour
 * @param spawnQuota how many spawn per run, or {@code 0} where the species respawns
 *                   and no total exists
 */
public record Critter(String name, SafariBiome biome, Rarity rarity, int spawnQuota) {

	/** Respawning species: catching one is all there is to do. */
	Critter(String name, SafariBiome biome, Rarity rarity) {
		this(name, biome, rarity, 0);
	}

	/** True when a fixed number spawn per run, so "all of them" is a meaningful target. */
	public boolean hasQuota() {
		return spawnQuota > 0;
	}

	/**
	 * The bazaar product this species' shard trades as.
	 *
	 * <p>Derived rather than tabulated: every one of the 37 is {@code SHARD_} followed by
	 * the species name upper-cased with spaces underscored, checked against the live
	 * product list. A species Hypixel later names differently would simply have no price,
	 * which is what {@code 0} means everywhere this is used.
	 */
	public String bazaarId() {
		return "SHARD_" + name.toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	public enum Rarity {
		COMMON(0xFFFFFF),
		UNCOMMON(0x55FF55),
		RARE(0x5555FF),
		EPIC(0xAA00AA),
		LEGENDARY(0xFFAA00);

		private final int colour;

		Rarity(int colour) {
			this.colour = colour;
		}

		public int colour() {
			return colour;
		}
	}
}
