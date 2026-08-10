package dev.rok.crittermod.session;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats a run's outstanding species as lines meant to be read by teammates:
 * <pre>
 * Icy missing: Wumpa, Troodon (1/3)
 * Cavern missing: Gemzie (1/3)
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
			for (int i = 0; i < missing.size(); i++) {
				Critter critter = missing.get(i);
				// Comma-separated, because a species and its count are both spaced and
				// "Troodon (1/3) Gemzie" would read as one thing.
				line.append(i == 0 ? " " : ", ").append(critter.name());
				if (session.isUnavailable(critter)) {
					line.append(" (none)");
					continue;
				}
				// e.g. "Gemzie (1/3)" — tells the reader how many are left, not just that
				// something is outstanding.
				int total = session.required(critter);
				if (total > 1 && !TrackingMode.uniqueOnly()) {
					line.append(" (").append(session.partyCatches(critter))
						.append('/').append(total).append(')');
				}
			}
			lines.add(line.toString());
		}
		return lines;
	}

	/**
	 * The same report as one string, for the clipboard.
	 *
	 * <p>Joined with a visible separator rather than newlines. The clipboard is nearly
	 * always pasted straight into chat, and Minecraft's chat box drops the line breaks —
	 * which ran the sections together as "…DriftlingIcy missing:…". Falls back to a
	 * completion line when nothing is outstanding, so it is never silently empty.
	 */
	public static String text(SafariSession session) {
		List<String> lines = lines(session);
		if (lines.isEmpty()) return "All " + Critters.total() + " critters caught!";
		return String.join(" | ", lines);
	}
}
