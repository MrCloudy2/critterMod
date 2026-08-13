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

	/** What an import did: runs that were new, and old ones it could fill in. */
	public record Imported(int added, int filled) {
		public boolean changedAnything() {
			return added > 0 || filled > 0;
		}
	}

	/**
	 * Adds runs read from somewhere else, and fills in what the ones already held are
	 * missing.
	 *
	 * <p>A run saved before the shard breakdown existed knows how many shards it gave but
	 * not of what, so it can never be priced. The logs still say, and the same parser
	 * reads them, so re-importing backfills the breakdown onto the run that is already
	 * there rather than adding a second copy of it.
	 *
	 * <p>Nothing else is overwritten. A saved run's catch counts are what was actually
	 * seen at the time; a replay of a log that rotated mid-run would only know less.
	 */
	public static Imported importRuns(List<SafariSession> sessions) {
		int added = 0;
		int filled = 0;
		for (SafariSession session : sessions) {
			RunRecord record = RunRecord.of(session);
			if (record.partyTotal() < MIN_CATCHES) continue;

			RunRecord existing = sameRun(record);
			if (existing != null) {
				if (!existing.hasShardData() && record.hasShardData()) {
					existing.shards = record.shards;
					filled++;
				}
				continue;
			}
			runs.add(record);
			added++;
		}
		runs.sort((a, b) -> Long.compare(a.started, b.started));
		while (runs.size() > MAX_RUNS) runs.removeFirst();
		Imported result = new Imported(added, filled);
		if (result.changedAnything()) save();
		return result;
	}

	/**
	 * The saved run {@code record} is a replay of, or {@code null} if it is a new one.
	 *
	 * <p>Matched on overlapping time rather than on an equal start: a live run opens when
	 * you arrive, while a replayed one opens at its first chat line, so the same run is
	 * saved under two start times seconds apart. Half of the shorter run has to be shared
	 * before they count as the same, because runs come back to back — one starts seconds
	 * after the last one ended, and anything looser welds two of them together.
	 */
	private static RunRecord sameRun(RunRecord record) {
		RunRecord best = null;
		long bestOverlap = 0;
		for (RunRecord existing : runs) {
			long shared = Math.min(existing.ended, record.ended)
				- Math.max(existing.started, record.started);
			if (shared <= 0 || shared <= bestOverlap) continue;
			if (shared * 2 < Math.min(existing.durationMillis(), record.durationMillis())) continue;
			best = existing;
			bestOverlap = shared;
		}
		return best;
	}

	/** Saved runs that kept a shard breakdown, and so can be priced. */
	public static int pricedRuns() {
		return (int) runs.stream().filter(RunRecord::hasShardData).count();
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
