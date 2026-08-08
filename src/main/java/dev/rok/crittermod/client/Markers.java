package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** The positions worth highlighting, each behind its own setting. */
public final class Markers {

	/** One thing worth walking to. */
	public record Marker(BlockPos pos, String label, int colour) {
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
			addWalls(markers, WallTracker.SNOOPER, biome);
		}
		if (display.highlightTroodonWalls) {
			addWalls(markers, WallTracker.TROODON, biome);
		}

		if (display.highlightNests && biome == SafariBiome.FOREST) {
			for (NestTracker.Nest nest : NestTracker.nests()) {
				if (!nest.unpunched()) continue;
				markers.add(new Marker(nest.pos(), "Nest", 0x55FF55));
			}
		}

		if (display.highlightTrades) {
			for (TraderWatch.Trade trade : TraderWatch.found()) {
				TraderWatch.Spot spot = trade.spot();
				if (spot == null) continue;
				markers.add(new Marker(new BlockPos(spot.x(), spot.y(), spot.z()), trade.critter().name(), 0x55FFFF));
			}
		}

		// It promises to stay in the Haunted biome, and says so in chat every round.
		if (display.hideyhoSolver && biome == SafariBiome.HAUNTED) {
			BlockPos hideyho = HideyhoSolver.position();
			if (hideyho != null) markers.add(new Marker(hideyho, "Hideyho", 0xFF55FF));
		}

		if (display.highlightMounds && biome == SafariBiome.CAVERN) {
			for (BlockPos pos : MoundSpotter.mounds()) {
				markers.add(new Marker(pos, "Mound", 0xCC7744));
			}
		}
		return markers;
	}

	/**
	 * Marks the walls of one set that are still standing.
	 *
	 * <p>A broken wall leaves air behind, and air is also what an unloaded chunk reports,
	 * so only walls confirmed to still hold a block are marked — never a guess.
	 */
	private static void addWalls(List<Marker> markers, WallTracker walls, SafariBiome biome) {
		if (biome != walls.biome()) return;
		for (WallTracker.Wall wall : walls.walls()) {
			if (wall.state() != WallTracker.State.INTACT) continue;
			markers.add(new Marker(wall.pos(), walls.name() + " wall", 0xFFAA00));
		}
	}
}
