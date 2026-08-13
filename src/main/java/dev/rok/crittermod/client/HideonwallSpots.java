package dev.rok.crittermod.client;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * The perches a Hideonwall can turn up on.
 *
 * <p>Unlike everything else marked in this mod, these are not read from the world —
 * nothing about a perch looks different when it is empty. They are the eight places one
 * has been seen, so the mark says "check here", not "one is here". Which is the useful
 * thing for a species whose whole trick is sitting still against a wall.
 *
 * <p>All eight resolve to the Haunted biome on the area map, between one and seven
 * blocks from a mapped node, which is what they were checked against before being
 * trusted.
 */
public final class HideonwallSpots {

	private static final int[][] PERCHES = {
		{18, 71, -57},
		{-5, 71, -85},
		{-23, 71, -76},
		{-25, 71, -65},
		{-25, 79, -57},
		{6, 79, -84},
		{17, 80, -75},
		{17, 79, -52},
	};

	private static final List<BlockPos> POSITIONS = buildPositions();

	private HideonwallSpots() {
	}

	/** Every perch, whether or not anything is on it. */
	public static List<BlockPos> positions() {
		return POSITIONS;
	}

	private static List<BlockPos> buildPositions() {
		List<BlockPos> positions = new java.util.ArrayList<>(PERCHES.length);
		for (int[] perch : PERCHES) {
			positions.add(new BlockPos(perch[0], perch[1], perch[2]));
		}
		return List.copyOf(positions);
	}
}
