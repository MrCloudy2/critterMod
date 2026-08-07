package dev.rok.crittermod.client;

import dev.rok.crittermod.CritterMod;
import dev.rok.crittermod.data.SafariBiome;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Maps a position on the Safari island to its biome, using a precomputed
 * node-to-biome table.
 *
 * <p>The Safari's biomes are not convex regions — Forest and Haunted interleave
 * around z≈0, and the cave sections fold back over each other — so classifying by
 * nearest biome centre is wrong for a few percent of the map. SkyHanni resolves this
 * by walking its island path graph: find the graph node nearest the player, then
 * Dijkstra along the edges to the nearest node tagged with an area name.
 *
 * <p>That whole search is position-independent, so it was run offline once over
 * SkyHanni's {@code SAFARI.json} graph (1,327 nodes, 68 area tags) and collapsed into
 * {@code safari_areas.txt}: one {@code x y z biome} row per node. At runtime this is
 * just a nearest-node lookup, which reproduces SkyHanni's answer exactly without
 * needing the graph, the edges, or SkyHanni itself installed.
 */
public final class SafariAreaMap {

	private static final String RESOURCE = "/assets/crittermod/safari_areas.txt";
	/** Past this distance from every known node, the player is not on the Safari map. */
	private static final double MAX_NODE_DISTANCE = 40.0;

	private static int[] xs;
	private static int[] ys;
	private static int[] zs;
	private static byte[] areas;
	private static boolean loaded;

	private SafariAreaMap() {
	}

	private static synchronized void load() {
		if (loaded) return;
		loaded = true;

		try (InputStream in = SafariAreaMap.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				CritterMod.LOGGER.error("Missing {}; position-based biome detection disabled", RESOURCE);
				return;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				var lines = reader.lines().filter(l -> !l.isBlank()).toList();
				xs = new int[lines.size()];
				ys = new int[lines.size()];
				zs = new int[lines.size()];
				areas = new byte[lines.size()];
				for (int i = 0; i < lines.size(); i++) {
					String[] parts = lines.get(i).split(" ");
					xs[i] = Integer.parseInt(parts[0]);
					ys[i] = Integer.parseInt(parts[1]);
					zs[i] = Integer.parseInt(parts[2]);
					areas[i] = Byte.parseByte(parts[3]);
				}
				CritterMod.LOGGER.info("Loaded {} Safari area nodes", lines.size());
			}
		} catch (IOException | RuntimeException e) {
			CritterMod.LOGGER.error("Could not read {}", RESOURCE, e);
			xs = null;
		}
	}

	/**
	 * The biome at a position, or {@code null} when the position is outside the Safari
	 * or inside an unnamed area such as the entrance hub.
	 */
	public static SafariBiome biomeAt(double x, double y, double z) {
		load();
		if (xs == null || xs.length == 0) return null;

		int best = -1;
		double bestDistanceSq = Double.MAX_VALUE;
		for (int i = 0; i < xs.length; i++) {
			double dx = x - xs[i];
			double dy = y - ys[i];
			double dz = z - zs[i];
			double distanceSq = dx * dx + dy * dy + dz * dz;
			if (distanceSq < bestDistanceSq) {
				bestDistanceSq = distanceSq;
				best = i;
			}
		}

		if (best < 0 || bestDistanceSq > MAX_NODE_DISTANCE * MAX_NODE_DISTANCE) return null;
		return fromIndex(areas[best]);
	}

	/** Distance to the nearest known node, for {@code /critters debug}. */
	public static double distanceToNearestNode(double x, double y, double z) {
		load();
		if (xs == null || xs.length == 0) return Double.NaN;

		double bestDistanceSq = Double.MAX_VALUE;
		for (int i = 0; i < xs.length; i++) {
			double dx = x - xs[i];
			double dy = y - ys[i];
			double dz = z - zs[i];
			bestDistanceSq = Math.min(bestDistanceSq, dx * dx + dy * dy + dz * dz);
		}
		return Math.sqrt(bestDistanceSq);
	}

	public static int nodeCount() {
		load();
		return xs == null ? 0 : xs.length;
	}

	/** Index 0 is {@code no_area}; 1-4 match the generator's biome ordering. */
	private static SafariBiome fromIndex(byte index) {
		return switch (index) {
			case 1 -> SafariBiome.FOREST;
			case 2 -> SafariBiome.CAVERN;
			case 3 -> SafariBiome.ICY;
			case 4 -> SafariBiome.HAUNTED;
			default -> null;
		};
	}
}
