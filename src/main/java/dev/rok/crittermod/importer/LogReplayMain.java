package dev.rok.crittermod.importer;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.session.SafariSession;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Standalone entry point: replays a Minecraft logs directory and prints every
 * past Critter Safari run. Run via {@code ./gradlew replayLogs -Plogs=<dir>}.
 *
 * <p>Touches no Minecraft classes, so it doubles as the parser's test harness.
 */
public final class LogReplayMain {

	private static final DateTimeFormatter STAMP =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private static final String DEFAULT_LOGS =
		System.getProperty("user.home")
			+ "/.local/share/atlauncher/instances/SkyBlockEnhancedModernEdition/logs";

	public static void main(String[] args) throws Exception {
		Path logsDir = Path.of(args.length > 0 ? args[0] : DEFAULT_LOGS);
		String selfName = args.length > 1 ? args[1] : null;

		List<SafariSession> sessions = LogScanner.scan(logsDir, selfName);
		System.out.printf("Scanned %s — %d Critter Safari run(s)%n%n", logsDir, sessions.size());

		SafariSession best = null;
		for (int i = 0; i < sessions.size(); i++) {
			SafariSession session = sessions.get(i);
			printSession(i + 1, session);
			if (best == null || session.partyUnique() > best.partyUnique()) best = session;
		}

		if (best != null) {
			System.out.println("=".repeat(64));
			System.out.printf("Best run by party dex: %d/%d species%n", best.partyUnique(), Critters.total());
		}
		printLifetime(sessions);
	}

	private static void printSession(int index, SafariSession session) {
		System.out.printf("--- Run #%d  %s  (%d min)%n", index,
			STAMP.format(Instant.ofEpochMilli(session.startedAtMillis())),
			session.durationMillis() / 60_000);

		System.out.printf("    party %2d/%d unique, %4d caught   |   you %2d/%d unique, %4d caught%n",
			session.partyUnique(), Critters.total(), session.partyTotal(),
			session.ownUnique(), Critters.total(), session.ownTotal());

		for (SafariBiome biome : SafariBiome.values()) {
			int max = Critters.totalIn(biome);
			System.out.printf("      %-8s party %d/%d%s  you %d/%d%n",
				biome.displayName(),
				session.partyUnique(biome), max, session.biomeComplete(biome) ? " *" : "  ",
				session.ownUnique(biome), max);
		}

		Map<String, Map<SafariBiome, Integer>> perPlayer = session.uniquePerPlayer();
		if (perPlayer.size() > 1) {
			System.out.println("    per player (unique per biome):");
			perPlayer.forEach((player, counts) -> System.out.printf("      %-18s %s%n", player,
				counts.isEmpty() ? "-" : counts.entrySet().stream()
					.map(e -> e.getKey().displayName() + " " + e.getValue())
					.reduce((a, b) -> a + ", " + b).orElse("-")));
		}

		if (!session.sparklings().isEmpty()) {
			System.out.println("    SPARKLING: " + session.sparklings().stream()
				.map(Critter::name).reduce((a, b) -> a + ", " + b).orElse(""));
		}
		System.out.println();
	}

	/** Aggregate across every run, to spot species the parser never resolved. */
	private static void printLifetime(List<SafariSession> sessions) {
		System.out.println("\nSpecies never seen in any run (parser gaps or genuinely uncaught):");
		for (SafariBiome biome : SafariBiome.values()) {
			List<String> never = Critters.inBiome(biome).stream()
				.filter(c -> sessions.stream().noneMatch(s -> s.caughtByParty(c)))
				.map(Critter::name).toList();
			System.out.printf("  %-8s %s%n", biome.displayName(), never.isEmpty() ? "(none)" : String.join(", ", never));
		}
	}
}
