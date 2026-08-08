package dev.rok.crittermod.client;

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
		List<Marker> markers = new ArrayList<>();

		if (display.highlightWalls) {
			for (WallTracker.Wall wall : WallTracker.walls()) {
				if (wall.state() != WallTracker.State.INTACT) continue;
				markers.add(new Marker(wall.pos(), "Wall", 0xFFAA00));
			}
		}

		if (display.highlightNests) {
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

		if (display.highlightMounds) {
			for (BlockPos pos : MoundSpotter.mounds()) {
				markers.add(new Marker(pos, "Mound", 0xCC7744));
			}
		}
		return markers;
	}
}
