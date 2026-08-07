package dev.rok.crittermod.parse;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the roaming Hunter NPCs' trade dialogs.
 *
 * <p>These NPCs spawn around the Safari and each offers one shard for one quest item.
 * The catch is that the shard and the price arrive as two separate lines, and every
 * NPC words both differently:
 *
 * <pre>
 * Hunter Billy      I found this really cool &lt;C&gt; Shard on the floor around here.
 *                   I'll trade it to you in exchange for a &lt;item&gt;.
 * Hunter Dennis     I've got a &lt;C&gt; Shard you can h-h-have, if you w-w-want it...
 *                   You can h-h-have it if you give m-m-me a &lt;item&gt;...
 * Hunter Harry      Say, do you have a use for a &lt;C&gt; Shard? I found it lying around...
 *                   I'll give you it in exchange for a &lt;item&gt;!
 * Huntress Melissa  Do you want this &lt;C&gt; Shard? I already maxed that Attribute...
 *                   How about I give you it in exchange for, say, a &lt;item&gt;?
 * </pre>
 *
 * <p>Rather than four pairs of sentence patterns, an offer is any NPC line naming a
 * shard, and a price is any NPC line asking for something in exchange. The pending
 * offer is held per speaker, so a completed trade is an offer plus the next price
 * line from that same NPC.
 */
public final class TraderParser {

	/** {@code [NPC] <name>: <text>} */
	private static final Pattern NPC_LINE = Pattern.compile("^\\[NPC] ([^:]{1,40}): (.*)$");

	/** The wordings that state a price, across all four traders. */
	private static final Pattern PRICE = Pattern.compile(
		"(?:in exchange for(?:,\\s*say,)?|give m-m-me)\\s+(?:an?|the)\\s+(.+?)\\s*[.!?…]*$");

	/** Speaker -> the shard they have already offered but not yet priced. */
	private final Map<String, Critter> pendingOffers = new HashMap<>();

	/** Speaker whose offer the most recent {@link #parse} call registered, if any. */
	private String offerJustRegistered;

	/** A complete trade: {@code npc} will hand over {@code critter}'s shard for {@code item}. */
	public record TradeOffer(String npc, Critter critter, String item) {
	}

	/**
	 * Feeds one cleaned chat line.
	 *
	 * @return the trade once its price line lands, or {@code null} for anything else
	 */
	public TradeOffer parse(String line) {
		offerJustRegistered = null;

		Matcher npcLine = NPC_LINE.matcher(line);
		if (!npcLine.matches()) return null;

		String npc = npcLine.group(1).trim();
		String text = npcLine.group(2);

		Matcher price = PRICE.matcher(text);
		if (price.find()) {
			Critter critter = pendingOffers.remove(npc);
			if (critter == null) return null;
			String item = price.group(1).trim();
			return item.isEmpty() ? null : new TradeOffer(npc, critter, item);
		}

		// Not a price, so see whether it is this NPC offering a shard. "Shard" alone
		// is not enough — plenty of NPC flavour text mentions shards without offering
		// one — so it also has to name a species.
		if (text.contains("Shard")) {
			Critter critter = Critters.findIn(text);
			if (critter != null) {
				pendingOffers.put(npc, critter);
				offerJustRegistered = npc;
			}
		}
		return null;
	}

	/**
	 * The speaker whose offer the last {@link #parse} call registered, or {@code null}.
	 *
	 * <p>Lets the caller note where the player was standing when the dialog opened,
	 * rather than when it finished — the two lines are seconds apart, and by the price
	 * line the player may already have moved away from the NPC.
	 */
	public String offerJustRegistered() {
		return offerJustRegistered;
	}

	/** Drops any half-seen dialog, e.g. when a run ends. */
	public void reset() {
		pendingOffers.clear();
		offerJustRegistered = null;
	}
}
