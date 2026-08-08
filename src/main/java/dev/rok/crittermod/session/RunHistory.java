package dev.rok.crittermod.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every run this client has finished, kept across restarts.
 *
 * <p>A run is written the moment the next one starts, which is also when it stops being
 * the run {@code /ct} shows. Nothing is ever written mid-run: a run in progress is
 * still changing, and half of one is not worth keeping.
 *
 * <p>The file is a plain list of {@link RunRecord}, so it can be read, edited or thrown
 * away by hand. An unreadable file costs the history, not the session — it is replaced
 * on the next save rather than being allowed to take the mod down with it.
 */
public final class RunHistory {

	/** Enough for months of play; the file stays small and the stats stay honest. */
	private static final int MAX_RUNS = 500;
	/** Runs with nothing in them are noise — leaving and re-entering makes plenty. */
	private static final int MIN_CATCHES = 1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final List<RunRecord> runs = new ArrayList<>();
	private static Path file;

	/** One species' record across every saved run. */
	public record SpeciesStat(Critter critter, int total, int runsSeen, int best) {
		/** Average per run over the runs it turned up in, not over every run. */
		public double perRunSeen() {
			return runsSeen == 0 ? 0 : (double) total / runsSeen;
		}
	}

	private RunHistory() {
	}

	/** Points the history at a file and reads whatever is in it. */
	public static void load(Path path) {
		file = path;
		runs.clear();
		if (path == null || !Files.isRegularFile(path)) return;

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			List<RunRecord> loaded = GSON.fromJson(reader,
				new TypeToken<List<RunRecord>>() {
				}.getType());
			if (loaded != null) runs.addAll(loaded);
		} catch (IOException | RuntimeException unreadable) {
			// Left in place rather than deleted: the next save overwrites it, and if
			// something else is wrong the file is still there to look at.
			runs.clear();
		}
	}

	/**
	 * Saves a finished run.
	 *
	 * <p>An empty run is dropped. Walking through the entrance and out again produces
	 * one, and a history full of those buries the runs that happened.
	 */
	public static void record(SafariSession session) {
		if (session == null) return;
		RunRecord record = RunRecord.of(session);
		if (record.partyTotal() < MIN_CATCHES) return;

		runs.add(record);
		while (runs.size() > MAX_RUNS) runs.removeFirst();
		save();
	}

	/** Saved runs, oldest first. */
	public static List<RunRecord> runs() {
		return Collections.unmodifiableList(runs);
	}

	public static int size() {
		return runs.size();
	}

	/** Drops everything, on disk as well. */
	public static void clear() {
		runs.clear();
		save();
	}

	/**
	 * Adds runs read from somewhere else, skipping any that are already held.
	 *
	 * <p>Matched on start time, so importing the same logs twice does not double the
	 * history.
	 *
	 * @return how many were actually new
	 */
	public static int importRuns(List<SafariSession> sessions) {
		int added = 0;
		for (SafariSession session : sessions) {
			RunRecord record = RunRecord.of(session);
			if (record.partyTotal() < MIN_CATCHES) continue;
			if (runs.stream().anyMatch(existing -> existing.started == record.started)) continue;
			runs.add(record);
			added++;
		}
		runs.sort((a, b) -> Long.compare(a.started, b.started));
		while (runs.size() > MAX_RUNS) runs.removeFirst();
		if (added > 0) save();
		return added;
	}

	// --- stats ---------------------------------------------------------------

	/** Every species with its totals across the saved runs, most-caught first. */
	public static List<SpeciesStat> speciesStats() {
		List<SpeciesStat> stats = new ArrayList<>();
		for (Critter critter : Critters.all()) {
			int total = 0;
			int runsSeen = 0;
			int best = 0;
			for (RunRecord run : runs) {
				int caught = run.caught(critter);
				if (caught == 0) continue;
				total += caught;
				runsSeen++;
				best = Math.max(best, caught);
			}
			stats.add(new SpeciesStat(critter, total, runsSeen, best));
		}
		return stats;
	}

	/** The stat for one species, so a view can look one up without scanning. */
	public static SpeciesStat statFor(Critter critter) {
		return speciesStats().stream()
			.filter(stat -> stat.critter().equals(critter))
			.findFirst()
			.orElse(new SpeciesStat(critter, 0, 0, 0));
	}

	public static long totalTimeMillis() {
		return runs.stream().mapToLong(RunRecord::durationMillis).sum();
	}

	public static int totalCatches() {
		return runs.stream().mapToInt(RunRecord::partyTotal).sum();
	}

	public static int ownCatches() {
		return runs.stream().mapToInt(RunRecord::ownTotal).sum();
	}

	public static int totalShards() {
		return runs.stream().mapToInt(run -> run.totalShards).sum();
	}

	/** The best party dex any saved run reached. */
	public static int bestDex() {
		return runs.stream().mapToInt(RunRecord::partyUnique).max().orElse(0);
	}

	/** How many runs went all the way to every species. */
	public static int perfectRuns() {
		int total = Critters.total();
		return (int) runs.stream().filter(run -> run.partyUnique() == total).count();
	}

	/** Species that have never once been caught in a saved run. */
	public static List<Critter> neverCaught() {
		return speciesStats().stream()
			.filter(stat -> stat.total() == 0)
			.map(SpeciesStat::critter)
			.toList();
	}

	private static void save() {
		if (file == null) return;
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(runs, writer);
			}
		} catch (IOException | RuntimeException failed) {
			// Losing the history is not worth interrupting a run over.
		}
	}
}
