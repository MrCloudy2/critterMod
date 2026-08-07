package dev.rok.crittermod.client;

/**
 * Tracks whether the player is inside the Critter Safari.
 *
 * <p>Hypixel announces entering ({@code <player> entered Critter Safari!}) but never
 * announces leaving, and no server-transfer message accompanies the exit either — so
 * there is no "you left" event to listen for.
 *
 * <p>Instead, presence remembers which island the sidebar named when it was set. The
 * SkyBlock sidebar always carries a {@code ⏣ <island>} line, so once that line changes
 * to something else the player has demonstrably moved, and presence clears itself.
 * That works without needing to know what the Safari's own island line says.
 */
public final class SafariPresence {

	private static boolean entered;
	/** The {@code ⏣ …} sidebar line as it read when presence was last set. */
	private static String islandWhenEntered;

	private SafariPresence() {
	}

	/** Reacts to a cleaned chat line; returns true if it changed the state. */
	public static boolean onChatMessage(String line) {
		if (line.endsWith("entered Critter Safari!")) {
			// Fires for partymates too, but any such line means this client is in
			// the Safari instance, which is all this flag claims.
			boolean changed = !entered;
			enter(AreaDetector.islandLine());
			return changed;
		}
		// Kept because it is a genuine transfer signal where it does appear, even
		// though leaving the Safari does not produce one.
		if (line.startsWith("Sending you to server") || line.startsWith("Sending you to mini")) {
			boolean changed = entered;
			clear();
			return changed;
		}
		return false;
	}

	public static boolean inSafari() {
		return entered;
	}

	/** Marks the player present, anchored to the island the sidebar currently names. */
	public static void enter(String islandLine) {
		entered = true;
		if (islandLine != null) islandWhenEntered = islandLine;
	}

	public static void clear() {
		entered = false;
		islandWhenEntered = null;
	}

	/**
	 * True when the sidebar now names a different island than it did when presence
	 * was set — the only reliable evidence that the player has left.
	 */
	public static boolean movedAwayFrom(String currentIslandLine) {
		return entered
			&& islandWhenEntered != null
			&& currentIslandLine != null
			&& !islandWhenEntered.equals(currentIslandLine);
	}

	/** For {@code /critters debug}. */
	public static String islandWhenEntered() {
		return islandWhenEntered;
	}
}
