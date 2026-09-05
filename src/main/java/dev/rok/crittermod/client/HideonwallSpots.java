package dev.rok.crittermod.client;

import dev.rok.crittermod.parse.ChatParser;
import dev.rok.crittermod.parse.CritterEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The perches a Hideonwall can turn up on.
 *
 * <p>Eight preset positions. An empty perch looks exactly like an occupied one from any
 * distance, so the mark means "check here" rather than "one is here" — until the perch
 * has actually been resolved, at which point it says which.
 *
 * <p>A perch is resolved by <em>seeing it</em> or by <em>a capsule passing through it</em>,
 * and the answer is then kept:
 *
 * <ul>
 * <li>a Hideonwall on it — the mark turns its own colour and stays;
 * <li>nothing on it — the mark goes, because you now know.
 * </ul>
 *
 * <p><strong>Hideonwalls sit behind paintings</strong>, and a painting is an entity rather
 * than a block, so a plain {@code level.clip} ray goes straight through one and would
 * report a clear view of a perch nothing can be seen of. {@link Visibility} accounts for
 * them, which is what stops a perch being called empty — or called occupied — on the
 * strength of a look that never actually landed. A perch behind a painting stays
 * unresolved, which is the honest answer.
 *
 * <p>Resolved perches are re-read whenever they can be seen again, so catching the thing
 * turns the mark off by itself. A catch in chat does the same without needing a second
 * look, for when it is taken from across the room.
 *
 * <p><strong>A capsule is an {@code item_display} showing an item named "Critter
 * Capsule"</strong> — a zero-size entity, not a projectile of any kind. That was
 * established with {@code /critters entities} from real throws, after an earlier version
 * matched the {@code Projectile} base class and so never fired once. The entity type alone
 * is no use: the Safari is full of zero-size item displays and only the item's name tells
 * them apart.
 *
 * <p>All eight resolve to the Haunted biome on the area map, which is what they were
 * checked against before being trusted.
 */
public final class HideonwallSpots {

	/** The species these perches are for. */
	private static final String SPECIES = "Hideonwall";

	/**
	 * How near a capsule has to pass to count as having reached the perch.
	 *
	 * <p>Half a block off the block's own faces: loose enough that a throw which visibly
	 * struck the wall around it counts, tight enough that one sailing past does not.
	 */
	private static final double HIT_MARGIN = 0.5;

	/** How near a sighting has to be to count as sitting on the perch. */
	private static final double ON_PERCH_RADIUS = 3.0;

	/** The item a thrown capsule is displaying. */
	private static final String CAPSULE = "Critter Capsule";

	private static final int[][] PERCHES = {
		{18, 71, -57},
		{-5, 71, -85},
		{-24, 71, -76},
		{-25, 71, -65},
		{-25, 79, -57},
		{6, 79, -84},
		{17, 80, -75},
		{17, 79, -52},
	};

	/** How a perch should be drawn. Anything resolved empty is simply not in the list. */
	public enum State {
		/** Not resolved. The mark means "check here". */
		OUTSTANDING,
		/** Resolved, and something was on it. */
		OCCUPIED
	}

	/** One perch and how it should be drawn. */
	public record Perch(BlockPos pos, State state) {
	}

	private static final List<BlockPos> POSITIONS = buildPositions();
	/** Perch to whether it held one. Absent means still unresolved. */
	private static final Map<BlockPos, Boolean> resolved = new LinkedHashMap<>();
	/** Capsules seen at all this run — diagnostic, so "never fired" is distinguishable. */
	private static int capsulesSeen;

	private HideonwallSpots() {
	}

