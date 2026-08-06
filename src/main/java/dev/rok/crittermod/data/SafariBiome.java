package dev.rok.crittermod.data;

/**
 * The four biomes of the Critter Safari. Every Critter species belongs to
 * exactly one of them, which is what makes "one biome per party member" a
 * meaningful way to split a run.
 */
public enum SafariBiome {
	FOREST("Forest", 0x55FF55),
	CAVERN("Cavern", 0xFFAA00),
	ICY("Icy", 0x55FFFF),
	HAUNTED("Haunted", 0xAA00AA);

	private final String displayName;
	private final int colour;

	SafariBiome(String displayName, int colour) {
		this.displayName = displayName;
		this.colour = colour;
	}

	public String displayName() {
		return displayName;
	}

	/** Name as it appears on the SkyBlock scoreboard, e.g. {@code "Forest Biome"}. */
	public String areaName() {
		return displayName + " Biome";
	}

	/** RGB used for HUD text, roughly matching the in-game biome colours. */
	public int colour() {
		return colour;
	}

	/** Resolves a scoreboard/tab area string such as {@code "⏣ Icy Biome"}. */
	public static SafariBiome fromAreaName(String area) {
		if (area == null) return null;
		for (SafariBiome biome : values()) {
			if (area.contains(biome.areaName())) return biome;
		}
		return null;
	}
}
