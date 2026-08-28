package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks the breakable walls that hide critters behind them.
 *
 * <p>There are two sets, each at fixed positions in one biome: the Snooper walls in the
 * Cavern and the Troodon walls in the Icy biome. Both want breaking every run to check
 * behind them. Their positions are preset, so marking them gives nothing away — but
 * whether one is still standing is read out of the world, and that has to be earned.
 *
 * <p>So a wall being gone is <em>latched, and only on sight</em>. Reading the block
 * state alone is not enough: chunks load far past what anyone can see, so a wall a
 * partymate broke on the other side of the Cavern used to drop off this client's list
 * the moment its chunk came into range — the mod quietly reporting work you had no way
 * of knowing was done. Now a wall stays listed until you have had a clear line to the
 * space where it was.
 *
 * <p>{@link Level#isLoaded} still matters, because an unloaded chunk reports air too.
 * Between that and the sight requirement, air at a wall's position means "broken" only
 * once both are satisfied; anything else is {@link State#UNKNOWN}, which is counted as
 * still to do rather than as done.
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

	/** How often sight of the walls is rechecked. They are five positions; this is cheap. */
	private static final int CONFIRM_INTERVAL_TICKS = 10;

	public enum State {
		/** Still standing — the position holds a block. */
		INTACT,
		/** Seen to be gone. Sticks for the rest of the run once established. */
		BROKEN,
		/** Not loaded, or empty but never looked at. Still to do, not done. */
		UNKNOWN
	}

	/** One wall and what is known about it. */
	public record Wall(BlockPos pos, State state, double distance) {
	}

	private final String name;
	private final SafariBiome biome;
	private final int[][] positions;
	/** Walls seen to be gone. Only ever added to within a run; cleared when one starts. */
	private final Set<BlockPos> confirmedBroken = new HashSet<>();
	private static int ticks;

	private WallTracker(String name, SafariBiome biome, int[][] positions) {
		this.name = name;
		this.biome = biome;
		this.positions = positions;
	}

	/** Looks at both sets of walls and latches any newly seen to be gone. */
	public static void tick() {
		if (++ticks < CONFIRM_INTERVAL_TICKS) return;
		ticks = 0;
		if (!SafariLocation.inSafari()) return;
		SNOOPER.confirm();
		TROODON.confirm();
	}

	/**
	 * Latches the walls of this set that can be seen to be gone.
	 *
	 * <p>Only ever adds. A wall does not come back, so once it has been seen broken there
	 * is nothing to reconsider — and rechecking would only let it flicker back to
	 * outstanding when you walked away.
	 */
	private void confirm() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) return;

		for (int[] coords : positions) {
			BlockPos pos = new BlockPos(coords[0], coords[1], coords[2]);
			if (confirmedBroken.contains(pos)) continue;
			if (!client.level.isLoaded(pos)) continue;
			if (!client.level.getBlockState(pos).isAir()) continue;
			if (!Visibility.canSee(pos)) continue;
			confirmedBroken.add(pos);
		}
	}

	/** The walls of both sets start every run unbroken as far as this client knows. */
	public static void resetAll() {
		SNOOPER.confirmedBroken.clear();
		TROODON.confirmedBroken.clear();
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
			// Reading only, in the order the evidence is worth: something seen gone is
			// gone, a block that is there is there, and everything else is not yet known.
			// The air-but-unseen case lands in UNKNOWN, which is the whole point.
			State state;
			if (confirmedBroken.contains(pos)) {
				state = State.BROKEN;
			} else if (client.level.isLoaded(pos) && !client.level.getBlockState(pos).isAir()) {
				state = State.INTACT;
			} else {
				state = State.UNKNOWN;
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
	 * <p>Confirmed now means seen, so this is a stronger claim than it used to be: every
	 * wall has to have been looked at. That is what the caller wants — it decides whether
	 * to stop listing Snoozle as outstanding, and writing the species off on the strength
	 * of chunks nobody has visited would be exactly backwards.
	 */
	public boolean allConfirmedBroken() {
		List<Wall> walls = walls();
		return !walls.isEmpty() && walls.stream().allMatch(w -> w.state() == State.BROKEN);
	}
}
