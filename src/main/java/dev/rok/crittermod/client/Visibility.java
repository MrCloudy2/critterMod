package dev.rok.crittermod.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether the player can actually see a thing, rather than merely having it loaded.
 *
 * <p>The client is sent every entity and every block within the server's tracking range,
 * walls or no walls, and {@code entitiesForRendering()} hands back all of them despite
 * the name. Reading that list directly is how a mod ends up reporting what is behind a
 * wall, so anything the player is told about a <em>dynamic</em> thing — a critter that
 * spawned this run, a wall someone else broke — goes through here first.
 *
 * <p>Preset positions are deliberately not gated on this. A fixed perch or a wall's
 * location is map knowledge that could be written on a sticky note, so marking one
 * reveals nothing; it is only ever the state read back out of the world that has to be
 * earned by looking at it.
 *
 * <p>Two questions, because the callers want different ones:
 *
 * <ul>
 * <li>{@link #canSee} — on screen <em>and</em> unobstructed. What SkyHanni's
 *     {@code EntityUtils.canBeSeen} asks, in the same order: the frustum first, since it
 *     is a cheap rejection, then the ray.
 * <li>{@link #hasLineTo} — unobstructed only, ignoring which way you are facing.
 *     SkyHanni exposes the same escape hatch as {@code ignoreFrustum}. It is for the
 *     things that are <em>counted</em> rather than announced: a number that fell to zero
 *     because you turned round would read as the mod having broken.
 * </ul>
 *
 * <p>The frustum is the game's own — the one vanilla culls rendering with — rather than
 * a hand-rolled dot product against the look vector, so "on screen" means exactly what
 * it means everywhere else, including at whatever FOV and aspect ratio the player has
 * set.
 */
public final class Visibility {

	/**
	 * Past this there is nothing to be gained by claiming a sighting, and it bounds what
	 * each ray costs. Hypixel stops tracking well inside it.
	 */
	private static final double MAX_RANGE = 128.0;

	/** Paintings do not move, so the list is worth keeping for a moment. */
	private static final long PAINTING_CACHE_MILLIS = 1_000;
	private static List<AABB> paintings = List.of();
	private static long paintingsAt;

	private Visibility() {
	}

	/**
	 * Whether {@code target} is on screen and nothing is in the way.
	 *
	 * <p>This is the one to use for anything that <em>tells</em> the player something.
	 */
	public static boolean canSee(Entity target) {
		return target != null && inView(target.getBoundingBox()) && hasLineTo(target);
	}

	/**
	 * Whether any part of {@code target} is unobstructed from the player's eyes,
	 * whichever way they happen to be facing.
	 *
	 * <p>Several points are tried rather than the single eye-to-eye ray vanilla uses for
	 * its own line-of-sight checks: a Rockmite is small enough that its centre can be
	 * behind a lip of stone while most of it is in plain view, and calling that hidden
	 * would lose sightings the player can see perfectly well.
	 */
	public static boolean hasLineTo(Entity target) {
		if (target == null) return false;

		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || client.level == null) return false;

		Vec3 eye = player.getEyePosition();
		AABB box = target.getBoundingBox();
		if (eye.distanceToSqr(box.getCenter()) > MAX_RANGE * MAX_RANGE) return false;

		for (Vec3 point : samples(box)) {
			if (clip(client.level, player, eye, point).getType() != HitResult.Type.MISS) continue;
			if (behindAPainting(eye, point)) continue;
			return true;
		}
		return false;
	}

	/**
	 * Whether the player can see the block position itself.
	 *
	 * <p>A block is its own obstruction, so a MISS is not the only pass: a ray that stops
	 * on the very block being asked about has reached it, which is what seeing it means.
	 * The two answers together are exactly "nothing is in the way", whether or not
	 * anything is standing there — so this reads the same for a wall and for the hole
	 * where a wall used to be.
	 */
	public static boolean canSee(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || client.level == null) return false;
		if (!inView(new AABB(pos))) return false;

		Vec3 eye = player.getEyePosition();
		Vec3 centre = Vec3.atCenterOf(pos);
		if (eye.distanceToSqr(centre) > MAX_RANGE * MAX_RANGE) return false;

		BlockHitResult hit = clip(client.level, player, eye, centre);
		boolean clearOfBlocks = hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
		return clearOfBlocks && !behindAPainting(eye, centre);
	}

	/**
	 * Whether a painting stands in the way.
	 *
	 * <p>Paintings are entities, not blocks, so {@code level.clip} goes straight through
	 * one as if it were not there. That matters because Hideonwalls sit behind them: the
	 * ray would happily report a clear line to a perch the player cannot see any part of,
	 * and the mark would claim one had been spotted when nothing had.
	 */
	private static boolean behindAPainting(Vec3 from, Vec3 to) {
		for (AABB painting : paintings()) {
			if (painting.clip(from, to).isPresent()) return true;
		}
		return false;
	}

	/** Every loaded painting's box, rebuilt at most once a second. */
	private static List<AABB> paintings() {
		long now = System.currentTimeMillis();
		if (now - paintingsAt < PAINTING_CACHE_MILLIS) return paintings;
		paintingsAt = now;

		Minecraft client = Minecraft.getInstance();
		List<AABB> found = new ArrayList<>();
		if (client.level != null) {
			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity instanceof Painting) found.add(entity.getBoundingBox());
			}
		}
		paintings = List.copyOf(found);
		return paintings;
	}

	/**
	 * Whether the box is inside the camera's view — the game's own cull frustum, so this
	 * is the same test that decides whether vanilla would bother drawing it.
	 *
	 * <p>Passes when there is no frustum yet, which is the case before the first frame
	 * has been drawn. The ray is the substantive gate; this one only ever narrows it, so
	 * failing open here cannot let anything through that the ray would have stopped.
	 */
	static boolean inView(AABB box) {
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		if (camera == null) return true;
		Frustum frustum = camera.getCullFrustum();
		return frustum == null || frustum.isVisible(box);
	}

	/**
	 * The points on a box worth aiming at: its centre, just inside the top and bottom,
	 * and two opposite corners of the middle. Enough to catch a critter half behind
	 * cover without paying for a ray per corner.
	 */
	private static Vec3[] samples(AABB box) {
		Vec3 centre = box.getCenter();
		double insetX = box.getXsize() * 0.25;
		double insetY = box.getYsize() * 0.1;
		double insetZ = box.getZsize() * 0.25;
		return new Vec3[]{
			centre,
			new Vec3(centre.x, box.maxY - insetY, centre.z),
			new Vec3(centre.x, box.minY + insetY, centre.z),
			new Vec3(centre.x - insetX, centre.y, centre.z - insetZ),
			new Vec3(centre.x + insetX, centre.y, centre.z + insetZ),
		};
	}

	/**
	 * {@link ClipContext.Block#VISUAL} rather than {@code COLLIDER}: what blocks sight is
	 * what is drawn, and the two differ on exactly the things this has to get right —
	 * glass stops you walking through it but not seeing through it.
	 */
	private static BlockHitResult clip(Level level, Player player, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(
			from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
	}
}
