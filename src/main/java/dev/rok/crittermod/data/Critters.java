package dev.rok.crittermod.data;

import dev.rok.crittermod.data.Critter.Rarity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The complete Critterdex roster (37 species), grouped by biome.
 *
 * <p>Some species spawn a fixed number of times per run, so catching one is not the
 * same as clearing them. Those carry a quota; everything else respawns and has none.
 * The quotas were read off 25 substantial replayed runs, where the per-run counts pile
 * up on a single value — Gemzie sat on exactly 3 in 21 of 24 runs, Billygoat on 1 in
 * 21 of 23, Hideyho on 1 in 20 of 22. Species without a quota show no such ceiling:
 * Honeybug ranged from 1 to 13.
 *
 * <p>Species names are matched against chat text, so the longest names must be
 * tried first — "Mantis Shrimp" would otherwise never match if a shorter name
 * were a prefix of it. {@link #findIn(String)} handles that ordering.
 */
public final class Critters {

	private static final List<Critter> ALL = List.of(
		// --- Forest (9) ---
		new Critter("Foxtrot", SafariBiome.FOREST, Rarity.COMMON),
		new Critter("Bluebird", SafariBiome.FOREST, Rarity.UNCOMMON),
		new Critter("Honeybug", SafariBiome.FOREST, Rarity.UNCOMMON),
		new Critter("Treefrog", SafariBiome.FOREST, Rarity.UNCOMMON),
		new Critter("Woodchucker", SafariBiome.FOREST, Rarity.UNCOMMON),
		new Critter("Fluffling", SafariBiome.FOREST, Rarity.RARE),
		new Critter("Hideonfloor", SafariBiome.FOREST, Rarity.RARE),
		new Critter("Parakeet", SafariBiome.FOREST, Rarity.RARE),
		new Critter("Macaw", SafariBiome.FOREST, Rarity.LEGENDARY, 2),

		// --- Cavern (9) ---
		new Critter("Cavernfish", SafariBiome.CAVERN, Rarity.COMMON),
		new Critter("Flitter", SafariBiome.CAVERN, Rarity.COMMON),
		new Critter("Shyworm", SafariBiome.CAVERN, Rarity.COMMON),
		new Critter("Driftling", SafariBiome.CAVERN, Rarity.UNCOMMON),
		new Critter("Chuckwalla", SafariBiome.CAVERN, Rarity.RARE),
		new Critter("Rockmite", SafariBiome.CAVERN, Rarity.RARE),
		new Critter("Scrappy", SafariBiome.CAVERN, Rarity.RARE),
		new Critter("Snoozle", SafariBiome.CAVERN, Rarity.RARE),
		new Critter("Gemzie", SafariBiome.CAVERN, Rarity.EPIC, 3),

		// --- Icy (9) ---
		new Critter("Strongarm", SafariBiome.ICY, Rarity.COMMON),
		new Critter("Tepid", SafariBiome.ICY, Rarity.COMMON),
		new Critter("Polaris", SafariBiome.ICY, Rarity.UNCOMMON),
		new Critter("Shuddersquid", SafariBiome.ICY, Rarity.UNCOMMON),
		new Critter("Billygoat", SafariBiome.ICY, Rarity.RARE, 1),
		new Critter("Mantis Shrimp", SafariBiome.ICY, Rarity.RARE),
		new Critter("Nozzlenose", SafariBiome.ICY, Rarity.RARE),
		new Critter("Troodon", SafariBiome.ICY, Rarity.RARE, 3),
		new Critter("Wumpa", SafariBiome.ICY, Rarity.LEGENDARY, 1),

		// --- Haunted (10) ---
		new Critter("Areita", SafariBiome.HAUNTED, Rarity.UNCOMMON),
		new Critter("Bloodbat", SafariBiome.HAUNTED, Rarity.UNCOMMON),
		new Critter("Duplico", SafariBiome.HAUNTED, Rarity.UNCOMMON),
		new Critter("Gazer", SafariBiome.HAUNTED, Rarity.UNCOMMON, 2),
		new Critter("Litterbug", SafariBiome.HAUNTED, Rarity.UNCOMMON),
		new Critter("Solsnatcher", SafariBiome.HAUNTED, Rarity.UNCOMMON),
		new Critter("Gimmiegold", SafariBiome.HAUNTED, Rarity.RARE),
		new Critter("Hideonwall", SafariBiome.HAUNTED, Rarity.RARE),
		new Critter("Hideyho", SafariBiome.HAUNTED, Rarity.RARE, 1),
		new Critter("Doomspiral", SafariBiome.HAUNTED, Rarity.LEGENDARY, 1)
	);

	private static final Map<String, Critter> BY_NAME = new LinkedHashMap<>();
	private static final Map<SafariBiome, List<Critter>> BY_BIOME = new EnumMap<>(SafariBiome.class);

	/** Species ordered longest-name-first, so substring matching never mis-resolves. */
	private static final List<Critter> BY_NAME_LENGTH_DESC;

	static {
		for (Critter critter : ALL) {
			BY_NAME.put(critter.name(), critter);
			BY_BIOME.computeIfAbsent(critter.biome(), b -> new ArrayList<>()).add(critter);
		}
		for (SafariBiome biome : SafariBiome.values()) {
			BY_BIOME.putIfAbsent(biome, List.of());
		}

		List<Critter> byLength = new ArrayList<>(ALL);
		byLength.sort(Comparator.comparingInt((Critter c) -> c.name().length()).reversed());
		BY_NAME_LENGTH_DESC = List.copyOf(byLength);
	}

	/**
	 * The Forest legendary. It only appears via the Birdfeeder and is not guaranteed
	 * to show up in a run, so "everything but this" is a milestone worth its own alert.
	 */
	public static final Critter MACAW = BY_NAME.get("Macaw");

	private Critters() {
	}

	public static List<Critter> all() {
		return ALL;
	}

	/** Total species count — 37. */
	public static int total() {
		return ALL.size();
	}

	public static List<Critter> inBiome(SafariBiome biome) {
		return BY_BIOME.get(biome);
	}

	public static int totalIn(SafariBiome biome) {
		return BY_BIOME.get(biome).size();
	}

	public static Critter byName(String name) {
		return BY_NAME.get(name);
	}

	/**
	 * Finds the species named somewhere inside a chat line.
	 *
	 * <p>Matching by roster lookup rather than by a full-sentence regex means new
	 * or unseen message wordings (sparkling catches, future capture methods) still
	 * resolve correctly as long as the species name appears verbatim.
	 *
	 * @return the species, or {@code null} if the line names none
	 */
	public static Critter findIn(String line) {
		for (Critter critter : BY_NAME_LENGTH_DESC) {
			if (line.contains(critter.name())) return critter;
		}
		return null;
	}
}
