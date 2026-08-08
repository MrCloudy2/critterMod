package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.waypoints.ClientWaypointManager;
import net.minecraft.core.Vec3i;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAssets;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Marks the places worth walking to: unbroken Cavern walls, unpunched bee nests, and
 * the Hunter NPCs holding an offer.
 *
 * <p>Built on Minecraft's own waypoint system rather than a renderer mixin. 26.x ships
 * {@code ClientWaypointManager}, and both it and {@link TrackedWaypoint#setPosition}
 * are public, so a client can register waypoints and let vanilla draw them on the
 * locator bar. That is worth preferring to SkyHanni's approach here: SkyHanni injects
 * into {@code LevelRenderer} with MixinExtras because it needs arbitrary world geometry
 * — boxes, beams, lines — and because much of it predates this API. Plain position
 * markers need none of that, and a bad injection crashes on world load rather than
 * merely rendering wrong.
 *
 * <p>Waypoints are keyed by a UUID derived from what they mark, so the same wall keeps
 * the same waypoint across refreshes instead of piling up duplicates.
 */
public final class WaypointManager {

	private static final int REFRESH_INTERVAL_TICKS = 20;

	/** Currently registered, by derived id, so they can be updated and removed again. */
	private static final Map<UUID, TrackedWaypoint> active = new LinkedHashMap<>();

	private static int ticks;

	private WaypointManager() {
	}

	public static void tick() {
		if (++ticks < REFRESH_INTERVAL_TICKS) return;
		ticks = 0;

		ClientWaypointManager manager = manager();
		if (manager == null) return;

		if (!ConfigManager.get().display.waypoints || !AreaDetector.inSafari()) {
			clear();
			return;
		}

		Map<UUID, Vec3i> wanted = new HashMap<>();
		Map<UUID, Integer> colours = new HashMap<>();

		for (WallTracker.Wall wall : WallTracker.walls()) {
			if (wall.state() != WallTracker.State.INTACT) continue;
			UUID id = idFor("wall", wall.pos().getX(), wall.pos().getY(), wall.pos().getZ());
			wanted.put(id, wall.pos());
			colours.put(id, SafariBiome.CAVERN.colour());
		}

		for (NestTracker.Nest nest : NestTracker.nests()) {
			if (!nest.unpunched()) continue;
			UUID id = idFor("nest", nest.pos().getX(), nest.pos().getY(), nest.pos().getZ());
			wanted.put(id, nest.pos());
			colours.put(id, SafariBiome.FOREST.colour());
		}

		for (TraderWatch.Trade trade : TraderWatch.found()) {
			TraderWatch.Spot spot = trade.spot();
			if (spot == null) continue;
			UUID id = idFor("trade", spot.x(), spot.y(), spot.z());
			wanted.put(id, new Vec3i(spot.x(), spot.y(), spot.z()));
			colours.put(id, 0xFFAA00);
		}

		// Drop anything no longer wanted before adding, so a wall broken since the last
		// pass stops being marked immediately.
		active.keySet().removeIf(id -> {
			if (wanted.containsKey(id)) return false;
			manager.untrackWaypoint(active.get(id));
			return true;
		});

		for (Map.Entry<UUID, Vec3i> entry : wanted.entrySet()) {
			if (active.containsKey(entry.getKey())) continue;
			Waypoint.Icon icon = new Waypoint.Icon();
			icon.style = WaypointStyleAssets.DEFAULT;
			icon.color = Optional.of(colours.get(entry.getKey()));
			TrackedWaypoint waypoint = TrackedWaypoint.setPosition(entry.getKey(), icon, entry.getValue());
			manager.trackWaypoint(waypoint);
			active.put(entry.getKey(), waypoint);
		}
	}

	/** Removes every waypoint this mod added, leaving the server's own alone. */
	public static void clear() {
		ClientWaypointManager manager = manager();
		if (manager != null) {
			for (TrackedWaypoint waypoint : active.values()) {
				manager.untrackWaypoint(waypoint);
			}
		}
		active.clear();
	}

	private static ClientWaypointManager manager() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) return null;
		return client.player.connection.getWaypointManager();
	}

	/**
	 * A stable id for a marker, so refreshing does not create duplicates. Derived from
	 * the kind and position rather than random, and namespaced so it cannot collide
	 * with a waypoint the server sent.
	 */
	private static UUID idFor(String kind, int x, int y, int z) {
		return UUID.nameUUIDFromBytes(
			("crittermod:%s:%d:%d:%d".formatted(kind, x, y, z)).getBytes(StandardCharsets.UTF_8));
	}
}
