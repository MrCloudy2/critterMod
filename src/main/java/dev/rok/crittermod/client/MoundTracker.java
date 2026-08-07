package dev.rok.crittermod.client;

/**
 * Counts the Rockmite mounds broken this run, and how many held anything.
 *
 * <p>Mounds announce themselves in chat, so unlike the nests and walls nothing has to
 * be read out of the world at all:
 *
 * <pre>
 * The mound falls apart, but nothing is inside...
 * The mound fell apart, revealing a Rockmite hidden inside!
 * </pre>
 *
 * <p>Across the sample logs that split 80 empty to 23 with a Rockmite — a 22% hit rate
 * — which is why "how many are left" was never the useful figure. What matters is how
 * many have been broken, since each is another roll.
 */
public final class MoundTracker {

	private static int broken;
	private static int rockmites;

	private MoundTracker() {
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		// "Spider Mound" in Torrhus Canyon is unrelated; these two wordings are the
		// only ones that mean a Rockmite mound has just been finished off.
		if (line.startsWith("The mound falls apart, but nothing is inside")) {
			broken++;
		} else if (line.startsWith("The mound fell apart, revealing")) {
			broken++;
			rockmites++;
		}
	}

	public static int broken() {
		return broken;
	}

	public static int rockmites() {
		return rockmites;
	}

	public static void reset() {
		broken = 0;
		rockmites = 0;
	}
}
