package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.parse.ChatParser;
import dev.rok.crittermod.parse.CritterEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Remembers where a critter was when you threw at it, so you can be waiting for it.
 *
 * <p>Throwing a capsule takes the critter out of the world and puts a CAPTURING ball off
 * to one side. If the capture fails the critter comes back to the spot it left from and
 * starts running again — and the fast ones are gone before you have found them a second
 * time. The spot is known the moment the throw lands, so it gets pinned and drawn, and
 * the next capsule can be in the air before the critter is.
 *
 * <p>All from the chat lines Hypixel already sends and the entity that was there:
 *
 * <ul>
 * <li>{@code You threw a Critter Capsule at the Rockmite!} pins the last place its body
 *     was seen, at the size it was;
 * <li>{@code The Rockmite escaped your Critter Capsule!} holds the pin — that is exactly
 *     the moment it is worth having;
 * <li>{@code CAPTURE!} clears it, and so does the critter running off, or time passing.
 * </ul>
 *
 * <p>One pin at a time: capsules are thrown one at a time, and a second throw is the
 * answer to where the interesting spot now is.
 *
 * <p>Not every species is worth it. Commons are caught on the throw, so there is no
 * second attempt to prepare for, and Hideyho, Wumpa and Doomspiral do not come straight
 * back — see {@link #worthPinning}.
 */
public final class RecatchSpots {

	/** Dropped after this long with nothing resolving it, so a stale box cannot linger. */
	private static final long HOLD_MILLIS = 40_000;
	/** A sighting older than this is not what the throw hit. */
	private static final long SIGHTING_MILLIS = 10_000;
	/** Within a scan or two, so the species counts as being in the world right now. */
	private static final long LOADED_MILLIS = 1_000;
	/** Once it is this far from the pin it is running again and the pin means nothing. */
	private static final double STALE_DISTANCE = 5.0;
	/** Added to the score of anything behind the player, so it always loses to what is not. */
	private static final double BEHIND_PENALTY = 1000.0;

	/** Species that do not simply reappear where they were, whatever their rarity. */
	private static final Set<String> NEVER_PINNED = Set.of("Hideyho", "Wumpa", "Doomspiral");

	/** Where a species' body was last seen, and how big it was. */
	private record Seen(AABB box, long millis) {
	}

	private static final Map<Critter, Seen> lastSeen = new HashMap<>();

	/** The sweep these sightings came from, so a cached list is not re-timestamped. */
	private static long lastScan;

	private static Critter pinnedCritter;
	private static AABB pinnedBox;
	private static long pinnedAt;

	private RecatchSpots() {
	}

	/** The spot to throw at, or {@code null} when there is nothing pinned. */
	public static AABB pinned() {
		return pinnedBox;
	}

	/** The species the pin belongs to, or {@code null}. */
	public static Critter pinnedCritter() {
		return pinnedCritter;
	}

	public static void tick() {
		long now = System.currentTimeMillis();

		if (CritterEntities.scannedAt() != lastScan) {
			lastScan = CritterEntities.scannedAt();
			Player player = Minecraft.getInstance().player;
			Map<Critter, Double> best = new HashMap<>();
			for (CritterEntities.Sighting sighting : CritterEntities.all()) {
				AABB box = sighting.body().getBoundingBox();
				double score = aimScore(player, box);
				// Several of a species can be in view at once, and only one of them was
				// thrown at. The one nearest the line you are looking along is the one.
				Double current = best.get(sighting.critter());
				if (current != null && current <= score) continue;
				best.put(sighting.critter(), score);
				lastSeen.put(sighting.critter(), new Seen(box, now));
			}
		}

		if (pinnedBox == null) return;

		if (now - pinnedAt > HOLD_MILLIS) {
			clear();
			return;
		}

		// Back and running: the critter is loaded again and has left the spot, so the
		// spot is now just a place it used to be.
		Seen seen = lastSeen.get(pinnedCritter);
		boolean loadedNow = seen != null && now - seen.millis() < LOADED_MILLIS;
		if (loadedNow && seen.box().getCenter().distanceTo(pinnedBox.getCenter()) > STALE_DISTANCE) {
			clear();
		}
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		if (!ConfigManager.get().display.recatchHelper) return;

		// selfName only matters for the entry banner, which is not one of the events
		// this cares about.
		CritterEvent event = ChatParser.parse(line, null);
		if (event == null || event.critter() == null) return;

		switch (event.type()) {
			case ATTEMPT -> pin(event.critter());
			// The throw failed, so it is coming back to where it was. Hold the pin and
			// restart the clock rather than letting the throw's timer run it out.
			case FAILED -> {
				if (event.critter().equals(pinnedCritter)) pinnedAt = System.currentTimeMillis();
			}
			case OWN_CATCH -> {
				if (event.critter().equals(pinnedCritter)) clear();
			}
			default -> {
			}
		}
	}

	/**
	 * How far a box is off the line the player is looking along — lower is more likely
	 * to be what a capsule was aimed at.
	 *
	 * <p>Anything behind the player scores by plain distance instead, pushed out beyond
	 * anything in front, so a critter at your back only wins if it is the only one.
	 */
	private static double aimScore(Player player, AABB box) {
		if (player == null) return Double.MAX_VALUE;
		Vec3 toBox = box.getCenter().subtract(player.getEyePosition());
		Vec3 look = player.getViewVector(1.0f);
		double along = toBox.dot(look);
		if (along <= 0) return BEHIND_PENALTY + toBox.length();
		return toBox.subtract(look.scale(along)).length();
	}

	/**
	 * Whether a failed throw at this species is worth marking the spot for.
	 *
	 * <p>Commons are caught on the throw, so there is never a second attempt to prepare
	 * for. Hideyho, Wumpa and Doomspiral do not come straight back either — they are set
	 * pieces with their own pacing, and a box left where one used to stand is only in the
	 * way. Everything else is the case this exists for: it reappears, it runs, and you
	 * want the next capsule already aimed.
	 */
	private static boolean worthPinning(Critter critter) {
		if (critter.rarity() == Critter.Rarity.COMMON) return false;
		return !NEVER_PINNED.contains(critter.name());
	}

	private static void pin(Critter critter) {
		if (!worthPinning(critter)) {
			clear();
			return;
		}

		Seen seen = lastSeen.get(critter);
		// A species nobody has seen recently cannot be pinned — the throw was at
		// something out of the client's view, and guessing a spot would be worse than
		// showing none.
		if (seen == null || System.currentTimeMillis() - seen.millis() > SIGHTING_MILLIS) {
			clear();
			return;
		}
		pinnedCritter = critter;
		pinnedBox = seen.box();
		pinnedAt = System.currentTimeMillis();
	}

	public static void clear() {
		pinnedCritter = null;
		pinnedBox = null;
	}

	/** Forgotten between runs; a spot from the last one is meaningless in this one. */
	public static void reset() {
		lastSeen.clear();
		clear();
	}

	/** Distance from the player to the pin, or -1 when there is nothing pinned. */
	public static double distance() {
		Minecraft client = Minecraft.getInstance();
		if (pinnedBox == null || client.player == null) return -1;
		return client.player.position().distanceTo(pinnedBox.getCenter());
	}
}
