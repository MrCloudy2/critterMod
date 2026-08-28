package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The places a bee block sits in the Forest, and what each one turned out to be.
 *
 * <p>Eleven preset positions, marked from the start of the run — so, like the walls and
 * the Hideonwall perches, the marks themselves give nothing away. What is <em>at</em> a
 * position does, and that has to be earned by looking at it.
 *
 * <p>Each spot therefore goes through three states, and only sight moves it along:
 *
 * <ul>
 * <li>{@link Status#UNCHECKED} — go and look;
 * <li>{@link Status#NEST} — a bee nest, marked in its own colour. This is the one worth
 *     walking to;
 * <li>{@link Status#CLEARED} — a beehive, or nothing, so the mark goes.
 * </ul>
 *
 * <p>{@code BEE_NEST} and {@code BEEHIVE} are two different blocks, and telling them
 * apart is the whole feature: the nest is what you want and the hive is what you can
 * ignore, so a spot holding a hive is worth actively crossing off. Vanilla spells them
 * inconsistently — {@code BEEHIVE} is one word, {@code BEE_NEST} is two.
 *
 * <p>Anything other than those two blocks also clears the spot. Having looked at a place
 * and found neither is a real answer, not an undetermined one — the sight check has
 * already ruled out "cannot see from here", which is the case that would otherwise
 * deserve the benefit of the doubt.
 *
 * <p>All eleven resolve to the Forest biome on the area map, which is what they were
 * checked against before being trusted. The last two sit some fifteen blocks above the
 * rest, which is worth knowing if a scanning radius is ever put back anywhere near this.
 */
public final class BeeSpots {

	/** They do not change mid-look; twice a second is plenty. */
	private static final int SCAN_INTERVAL_TICKS = 10;

	private static final int[][] SPOTS = {
		{-17, 68, 41},
		{-22, 68, 39},
		{23, 70, 46},
		{25, 70, 41},
		{-9, 81, 58},
		{-1, 81, 60},
		{15, 88, 31},
		{20, 88, 33},
		{-1, 84, 9},
		{-13, 103, 21},
		{-11, 103, 13},
	};

	/** What looking at a spot established. */
	public enum Status {
		/** Not yet seen. The mark means "check here". */
		UNCHECKED,
		/** Seen to hold a bee nest — the one worth going to. */
		NEST,
		/** Seen to hold a beehive, or nothing. Not marked. */
		CLEARED
	}

	/** One preset position and what is known about it this run. */
	public record Spot(BlockPos pos, Status status) {
	}

	private static final List<BlockPos> POSITIONS = buildPositions();
	/** Only holds spots that have been resolved; anything absent is still unchecked. */
	private static final Map<BlockPos, Status> seen = new HashMap<>();
	private static int ticks;

	private BeeSpots() {
	}

	/**
	 * Resolves any spot that can now be seen.
	 *
	 * <p>Only ever settles a spot once. Punching leaves the block as it was, so a nest
	 * dealt with later in the run reads the same as one that has not been — there is
	 * nothing to be gained by looking again, and re-reading would only let a mark flicker
	 * as you walked past.
	 */
	public static void tick() {
		if (++ticks < SCAN_INTERVAL_TICKS) return;
		ticks = 0;

		if (!ConfigManager.get().display.highlightBeeNests) return;
		if (!SafariLocation.inSafari()) return;

		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) return;

		for (BlockPos pos : POSITIONS) {
			if (seen.containsKey(pos)) continue;
			if (!client.level.isLoaded(pos)) continue;
			if (!Visibility.canSee(pos)) continue;
			seen.put(pos, client.level.getBlockState(pos).is(Blocks.BEE_NEST)
				? Status.NEST : Status.CLEARED);
		}
	}

	/** Every spot with its current status, cleared ones included. */
	public static List<Spot> spots() {
		List<Spot> result = new ArrayList<>(POSITIONS.size());
		for (BlockPos pos : POSITIONS) {
			result.add(new Spot(pos, seen.getOrDefault(pos, Status.UNCHECKED)));
		}
		return result;
	}

	/** Spots worth a mark: the ones not yet looked at, plus the nests found. */
	public static long outstanding() {
		return spots().stream().filter(s -> s.status() != Status.CLEARED).count();
	}

	/** Every run reshuffles what is where, so last run's answers mean nothing. */
	public static void reset() {
		seen.clear();
	}

	private static List<BlockPos> buildPositions() {
		List<BlockPos> positions = new ArrayList<>(SPOTS.length);
		for (int[] spot : SPOTS) {
			positions.add(new BlockPos(spot[0], spot[1], spot[2]));
		}
		return List.copyOf(positions);
	}
}
