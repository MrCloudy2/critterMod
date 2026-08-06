package dev.rok.crittermod.data;

/**
 * One of the 37 Critter species catchable in the Critter Safari.
 *
 * @param name   exact in-game name as it appears in chat, e.g. {@code "Mantis Shrimp"}
 * @param biome  the biome this species spawns in
 * @param rarity Hypixel rarity, used only for display ordering/colour
 */
public record Critter(String name, SafariBiome biome, Rarity rarity) {

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
