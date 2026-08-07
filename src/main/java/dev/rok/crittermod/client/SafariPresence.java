package dev.rok.crittermod.client;

/**
 * Chat-driven fallback for "am I in the Critter Safari".
 *
 * <p>The scoreboard is the real source — see {@link AreaDetector#inSafari()} — and it
 * overwrites this flag every tick. This only covers the gap where the sidebar has not
 * loaded yet, such as the moment right after a server transfer.
 *
 * <p>Hypixel announces entering ({@code <player> entered Critter Safari!}) but never
 * announces leaving, which is why nothing here can be trusted to clear itself.
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
