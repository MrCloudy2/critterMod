package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** The positions worth highlighting, each behind its own setting. */
public final class Markers {

	/**
	 * Species awkward enough to spot that a box around them earns its place.
	 *
	 * <p>Bloodbats are small, dark, and sit against Haunted geometry the same colour as
	 * they are.
	 */
	private static final java.util.Set<String> HARD_TO_FIND = java.util.Set.of("Bloodbat");

	/** How a marker is drawn. */
	public enum Style {
		/** Through the terrain, with its name and distance floating above it. */
		WAYPOINT,
		/** Depth-tested and unnamed: a box on the thing, seen only when the thing is. */
		HIGHLIGHT
	}

	/**
	 * One thing worth walking to.
	 *
	 * <p>A box rather than a position: most of these are blocks and so are exactly a
	 * block big, but a pinned critter is whatever size that critter is, and drawing a
	 * full block around a Rockmite would be pointing at the wrong thing.
	 */
	public record Marker(AABB box, String label, int colour, Style style) {
	}

	private Markers() {
	}

	public static List<Marker> collect() {
		CritterConfig.DisplayConfig display = ConfigManager.get().display;
		SafariBiome biome = SafariLocation.biome();
		List<Marker> markers = new ArrayList<>();

		// Everything but the trades belongs to one biome, so it is only marked while you
		// are in that biome. Boxes floating through the terrain of a biome they have
		// nothing to do with are just noise.
		if (display.highlightSnooperWalls) {
			addWalls(markers, WallTracker.SNOOPER, biome,
				Colours.argb(display.snooperWallColour, 0xFFFFAA00));
		}
		if (display.highlightTroodonWalls) {
			addWalls(markers, WallTracker.TROODON, biome,
				Colours.argb(display.troodonWallColour, 0xFF55AAFF));
		}

		// Preset spots, like the perches and the walls — but each one carries what looking
		// at it established, so the colour says whether there is anything there. Cleared
		// ones are simply not marked: you went, you looked, there was no hive.
		if (display.highlightBeeNests && biome == SafariBiome.FOREST) {
			for (BeeSpots.Spot spot : BeeSpots.spots()) {
				if (spot.status() == BeeSpots.Status.CLEARED) continue;
				boolean found = spot.status() == BeeSpots.Status.NEST;
				markers.add(block(spot.pos(), found ? "Bee nest" : "Bee spot",
					found ? Colours.argb(display.beeNestColour, 0xFFFFCC22)
						: Colours.argb(display.beeSpotColour, 0xFF55FF55)));
			}
		}

		if (display.highlightTrades) {
			for (TraderWatch.Trade trade : TraderWatch.found()) {
				TraderWatch.Spot spot = trade.spot();
				if (spot == null) continue;
				markers.add(block(new BlockPos(spot.x(), spot.y(), spot.z()),
					trade.critter().name(), Colours.argb(display.tradeColour, 0xFF55FFFF)));
			}
		}

		// Boxed where it stands, never through the terrain: this marks something you
		// could see anyway, which is the whole difference between helping you spot it
		// and telling you what is behind a wall.
		if (display.highlightHardToFind) {
			for (CritterEntities.Sighting sighting : CritterEntities.all()) {
				if (!HARD_TO_FIND.contains(sighting.critter().name())) continue;
				// The name tag alone gives nothing to box; the mob under it is the thing.
				if (sighting.mob() == null) continue;
				markers.add(new Marker(sighting.mob().getBoundingBox(), sighting.critter().name(),
					Colours.argb(display.hardToFindColour, 0xFFFF5555), Style.HIGHLIGHT));
			}
		}

		// Fixed perches rather than anything read from the world: an empty one looks the
		// same as an occupied one, so the mark means "check here".
		if (display.highlightHideonwalls && biome == SafariBiome.HAUNTED) {
			for (BlockPos pos : HideonwallSpots.positions()) {
				markers.add(block(pos, "Hideonwall perch",
					Colours.argb(display.hideonwallColour, 0xFFAA55FF)));
			}
		}

		if (display.highlightMounds && biome == SafariBiome.CAVERN) {
			for (BlockPos pos : MoundSpotter.mounds()) {
				markers.add(block(pos, "Mound", Colours.argb(display.moundColour, 0xFFCC7744)));
			}
		}

		// Not gated on the biome: it is pinned by a capsule you threw, so it is wherever
		// you were standing when you threw it.
		if (display.recatchHelper && RecatchSpots.pinned() != null) {
			markers.add(new Marker(RecatchSpots.pinned(), RecatchSpots.pinnedCritter().name(),
				Colours.argb(display.recatchColour, 0xFFFFFF55), Style.WAYPOINT));
		}

		// Drops turn up in every biome, so each one is gated on its own position rather
		// than the feature being gated wholesale: standing near a border would otherwise
		// mark drops on the far side of it.
		//
		// Always a highlight, never a waypoint. A drop is put wherever the run put it, so
		// unlike the walls and perches above there is nothing preset about it, and a box
		// drawn through the terrain would be handing over a position that could not have
		// been worked out any other way.
		if (display.floorDropStyle() != CritterConfig.MarkStyle.OFF && biome != null) {
			for (BlockPos pos : FloorDrops.positions()) {
				if (SafariAreaMap.biomeAt(pos.getX(), pos.getY(), pos.getZ()) != biome) continue;
				markers.add(new Marker(new AABB(pos), "Floor drop",
					Colours.argb(display.floorDropColour, 0xFF55FFAA), Style.HIGHLIGHT));
			}
		}
		return markers;
	}

	/** A marker filling one block, which is what everything read off the map wants. */
	private static Marker block(BlockPos pos, String label, int colour) {
		return new Marker(new AABB(pos), label, colour, Style.WAYPOINT);
	}

	/**
	 * Marks the walls of one set that have not been seen to be gone.
	 *
	 * <p>Everything but {@code BROKEN}, rather than only {@code INTACT}: the positions are
	 * preset, so a mark on one costs nothing even before its chunk has loaded, and the
	 * only thing that should take a mark away is having looked at the wall and found it
	 * broken. Marking only confirmed-standing walls used to hide the ones a partymate had
	 * broken out of sight, which is the same as being told about it.
	 */
	private static void addWalls(List<Marker> markers, WallTracker walls, SafariBiome biome, int colour) {
		if (biome != walls.biome()) return;
		for (WallTracker.Wall wall : walls.walls()) {
			if (wall.state() == WallTracker.State.BROKEN) continue;
			markers.add(block(wall.pos(), walls.name() + " wall", colour));
		}
	}
}
