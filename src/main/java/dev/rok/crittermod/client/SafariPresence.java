package dev.rok.crittermod.client;

/**
 * Chat-driven "am I in the Safari" flag.
 *
 * <p>Hypixel announces entering ({@code <player> entered Critter Safari!}) but never
 * announces leaving, so the exit signal is the server transfer that always
 * accompanies it ({@code Sending you to server mini…}).
 *
 * <p>Kept separate from {@link AreaDetector} and the session manager so neither has
 * to depend on the other.
 */
public final class SafariPresence {

	private static boolean entered;

	private SafariPresence() {
	}

	/** Reacts to a cleaned chat line; returns true if it changed the state. */
	public static boolean onChatMessage(String line) {
		if (line.endsWith("entered Critter Safari!")) {
			// Fires for partymates too, but any such line means this client is in
			// the Safari instance, which is all this flag claims.
			boolean changed = !entered;
			entered = true;
			return changed;
		}
		if (line.startsWith("Sending you to server") || line.startsWith("Sending you to mini")) {
			boolean changed = entered;
			entered = false;
			return changed;
		}
		return false;
	}

	public static boolean inSafari() {
		return entered;
	}

	public static void set(boolean value) {
		entered = value;
	}
}
