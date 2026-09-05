package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
 * <li>{@link Status#CLEARED} — a beehive, nothing, or a nest already emptied, so the
 *     mark goes.
 * </ul>
 *
 * <p>A nest is also crossed off by <em>punching it and getting a bee</em>. The punch alone
 * is not the signal: a nest already emptied can be hit all day and gives nothing, and
 * clearing on the swing would retire a spot that still has something in it.
 *
 * <p>Nothing is said in chat when a Safari nest is punched — that was checked against
 * every log on disk, and it is why the tracker this replaces recorded the swing itself.
 * The bee arriving is the only evidence, so a punch arms the spot and the mark goes when a
 * <em>new</em> Honeybug turns up near it within {@link #PUNCH_WINDOW_MILLIS}. New matters:
 * one already wandering past would otherwise retire a nest that never opened.
 *
 * <p>An earlier version watched for {@code A Honeybuzz appeared, angry at you for stealing
 * its honey!}. That line belongs to the Honeyhive feature elsewhere in SkyBlock — it is
 * always preceded by {@code You stick your hand into the honeyhive} — and could never have
 * fired in the Safari.
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

	/**
	 * How long after a punch the bee still counts as having come from it.
	 *
	 * <p>The message follows the swing immediately; this is slack for a laggy tick, not a
	 * window wide enough for a second nest to get mixed up in it.
	 */
	private static final long PUNCH_WINDOW_MILLIS = 3_000;

	/** The species a nest gives up. */
	private static final String BEE = "Honeybug";

	/** How near the punched spot a new bee has to appear to have come out of it. */
	private static final double BEE_RADIUS = 6.0;

	/**
	 * How far a punch may land from a recorded spot and still count as that one.
	 *
	 * <p>The positions were collected by hand, and a nest is a block you hit from beside
	 * rather than stand on, so demanding an exact match made the punch register on nothing
	 * at all — which is what the first version did.
	 */
	private static final double PUNCH_RADIUS = 2.5;

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
	/** The spot last punched, waiting to see whether a bee comes out of it. */
	private static BlockPos punched;
	private static long punchedAt;
	/** Bees already in the world when the punch landed; a new one is not among these. */
	private static final Set<UUID> beesBefore = new HashSet<>();

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
		// Ahead of the interval gate: a bee is out for a moment and the window is short.
		checkForBee();

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

	/**
	 * Notes a punch on one of the known spots, so a bee that follows can be attributed to
	 * it. A punch anywhere else is not this feature's business.
	 */
	public static void onAttack(BlockPos pos) {
		BlockPos spot = nearestSpot(pos);
		if (spot == null) return;
		punched = spot;
		punchedAt = System.currentTimeMillis();

		// Everything already buzzing about, so the one that comes out of this nest can be
		// told apart from one that happened to be walking past.
		beesBefore.clear();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (BEE.equals(sighting.critter().name())) beesBefore.add(sighting.label().getUUID());
		}
	}

	/** The recorded spot a punch landed on, or {@code null} if it was not near one. */
	private static BlockPos nearestSpot(BlockPos pos) {
		BlockPos best = null;
		double bestSq = PUNCH_RADIUS * PUNCH_RADIUS;
		for (BlockPos spot : POSITIONS) {
			double distanceSq = spot.distSqr(pos);
			if (distanceSq > bestSq) continue;
			bestSq = distanceSq;
			best = spot;
		}
		return best;
	}

	/**
	 * Looks for the bee that a punch let out.
	 *
	 * <p>Called from the sweep, so it sees whatever {@link CritterEntities} last found.
	 * A punch that produces nothing simply runs out of time, which is the whole point: an
	 * already-emptied nest keeps its mark rather than looking dealt with.
	 */
	private static void checkForBee() {
		if (punched == null) return;
		if (System.currentTimeMillis() - punchedAt > PUNCH_WINDOW_MILLIS) {
			punched = null;
			return;
		}
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!BEE.equals(sighting.critter().name())) continue;
			if (beesBefore.contains(sighting.label().getUUID())) continue;
			if (sighting.body().position().distanceToSqr(Vec3.atCenterOf(punched))
				> BEE_RADIUS * BEE_RADIUS) continue;
			seen.put(punched, Status.CLEARED);
			punched = null;
			return;
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
		punched = null;
		beesBefore.clear();
	}

	/** What each spot is doing, for {@code /critters debug}. */
	public static String describe() {
		long nests = spots().stream().filter(s -> s.status() == Status.NEST).count();
		long unchecked = spots().stream().filter(s -> s.status() == Status.UNCHECKED).count();
		return "%d nests, %d unchecked, of %d%s".formatted(
			nests, unchecked, POSITIONS.size(),
			punched == null ? "" : " (armed at " + punched.toShortString() + ")");
	}

	private static List<BlockPos> buildPositions() {
		List<BlockPos> positions = new ArrayList<>(SPOTS.length);
		for (int[] spot : SPOTS) {
			positions.add(new BlockPos(spot[0], spot[1], spot[2]));
		}
		return List.copyOf(positions);
	}
}