	/**
	 * Resolves whatever can be seen, and whatever a capsule has just flown through.
	 *
	 * <p>Runs every tick rather than on an interval: a projectile's previous position is
	 * only kept for one tick, so skipping ticks would leave gaps in the swept line.
	 */
	public static void tick() {
		if (!ConfigManager.get().display.highlightHideonwalls) return;
		if (!SafariLocation.inSafari()) return;

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		// Seen. Re-read every time rather than settled once, so a perch that had one on it
		// stops claiming so the moment the thing is gone.
		for (BlockPos pos : POSITIONS) {
			if (Visibility.canSee(pos)) resolved.put(pos, occupied(pos));
		}

		for (Entity entity : client.level.entitiesForRendering()) {
			if (!isCapsule(entity)) continue;
			capsulesSeen++;

			// Where it was last tick to where it is now — the line it actually travelled.
			// A capsule covers 2 to 2.6 blocks a tick, measured, so this is not optional:
			// comparing only its current position would let most throws pass clean through.
			Vec3 to = entity.position();
			Vec3 from = new Vec3(entity.xOld, entity.yOld, entity.zOld);
			if (from.equals(to)) continue;

			for (BlockPos pos : POSITIONS) {
				if (new AABB(pos).inflate(HIT_MARGIN).clip(from, to).isPresent()) {
					resolved.put(pos, occupied(pos));
				}
			}
		}
	}

	/**
	 * Feeds one cleaned chat line.
	 *
	 * <p>A Hideonwall being caught retires every perch still claiming to hold one. Looking
	 * at the perch again would say the same thing, but this covers taking it from across
	 * the room and walking off without a second glance.
	 */
	public static void onChatMessage(String line) {
		CritterEvent event = ChatParser.parse(line, null);
		if (event == null || event.critter() == null) return;
		if (!SPECIES.equals(event.critter().name())) return;
		if (event.type() != CritterEvent.Type.OWN_CATCH
			&& event.type() != CritterEvent.Type.SHARED_CATCH) return;
		resolved.replaceAll((pos, held) -> false);
	}

	/** The perches worth drawing, and how. Resolved-empty ones are left out. */
	public static List<Perch> perches() {
		List<Perch> result = new ArrayList<>(POSITIONS.size());
		for (BlockPos pos : POSITIONS) {
			Boolean held = resolved.get(pos);
			if (held == null) {
				result.add(new Perch(pos, State.OUTSTANDING));
			} else if (held) {
				result.add(new Perch(pos, State.OCCUPIED));
			}
		}
		return result;
	}

	/**
	 * Whether this is a capsule in flight.
	 *
	 * <p>Read off the displayed item, since the entity type says nothing useful — the
	 * Safari is full of zero-size item displays and this is only one of them.
	 */
	private static boolean isCapsule(Entity entity) {
		if (!(entity instanceof Display.ItemDisplay display)) return false;
		Display.ItemDisplay.ItemRenderState state = display.itemRenderState();
		if (state == null) return false;
		ItemStack stack = state.itemStack();
		if (stack == null || stack.isEmpty()) return false;
		return CAPSULE.equals(SafariLocation.strip(stack.getHoverName().getString()));
	}

	/** Whether a Hideonwall is on this perch right now. */
	private static boolean occupied(BlockPos pos) {
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!SPECIES.equals(sighting.critter().name())) continue;
			if (sighting.body().position().distanceToSqr(Vec3.atCenterOf(pos))
				<= ON_PERCH_RADIUS * ON_PERCH_RADIUS) {
				return true;
			}
		}
		return false;
	}

	/** Every perch, resolved or not. */
	public static List<BlockPos> all() {
		return POSITIONS;
	}

	/** State and projectile count, for {@code /critters debug}. */
	public static String describe() {
		long occupied = resolved.values().stream().filter(Boolean::booleanValue).count();
		return "%d unresolved, %d holding one, of %d · %d capsules seen".formatted(
			POSITIONS.size() - resolved.size(), occupied, POSITIONS.size(), capsulesSeen);
	}

	/** A new run puts every perch back: whatever was on them last time is long gone. */
	public static void reset() {
		resolved.clear();
		capsulesSeen = 0;
	}

	private static List<BlockPos> buildPositions() {
		List<BlockPos> positions = new ArrayList<>(PERCHES.length);
		for (int[] perch : PERCHES) {
			positions.add(new BlockPos(perch[0], perch[1], perch[2]));
		}
		return List.copyOf(positions);
	}
}
