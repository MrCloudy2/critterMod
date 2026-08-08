package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the breakable walls that hide critters behind them.
 *
 * <p>There are two sets, each at fixed positions in one biome: the Snooper walls in the
 * Cavern and the Troodon walls in the Icy biome. Both want breaking every run to check
 * behind them. Unlike critters they are blocks rather than entities, which makes them
 * far easier to read: a wall is still standing exactly when its position is not air.
 *
 * <p>The one trap is that an unloaded chunk also reports air, which would mark every
 * distant wall as already broken. {@link Level#isLoaded} separates "broken" from
 * "cannot see from here", and the two are shown differently.
 */
public final class WallTracker {

	/** The Cavern walls. Snoozle comes from behind one of these, if it comes at all. */
	public static final WallTracker SNOOPER = new WallTracker("Snooper", SafariBiome.CAVERN, new int[][]{
		{-126, 39, 74},
		{-114, 39, 87},
		{-70, 39, 68},
		{-96, 40, 17},
		{-95, 40, 42},
	});

	/** The Icy walls. */
	public static final WallTracker TROODON = new WallTracker("Troodon", SafariBiome.ICY, new int[][]{
		{-104, 80, -95},
		{-131, 78, -61},
		{-109, 89, -27},
	});

	public enum State {
		/** Still standing — the position holds a block. */
		INTACT,
		/** Already broken this run. */
		BROKEN,
		/** Chunk not loaded, so air here would mean nothing. */
		UNKNOWN
	}

	/** One wall and what is known about it. */
	public record Wall(BlockPos pos, State state, double distance) {
	}

	private final String name;
	private final SafariBiome biome;
	private final int[][] positions;

	private WallTracker(String name, SafariBiome biome, int[][] positions) {
		this.name = name;
		this.biome = biome;
		this.positions = positions;
	}

	/** What these walls are called, for the panel heading. */
	public String name() {
		return name;
	}

	/** The biome they are in; nothing about them is worth showing anywhere else. */
	public SafariBiome biome() {
		return biome;
	}

	/** Every tracked wall with its current state, nearest first. */
	public List<Wall> walls() {
		Minecraft client = Minecraft.getInstance();
		List<Wall> result = new ArrayList<>();
		if (client.level == null || client.player == null) return result;

		for (int[] coords : positions) {
			BlockPos pos = new BlockPos(coords[0], coords[1], coords[2]);
			State state;
			if (!client.level.isLoaded(pos)) {
				state = State.UNKNOWN;
			} else {
				state = client.level.getBlockState(pos).isAir() ? State.BROKEN : State.INTACT;
			}
			double distance = Math.sqrt(client.player.position()
				.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
			result.add(new Wall(pos, state, distance));
		}
		result.sort((a, b) -> Double.compare(a.distance(), b.distance()));
		return result;
	}

	/** Walls known to still be standing. Unknown ones are not counted either way. */
	public long intactCount() {
		return walls().stream().filter(w -> w.state() == State.INTACT).count();
	}

	/**
	 * True only when every wall has been <em>confirmed</em> broken.
	 *
	 * <p>A wall in an unloaded chunk does not count: air and out-of-range look
	 * identical from here, and concluding "all broken" from chunks nobody has visited
	 * would be exactly backwards.
	 */
	public boolean allConfirmedBroken() {
		List<Wall> walls = walls();
		return !walls.isEmpty() && walls.stream().allMatch(w -> w.state() == State.BROKEN);
	}
}
