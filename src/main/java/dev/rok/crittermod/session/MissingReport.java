package dev.rok.crittermod.session;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats a run's outstanding species as lines meant to be read by teammates:
 * <pre>
 * Icy missing: Wumpa Troodon
 * Cavern missing: Gemzie
 * </pre>
 *
 * <p>Biomes that are already complete are omitted rather than printed empty, so the
 * message stays short enough for one chat line per biome.
 */
public final class MissingReport {

	private MissingReport() {
	}

	/** One line per biome that still has uncaught species; empty if the dex is done. */
	public static List<String> lines(SafariSession session) {
		List<String> lines = new ArrayList<>();
		for (SafariBiome biome : SafariBiome.values()) {
			List<Critter> missing = session.missing(biome);
			if (missing.isEmpty()) continue;
			StringBuilder line = new StringBuilder(biome.displayName()).append(" missing:");
			for (Critter critter : missing) {
				line.append(' ').append(critter.name());
				// e.g. "Gemzie(1/3)" — tells the reader how many are left, not just that
				// something is outstanding.
				if (critter.hasQuota() && !TrackingMode.uniqueOnly()) {
					line.append('(').append(session.partyCatches(critter))
						.append('/').append(critter.spawnQuota()).append(')');
				}
			}
			lines.add(line.toString());
		}
		return lines;
	}

	/**
	 * The same report as a single clipboard-ready string. Falls back to a completion
	 * line when nothing is outstanding, so the clipboard is never silently empty.
	 */
	public static String text(SafariSession session) {
		List<String> lines = lines(session);
		if (lines.isEmpty()) return "All " + Critters.total() + " critters caught!";
		return String.join("\n", lines);
	}
}
