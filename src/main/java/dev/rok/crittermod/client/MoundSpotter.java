package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Finds the Rockmite mounds, which are interaction entities rather than blocks.
 *
 * <p>They are not all the same size, so a band is matched rather than one shape. The
 * inspected mound was 0.70 x 0.50; the band around that is deliberately loose on the
 * assumption that mounds vary and tight enough to leave out the full-block props.
 * Interaction entities serve plenty of other purposes, so {@link #describeAll()} lists
 * every nearby one grouped by size — that is what the bounds should be set from, not
 * from a single reading.
 */
public final class MoundSpotter {

	/** Mounds vary; these bracket the one measured example with room either side. */
	private static final double MIN_WIDTH = 0.35;
	private static final double MAX_WIDTH = 1.10;
	private static final double MIN_HEIGHT = 0.25;
	private static final double MAX_HEIGHT = 0.95;
	private static final double SCAN_RADIUS = 64.0;

	/**
	 * Mounds are on the Cavern floor, which is below this. Hitbox size alone is weak
	 * evidence — plenty of props wear a squat interaction box — so anything higher up is
	 * something else, whatever it measures.
	 */
	private static final double MAX_Y = 65.0;

	private MoundSpotter() {
	}

	/** Interaction entities near the player whose hitbox matches a mound. */
	public static List<BlockPos> mounds() {
		Minecraft client = Minecraft.getInstance();
		List<BlockPos> found = new ArrayList<>();
		if (client.level == null || client.player == null) return found;

		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity.getType() != EntityType.INTERACTION) continue;
			if (entity.position().distanceToSqr(client.player.position()) > SCAN_RADIUS * SCAN_RADIUS) continue;
			if (entity.getY() > MAX_Y) continue;

			double w = entity.getBoundingBox().getXsize();
			double h = entity.getBoundingBox().getYsize();
			if (w < MIN_WIDTH || w > MAX_WIDTH) continue;
			if (h < MIN_HEIGHT || h > MAX_HEIGHT) continue;
			// Mounds sit squat on the floor; anything taller than it is wide is
			// something else wearing an interaction box.
			if (h > w + 0.1) continue;
			found.add(entity.blockPosition());
		}
		return found;
	}

	/**
	 * Nearby interaction entities grouped by hitbox size, commonest first.
	 *
	 * <p>Grouped rather than listed one by one: what matters is which sizes exist and
	 * how many of each, since that is what shows where the mounds sit and whether the
	 * band is picking up anything it should not.
	 */
	public static List<String> describeAll() {
		Minecraft client = Minecraft.getInstance();
		List<String> lines = new ArrayList<>();
		if (client.level == null || client.player == null) return lines;

		Map<String, Integer> bySize = new TreeMap<>();
		// Counted separately rather than filtered out: a size that only ever appears too
		// high up is exactly what the height rule is there to exclude, and seeing that
		// is the point of this listing.
		Map<String, Integer> lowEnough = new TreeMap<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity.getType() != EntityType.INTERACTION) continue;
			if (entity.position().distanceToSqr(client.player.position()) > SCAN_RADIUS * SCAN_RADIUS) continue;
			String size = "%.2f x %.2f".formatted(
				entity.getBoundingBox().getXsize(), entity.getBoundingBox().getYsize());
			bySize.merge(size, 1, Integer::sum);
			if (entity.getY() <= MAX_Y) lowEnough.merge(size, 1, Integer::sum);
		}
		bySize.entrySet().stream()
			.sorted((a, b) -> b.getValue() - a.getValue())
			.forEach(e -> {
				String[] parts = e.getKey().split(" x ");
				boolean matched = inBand(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
				int low = lowEnough.getOrDefault(e.getKey(), 0);
				String note = !matched ? ""
					: low == 0 ? "(all above y%.0f)".formatted(MAX_Y)
					: "<- counted as mound (%d of %d below y%.0f)".formatted(low, e.getValue(), MAX_Y);
				lines.add("  %-14s x%-3d %s".formatted(e.getKey(), e.getValue(), note));
			});
		return lines;
	}

	private static boolean inBand(double w, double h) {
		return w >= MIN_WIDTH && w <= MAX_WIDTH && h >= MIN_HEIGHT && h <= MAX_HEIGHT && h <= w + 0.1;
	}
}
