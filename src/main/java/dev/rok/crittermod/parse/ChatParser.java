package dev.rok.crittermod.parse;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a stripped (no §-codes) Hypixel chat line into a {@link CritterEvent}.
 *
 * <p>The message grammar this handles, as observed across ~3,200 real events:
 * <pre>
 * CAPTURE! You caught a|an &lt;C&gt; and gained a|an|Nx &lt;C&gt; Shard!
 * CAPTURE! You found Hideyho, and as a reward he gave you a|Nx Hideyho Shard!
 * LOOT SHARE! You received a|an|Nx &lt;C&gt; Shard from &lt;player&gt; catching a|an &lt;C&gt;!
 * LOOT SHARE! You received a|Nx &lt;C&gt; Shard from &lt;player&gt; finding Hideyho!
 * LOOT SHARE! You received a Rainbow Feather and Nx &lt;C&gt; Shard from &lt;player&gt; catching a SPARKLING &lt;C&gt;!
 * </pre>
 *
 * <p>Rather than pin one regex per wording, the parser keys off the {@code CAPTURE!}
 * / {@code LOOT SHARE!} prefix and then resolves the species by roster lookup. Unseen
 * wordings — notably the self-catch form of a SPARKLING, which has never appeared in
 * the sample logs — still parse correctly as long as the species name is present.
 */
public final class ChatParser {

	private static final Pattern SHARD_AMOUNT = Pattern.compile("(\\d[\\d,]*)x\\s+\\S");
	private static final Pattern LOOT_SHARE_CATCHER =
		Pattern.compile("from\\s+(\\w{1,16})\\s+(?:catching|finding)\\b");
	private static final Pattern ATTEMPT =
		Pattern.compile("^You threw a Critter Capsule at the (.+)!$");
	private static final Pattern FAILED =
		Pattern.compile("^The (.+?) (?:escaped your Critter Capsule|dodged your critter capsule)\\b");
	private static final Pattern ENTERED =
		Pattern.compile("^(?:\\[[^]]+]\\s*)?(\\w{1,16}) entered Critter Safari!$");

	private ChatParser() {
	}

	/**
	 * Strips §-colour codes and the trailing duplicate counter that chat-compacting
	 * mods (chatpatches, enhanced_chat) append — {@code " (3)"}, {@code " (×3)"},
	 * {@code " [x3]"}. Those suffixes are display artefacts, not part of the message.
	 */
	public static String clean(String raw) {
		String text = raw.replaceAll("§.", "").trim();
		text = text.replaceAll("\\s*(?:\\(\\s*[x×]?\\s*\\d+\\s*\\)|\\[\\s*[x×]?\\s*\\d+\\s*])$", "");
		return text.trim();
	}

	/**
	 * Parses one already-{@linkplain #clean cleaned} chat line.
	 *
	 * @param selfName the local player's name, used to tell your own "entered Critter
	 *                 Safari!" line from a partymate's; may be {@code null}
	 * @return the event, or {@code null} if the line is not a Critter Safari event
	 */
	public static CritterEvent parse(String line, String selfName) {
		if (line.startsWith("CAPTURE!")) {
			Critter critter = Critters.findIn(line);
			if (critter == null) return null;
			return new CritterEvent(CritterEvent.Type.OWN_CATCH, critter, null,
				shardAmount(line), line.contains("SPARKLING"));
		}

		if (line.startsWith("LOOT SHARE!")) {
			Critter critter = Critters.findIn(line);
			if (critter == null) return null;
			Matcher catcher = LOOT_SHARE_CATCHER.matcher(line);
			if (!catcher.find()) return null;
			return new CritterEvent(CritterEvent.Type.SHARED_CATCH, critter, catcher.group(1),
				shardAmount(line), line.contains("SPARKLING"));
		}

		Matcher attempt = ATTEMPT.matcher(line);
		if (attempt.matches()) {
			Critter critter = Critters.byName(attempt.group(1));
			if (critter == null) return null;
			return new CritterEvent(CritterEvent.Type.ATTEMPT, critter, null, 0, false);
		}

		Matcher failed = FAILED.matcher(line);
		if (failed.find()) {
			Critter critter = Critters.byName(failed.group(1));
			if (critter == null) return null;
			return new CritterEvent(CritterEvent.Type.FAILED, critter, null, 0, false);
		}

		Matcher entered = ENTERED.matcher(line);
		if (entered.matches() && selfName != null && selfName.equals(entered.group(1))) {
			return new CritterEvent(CritterEvent.Type.ENTERED_SAFARI, null, selfName, 0, false);
		}

		return null;
	}

	/**
	 * Reads the shard count out of a catch message. {@code "gained 2x Foxtrot Shard"}
	 * yields 2; {@code "gained a Foxtrot Shard"} has no numeral and yields 1.
	 */
	private static int shardAmount(String line) {
		Matcher matcher = SHARD_AMOUNT.matcher(line);
		if (!matcher.find()) return 1;
		try {
			return Integer.parseInt(matcher.group(1).replace(",", ""));
		} catch (NumberFormatException e) {
			return 1;
		}
	}
}
