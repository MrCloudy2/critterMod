package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks the bee nests that Honeybugs come from.
 *
 * <p>Unlike the Cavern walls these have no fixed positions to check, so they have to
 * be found by sweeping blocks near the player.
 *
 * <p>Punching one leaves the block exactly as it was — same block, same state — so
 * there is nothing to read back afterwards. The punch itself is therefore what gets
 * recorded, via {@link net.fabricmc.fabric.api.event.player.AttackBlockCallback}. The
 * consequence is that a nest someone else punched still shows as outstanding, since
 * this client never saw it happen.
 *
 * <p>The known set therefore grows as the Forest is explored, so the count is "nests
 * you have come across", not "nests on the map". That is the honest reading and it is
 * still the actionable one — an unpunched nest is somewhere you can walk to.
 */
public final class NestTracker {

	/** Sweeping is comparatively expensive, so it runs on a timer rather than per tick. */
	private static final int SCAN_INTERVAL_TICKS = 40;
	private static final int SCAN_RADIUS = 24;
	private static final int SCAN_HEIGHT = 12;

	private static final Set<BlockPos> known = new LinkedHashSet<>();
	private static final Set<BlockPos> punched = new LinkedHashSet<>();
	private static int ticks;

	/** A nest and whether it still needs punching. */
	public record Nest(BlockPos pos, boolean unpunched, double distance) {
	}

	private NestTracker() {
	}

	/** Records a punch on a nest. Hooked to the attack event, hence the block check. */
	public static void onAttack(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;
		if (client.level.getBlockState(pos).getBlock() != Blocks.BEE_NEST) return;
		BlockPos immutable = pos.immutable();
		known.add(immutable);
		punched.add(immutable);
	}

	public static void tick() {
		if (++ticks < SCAN_INTERVAL_TICKS) return;
		ticks = 0;

		if (!ConfigManager.get().display.showNests) return;
		if (!AreaDetector.inSafari()) return;

		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) return;

		BlockPos centre = client.player.blockPosition();
		BlockPos from = centre.offset(-SCAN_RADIUS, -SCAN_HEIGHT, -SCAN_RADIUS);
		BlockPos to = centre.offset(SCAN_RADIUS, SCAN_HEIGHT, SCAN_RADIUS);

		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			if (!client.level.isLoaded(pos)) continue;
			if (client.level.getBlockState(pos).getBlock() != Blocks.BEE_NEST) continue;
			// betweenClosed hands back one reusable position, so it must be copied.
			known.add(pos.immutable());
		}
	}

	/** Every nest found so far, still-to-punch ones first, then by distance. */
	public static List<Nest> nests() {
		Minecraft client = Minecraft.getInstance();
		List<Nest> result = new ArrayList<>();
		if (client.level == null || client.player == null) return result;

		for (BlockPos pos : known) {
			// An unloaded chunk reports air, which would read as punched. Only a loaded
			// chunk can say either way, so anything else is left out of the count.
			if (!client.level.isLoaded(pos)) continue;
			boolean unpunched = !punched.contains(pos);
			double distance = Math.sqrt(client.player.position()
				.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
			result.add(new Nest(pos, unpunched, distance));
		}
		result.sort((a, b) -> a.unpunched() != b.unpunched()
			? Boolean.compare(!a.unpunched(), !b.unpunched())
			: Double.compare(a.distance(), b.distance()));
		return result;
	}

	public static long unpunchedCount() {
		return nests().stream().filter(Nest::unpunched).count();
	}

	/** Nests are per-instance, so what was found last run means nothing in this one. */
	public static void reset() {
		known.clear();
		punched.clear();
	}
}
