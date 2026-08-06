package dev.rok.crittermod.importer;

import dev.rok.crittermod.parse.ChatParser;
import dev.rok.crittermod.parse.CritterEvent;
import dev.rok.crittermod.session.SafariSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Replays a Minecraft {@code logs/} directory through {@link ChatParser} and
 * reconstructs each past Critter Safari run as a {@link SafariSession}.
 *
 * <p>This is the historical/backfill path. Live tracking hooks chat directly and
 * does not go through here, but both share the same parser and session model, so
 * replaying old logs is also how the parser gets verified without launching the
 * game.
 */
public final class LogScanner {

	/** {@code [19:50:30] [Render thread/INFO]: [CHAT] <message>} */
	private static final Pattern CHAT_LINE =
		Pattern.compile("^\\[(\\d{2}):(\\d{2}):(\\d{2})].*?\\[CHAT] (.*)$");
	private static final Pattern SETTING_USER = Pattern.compile("Setting user: (\\w{1,16})");
	/** {@code 2026-08-05-3.log.gz} — the date a rotated log covers. */
	private static final Pattern LOG_FILE_DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})-\\d+\\.log(?:\\.gz)?$");

	/** A run is considered over after this long with no Critter Safari activity. */
	private static final long SESSION_GAP_MILLIS = 15 * 60 * 1000L;

	private LogScanner() {
	}

	/**
	 * Scans every {@code *.log} and {@code *.log.gz} in {@code logsDir}, oldest first.
	 *
	 * @param selfName the local player's name, or {@code null} to auto-detect it from
	 *                 the {@code Setting user:} line the launcher writes
	 * @return one session per reconstructed run, in chronological order
	 */
	public static List<SafariSession> scan(Path logsDir, String selfName) throws IOException {
		List<Path> files;
		try (var stream = Files.list(logsDir)) {
			files = stream
				.filter(p -> {
					String name = p.getFileName().toString();
					return name.endsWith(".log") || name.endsWith(".log.gz");
				})
				.sorted(Comparator.comparing(LogScanner::sortKey))
				.toList();
		}

		List<SafariSession> sessions = new ArrayList<>();
		SafariSession current = null;
		long lastEventMillis = 0;
		String player = selfName;

		for (Path file : files) {
			LocalDate date = dateOf(file);
			for (String rawLine : readLines(file)) {
				if (player == null) {
					Matcher user = SETTING_USER.matcher(rawLine);
					if (user.find()) player = user.group(1);
				}

				Matcher chat = CHAT_LINE.matcher(rawLine);
				if (!chat.matches()) continue;

				long timestamp = toMillis(date, chat.group(1), chat.group(2), chat.group(3));

				// A multi-line message is logged with a literal "\n"; split it so the
				// "<player> entered Critter Safari!" inside a banner is still seen.
				for (String part : chat.group(4).split("\\\\n")) {
					String line = ChatParser.clean(part);
					if (line.isEmpty()) continue;

					CritterEvent event = ChatParser.parse(line, player);
					if (event == null) continue;

					boolean startsRun = event.type() == CritterEvent.Type.ENTERED_SAFARI
						|| current == null
						|| timestamp - lastEventMillis > SESSION_GAP_MILLIS;

					if (startsRun) {
						if (current != null && !current.isEmpty()) sessions.add(current);
						current = new SafariSession(player, timestamp);
					}

					lastEventMillis = timestamp;
					current.record(event, timestamp);
				}
			}
		}

		if (current != null && !current.isEmpty()) sessions.add(current);
		return sessions;
	}

	private static List<String> readLines(Path file) throws IOException {
		List<String> lines = new ArrayList<>();
		boolean gzipped = file.getFileName().toString().endsWith(".gz");
		try (InputStream in = Files.newInputStream(file);
			 InputStream decoded = gzipped ? new GZIPInputStream(in) : in;
			 BufferedReader reader = new BufferedReader(new InputStreamReader(decoded, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) lines.add(line);
		} catch (IOException e) {
			// A truncated or actively-written log should not abort the whole scan.
			return lines;
		}
		return lines;
	}

	/** Rotated logs carry their date in the filename; {@code latest.log} uses its mtime. */
	private static LocalDate dateOf(Path file) {
		Matcher matcher = LOG_FILE_DATE.matcher(file.getFileName().toString());
		if (matcher.matches()) {
			return LocalDate.of(Integer.parseInt(matcher.group(1)),
				Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
		}
		try {
			return LocalDate.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
		} catch (IOException e) {
			return LocalDate.EPOCH;
		}
	}

	/** Orders rotated logs by date then rotation index, with {@code latest.log} last. */
	private static String sortKey(Path file) {
		String name = file.getFileName().toString();
		if (!LOG_FILE_DATE.matcher(name).matches()) return "9999" + name;
		String[] parts = name.replace(".log.gz", "").replace(".log", "").split("-");
		return "%s-%s-%s-%04d".formatted(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]));
	}

	private static long toMillis(LocalDate date, String h, String m, String s) {
		LocalTime time = LocalTime.of(Integer.parseInt(h), Integer.parseInt(m), Integer.parseInt(s));
		return LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}
}
