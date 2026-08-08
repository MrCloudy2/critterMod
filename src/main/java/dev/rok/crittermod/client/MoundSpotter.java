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
 * <p>Size alone does not identify one. The band here brackets the inspected 0.70 x 0.50
 * mound loosely, and plenty of other things wear an interaction box — the breakable
 * fish that come down the waterfalls are real entities with a box drawn around them,
 * and their boxes are bigger. Three rules together do the work:
 *
 * <ul>
 * <li>on the Cavern floor, below y 65;
 * <li>centred on a block, since a mound is placed on the map and a falling fish is at
 *     whatever fraction of a block it has reached;
 * <li>with nothing inside it, since a mound is only the box while a fish is a mob the
 *     box is drawn around.
 * </ul>
 *
 * <p>{@link #describeAll()} lists every nearby interaction box grouped by size and says
 * how many of each pass each rule — that is what the bounds should be set from, not a
 * single reading.
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

	/** How far off a block's centre the box may sit and still count as placed on it. */
	private static final double CENTRE_TOLERANCE = 0.05;
	/** How close a creature has to be to count as being inside a box rather than near it. */
	private static final double INSIDE_RADIUS = 0.35;
	private static final double INSIDE_HEIGHT = 1.0;

	private MoundSpotter() {
	}

	/** Both the HUD and the waypoints ask every frame; the answer changes far slower. */
	private static final long CACHE_MILLIS = 500;
	private static List<BlockPos> cached = List.of();
	private static long cachedAt;

	/** Interaction entities near the player whose hitbox matches a mound. */
	public static List<BlockPos> mounds() {
		long now = System.currentTimeMillis();
		if (now - cachedAt < CACHE_MILLIS) return cached;
		cachedAt = now;
		cached = scan();
		return cached;
	}

	private static List<BlockPos> scan() {
		Minecraft client = Minecraft.getInstance();
		List<BlockPos> found = new ArrayList<>();
		if (client.level == null || client.player == null) return found;

		List<Entity> candidates = new ArrayList<>();
		List<Entity> creatures = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity.position().distanceToSqr(client.player.position()) > SCAN_RADIUS * SCAN_RADIUS) continue;
			if (entity.getType() != EntityType.INTERACTION) {
				if (isCreature(entity)) creatures.add(entity);
				continue;
			}
			if (entity.getY() > MAX_Y) continue;
			if (!inBand(entity.getBoundingBox().getXsize(), entity.getBoundingBox().getYsize())) continue;
			if (!isBlockCentred(entity)) continue;
			candidates.add(entity);
		}

		for (Entity candidate : candidates) {
			if (wrapsACreature(candidate, creatures)) continue;
			found.add(candidate.blockPosition());
		}
		return found;
	}

	/**
	 * Whether the box is centred on a block.
	 *
	 * <p>A mound is placed on the map, so its box sits at a block's centre every time.
	 * The breakable fish that fall down the waterfalls also come wrapped in interaction
	 * boxes, and a falling entity is at whatever fraction of a block it has reached —
	 * so this separates the two without needing to know what a fish is.
	 */
	private static boolean isBlockCentred(Entity entity) {
		return offsetFromCentre(entity.getX()) < CENTRE_TOLERANCE
			&& offsetFromCentre(entity.getZ()) < CENTRE_TOLERANCE;
	}

	private static double offsetFromCentre(double coordinate) {
		return Math.abs(coordinate - (Math.floor(coordinate) + 0.5));
	}

	/**
	 * Whether the box has a creature inside it.
	 *
	 * <p>The fish are real entities that an interaction box is drawn around; a mound is
	 * only the box. So anything with a mob sitting in it is not a mound — which also
	 * catches a fish that has landed and stopped moving, where being block-centred alone
	 * could be a coincidence.
	 */
	private static boolean wrapsACreature(Entity box, List<Entity> creatures) {
		for (Entity creature : creatures) {
			// Tight enough that a critter walking past a mound does not hide it: the
			// creature has to be essentially inside the box.
			if (Math.abs(creature.getX() - box.getX()) > INSIDE_RADIUS) continue;
			if (Math.abs(creature.getZ() - box.getZ()) > INSIDE_RADIUS) continue;
			if (Math.abs(creature.getY() - box.getY()) > INSIDE_HEIGHT) continue;
			return true;
		}
		return false;
	}

	/** Excludes the scaffolding entities Hypixel builds its props out of. */
	private static boolean isCreature(Entity entity) {
		EntityType<?> type = entity.getType();
		return type != EntityType.ARMOR_STAND
			&& type != EntityType.ITEM_DISPLAY
			&& type != EntityType.BLOCK_DISPLAY
			&& type != EntityType.TEXT_DISPLAY
			&& type != EntityType.PLAYER
			&& type != EntityType.ITEM;
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
		// Each rule is counted separately rather than filtered out: a size that only ever
		// appears too high up, off a block centre, or wrapped around a mob is exactly
		// what those rules are there to exclude, and seeing that is the point of this.
		Map<String, Integer> lowEnough = new TreeMap<>();
		Map<String, Integer> centred = new TreeMap<>();
		Map<String, Integer> counted = new TreeMap<>();

		List<Entity> creatures = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity.getType() != EntityType.INTERACTION && isCreature(entity)) creatures.add(entity);
		}

		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity.getType() != EntityType.INTERACTION) continue;
			if (entity.position().distanceToSqr(client.player.position()) > SCAN_RADIUS * SCAN_RADIUS) continue;
			String size = "%.2f x %.2f".formatted(
				entity.getBoundingBox().getXsize(), entity.getBoundingBox().getYsize());
			bySize.merge(size, 1, Integer::sum);
			if (entity.getY() <= MAX_Y) lowEnough.merge(size, 1, Integer::sum);
			if (isBlockCentred(entity)) centred.merge(size, 1, Integer::sum);
			if (entity.getY() <= MAX_Y && isBlockCentred(entity) && !wrapsACreature(entity, creatures)) {
				counted.merge(size, 1, Integer::sum);
			}
		}

		bySize.entrySet().stream()
			.sorted((a, b) -> b.getValue() - a.getValue())
			.forEach(e -> {
				String[] parts = e.getKey().split(" x ");
				boolean matched = inBand(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
				String note = matched
					? "<- mound-sized: %d counted (low %d, centred %d, of %d)".formatted(
						counted.getOrDefault(e.getKey(), 0), lowEnough.getOrDefault(e.getKey(), 0),
						centred.getOrDefault(e.getKey(), 0), e.getValue())
					: "";
				lines.add("  %-14s x%-3d %s".formatted(e.getKey(), e.getValue(), note));
			});
		return lines;
	}

	private static boolean inBand(double w, double h) {
		return w >= MIN_WIDTH && w <= MAX_WIDTH && h >= MIN_HEIGHT && h <= MAX_HEIGHT && h <= w + 0.1;
	}
}
