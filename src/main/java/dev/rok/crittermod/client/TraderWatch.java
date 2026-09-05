package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.parse.TraderParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches the roaming Hunter NPCs' shard-for-item trades.
 *
 * <p>Their dialog is only shown to whoever clicked them, so an offer routinely goes
 * unused while a partymate is carrying exactly the item it wants. Each complete offer
 * is reported to chat, optionally posted to the party, and kept for the on-screen
 * tracker so it can still be found later in the run.
 */
public final class TraderWatch {

	/** The same offer re-read by clicking the NPC again should not re-announce. */
	private static final long REPEAT_COOLDOWN_MILLIS = 5 * 60 * 1000L;
	/** Bounds the tracker box; the oldest trade drops off beyond this. */
	private static final int MAX_TRACKED = 6;
	/** How often the Hunters' positions are refreshed. They barely move. */
	private static final int SCAN_INTERVAL_TICKS = 20;

	/**
	 * A party line, as it reads once the formatting is stripped:
	 * {@code Party > [VIP] Someone: <what they said>}. The rank is optional.
	 */
	private static final Pattern PARTY_LINE =
		Pattern.compile("^Party > (?:\\[[^]]+] )?(\\w+): (.+)$");

	/**
	 * This mod's own trade announcement, read back off party chat:
	 * {@code Hunter Dennis (Icy -106 87 -7): Gimmiegold Shard for Purple Gem}.
	 *
	 * <p>The biome is optional because the announcement leaves it out when the position
	 * fell outside the mapped areas.
	 */
	private static final Pattern SHARED_TRADE = Pattern.compile(
		"^(.+?) \\((?:([A-Za-z]+) )?(-?\\d+) (-?\\d+) (-?\\d+)\\): (.+?) Shard for (.+)$");

	/**
	 * The one line that says a trade has been taken, whoever took it.
	 *
	 * <p>Each Hunter thanks you in their own words — Dennis stutters, Billy says "Done
	 * deal!", Melissa "Yippee!" — and Harry sometimes says nothing at all, so none of
	 * those is the signal. This plain line follows every completed trade in the logs and
	 * is the same for all four.
	 *
	 * <p>It names the species, not the shard: {@code You have been given a Wumpa!} is what
	 * arrives after taking a Wumpa Shard.
	 */
	private static final Pattern TRADE_TAKEN =
		Pattern.compile("^You have been given an? (.+)!$");

	/**
	 * The same thing in Hypixel's current wording, which is what actually arrives today:
	 * {@code You received a Billygoat Shard!}
	 *
	 * <p>Both are kept because both have been seen in real logs — the older one through
	 * August, this one now — and there is no telling which a given trade will use.
	 *
	 * <p>Anchored at the start, which is the whole defence against {@code LOOT SHARE! You
	 * received a Billygoat Shard from someone catching a Billygoat!}. That line is far
	 * commoner than this one and matching it would retire a trade every time a partymate
	 * caught anything.
	 */
	private static final Pattern SHARD_RECEIVED =
		Pattern.compile("^You received (?:an? |\\d+x )?(.+) Shard!$");

	/**
	 * This mod's own "taken" announcement, read back off party chat.
	 *
	 * <p>Deliberately unlike {@link #SHARED_TRADE}, which carries coordinates in brackets,
	 * so neither pattern can match the other's line.
	 */
	private static final Pattern SHARED_TAKEN = Pattern.compile("^Trade taken: (.+) Shard$");

	private static final TraderParser PARSER = new TraderParser();
	private static final Map<String, Long> announced = new HashMap<>();
	/** Speaker -> where their offer was found, until the price line completes it. */
	private static final Map<String, Spot> offerSpots = new HashMap<>();
	/**
	 * Where each Hunter was last seen.
	 *
	 * <p>An NPC talks for several seconds and you are free to walk off mid-sentence, so
	 * by the time a line worth recording arrives the NPC can be out of range — or gone
	 * from the client entirely. Falling back to where the player is standing then sends
	 * the party to wherever you happened to have wandered, which is the bug this fixes.
	 */
	private static final Map<String, Spot> lastSeen = new HashMap<>();
	private static final List<Trade> found = new ArrayList<>();
	private static int ticks;

	/** Where an offer was found. */
	public record Spot(int x, int y, int z, SafariBiome biome) {
		public String describe() {
			String coords = "%d %d %d".formatted(x, y, z);
			return biome == null ? coords : biome.displayName() + " " + coords;
		}

		/** Straight-line distance from the player, or -1 when there is no player. */
		public double distanceFromPlayer() {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) return -1;
			return Math.sqrt(client.player.position().distanceToSqr(x + 0.5, y + 0.5, z + 0.5));
		}
	}

	/** One resolved trade: {@code npc} hands over {@code critter}'s shard for {@code item}. */
	public record Trade(String npc, Critter critter, String item, Spot spot) {
	}

	private TraderWatch() {
	}

	/**
	 * Keeps track of where the Hunters are standing.
	 *
	 * <p>Done continuously rather than only when one speaks, so a trade can be placed at
	 * the NPC even if the dialog finishes after you have walked away from it.
	 */
	public static void tick() {
		if (++ticks < SCAN_INTERVAL_TICKS) return;
		ticks = 0;
		if (!SafariLocation.inSafari()) return;

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		for (Entity entity : client.level.entitiesForRendering()) {
			String name = nameOf(entity);
			if (name == null || !isHunter(name)) continue;
			lastSeen.put(name, spotAt(entity.blockPosition()));
		}
	}

	/** Feeds one cleaned chat line; records and announces when a trade becomes complete. */
	public static void onChatMessage(String line) {
		if (acceptShared(line)) return;
		if (takeTrade(line)) return;

		TraderParser.TradeOffer offer = PARSER.parse(line);

		// Note the spot as the dialog opens. The price line follows seconds later, by
		// which point the player may have turned away from the NPC.
		String opening = PARSER.offerJustRegistered();
		if (opening != null) {
			Spot spot = locate(opening);
			if (spot != null) offerSpots.put(opening, spot);
		}

		if (offer == null) return;

		String key = offer.npc() + "|" + offer.critter().name() + "|" + offer.item();
		long now = System.currentTimeMillis();
		Long last = announced.get(key);
		if (last != null && now - last < REPEAT_COOLDOWN_MILLIS) return;
		announced.put(key, now);

		Spot spot = offerSpots.remove(offer.npc());

		// Recorded even when the chat report is off, so the on-screen tracker still
		// works for anyone who would rather not have the chat lines.
		record(new Trade(offer.npc(), offer.critter(), offer.item(), spot));

		if (!ConfigManager.get().alerts.traderAlerts) return;

		String where = spot == null ? "" : " (" + spot.describe() + ")";
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.hud.getChat().addClientSystemMessage(
				Component.literal("[Critters] ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal(offer.npc() + where + ": ").withStyle(ChatFormatting.AQUA))
					.append(Component.literal(offer.critter().name() + " Shard")
						.withStyle(rarityStyle(offer.critter())))
					.append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(offer.item()).withStyle(ChatFormatting.YELLOW)));
		}

		EncounterAlerts.post(ConfigManager.get().party.trades(), "%s%s: %s Shard for %s".formatted(
			offer.npc(), where, offer.critter().name(), offer.item()));
	}

	/**
	 * Drops a trade that has just been taken, and says so.
	 *
	 * <p>An offer is one item for one shard, so once someone has taken it there is
	 * nothing left at that Hunter — leaving it on the tracker sends the party walking to
	 * an NPC with nothing to give. Announcing it is what lets the rest of the party's
	 * trackers drop it too; {@link #acceptTaken} is the other half.
	 *
	 * @return true if the line was a completed trade and should not be parsed further
	 */
	private static boolean takeTrade(String line) {
		Matcher matcher = TRADE_TAKEN.matcher(line);
		if (!matcher.matches()) {
			matcher = SHARD_RECEIVED.matcher(line);
			if (!matcher.matches()) return false;
		}

		// The species has to be one this mod knows, which is also what keeps this off any
		// other line shaped like the sentence.
		Critter critter = Critters.byName(matcher.group(1).trim());
		if (critter == null) return false;

		// Dropped whatever the announcement setting is: this client saw the trade taken,
		// so its own tracker is wrong from here on either way.
		if (!drop(critter)) return true;

		EncounterAlerts.post(ConfigManager.get().party.trades(),
			"Trade taken: " + critter.name() + " Shard");
		return true;
	}

	/**
	 * Drops a trade a partymate announced taking.
	 *
	 * @return true if the line was one of ours and should not be parsed further
	 */
	private static boolean acceptTaken(String said) {
		Matcher matcher = SHARED_TAKEN.matcher(said);
		if (!matcher.matches()) return false;

		Critter critter = Critters.byName(matcher.group(1).trim());
		if (critter != null) drop(critter);
		return true;
	}

	/**
	 * Forgets the tracked offer of a species' shard.
	 *
	 * <p>Matched on the species rather than the NPC, because that is all the announcement
	 * carries — and two Hunters offering the same shard at once has not been seen.
	 *
	 * @return true if anything was actually removed
	 */
	private static boolean drop(Critter critter) {
		return found.removeIf(trade -> trade.critter().equals(critter));
	}

	/**
	 * Takes a trade a partymate's copy of this mod announced.
	 *
	 * <p>Their client saw the dialog and worked out where the NPC was; that is exactly
	 * the information this one is missing, since the dialog is only ever shown to
	 * whoever clicked. Reading our own announcement back off party chat puts the trade
	 * in the tracker and on a waypoint for everyone running the mod, which is the point
	 * of announcing it at all.
	 *
	 * @return true if the line was a shared trade and should not be parsed further
	 */
	private static boolean acceptShared(String line) {
		Matcher party = PARTY_LINE.matcher(line);
		if (!party.matches()) return false;

		// Our own announcement comes back to us; the local copy is already recorded and
		// was placed from the entity rather than from text.
		String sender = party.group(1);
		if (sender.equals(Minecraft.getInstance().getUser().getName())) return true;

		// Ahead of the setting below, deliberately. That one is about taking partymates'
		// offers; this is about an offer going away, which matters just as much for one
		// this client found by itself — a trade someone else has taken is stale whether
		// or not their finds are wanted.
		if (acceptTaken(party.group(2))) return true;

		if (!ConfigManager.get().party.acceptSharedTrades) return false;

		Matcher trade = SHARED_TRADE.matcher(party.group(2));
		if (!trade.matches()) return false;

		Critter critter = Critters.byName(trade.group(6));
		if (critter == null) return false;

		Spot spot = new Spot(Integer.parseInt(trade.group(3)), Integer.parseInt(trade.group(4)),
			Integer.parseInt(trade.group(5)), SafariBiome.fromDisplayName(trade.group(2)));
		record(new Trade(trade.group(1), critter, trade.group(7), spot));
		return true;
	}

	/** Adds a trade to the tracker, replacing whatever that NPC was last offering. */
	private static void record(Trade trade) {
		found.removeIf(t -> t.npc().equals(trade.npc()));
		found.add(trade);
		while (found.size() > MAX_TRACKED) found.removeFirst();
	}

	/**
	 * Where to send people, in order of how well it is known: the NPC itself if it is
	 * still loaded, else where it was last seen, else where the player is standing.
	 *
	 * <p>Searched across everything loaded rather than within a radius of the player,
	 * since the whole problem is that the player may no longer be next to it. Hypixel
	 * renders an NPC's label on a separate entity from its body, so either counts, and
	 * both sit at the same spot.
	 */
	private static Spot locate(String npcName) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return null;

		if (client.level != null) {
			for (Entity entity : client.level.entitiesForRendering()) {
				String name = nameOf(entity);
				if (name != null && name.contains(npcName)) return spotAt(entity.blockPosition());
			}
		}

		for (Map.Entry<String, Spot> seen : lastSeen.entrySet()) {
			if (seen.getKey().contains(npcName)) return seen.getValue();
		}

		// Last resort, and marked as such: the player has walked off, so this is only
		// roughly where the NPC was.
		return spotAt(client.player.blockPosition());
	}

	/** Builds a spot, taking the biome from the position itself rather than the player's. */
	private static Spot spotAt(BlockPos pos) {
		SafariBiome biome = SafariAreaMap.biomeAt(pos.getX(), pos.getY(), pos.getZ());
		return new Spot(pos.getX(), pos.getY(), pos.getZ(),
			biome != null ? biome : SafariLocation.biome());
	}

	/** The four roaming traders are all Hunters or Huntresses. */
	private static boolean isHunter(String name) {
		return name.startsWith("Hunter ") || name.startsWith("Huntress ");
	}

	/** An entity's name as plain text — its custom name if it has one, else its own. */
	private static String nameOf(Entity entity) {
		Component name = entity.hasCustomName() ? entity.getCustomName() : entity.getDisplayName();
		if (name == null) return null;
		String text = name.getString().replaceAll("§.", "").replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
		return text.isEmpty() ? null : text;
	}

	/** Legendaries stand out, since those are the trades worth crossing the map for. */
	static ChatFormatting rarityStyle(Critter critter) {
		return switch (critter.rarity()) {
			case LEGENDARY -> ChatFormatting.GOLD;
			case EPIC -> ChatFormatting.LIGHT_PURPLE;
			case RARE -> ChatFormatting.BLUE;
			case UNCOMMON -> ChatFormatting.GREEN;
			case COMMON -> ChatFormatting.WHITE;
		};
	}

	/** Trades found this run, newest last. */
	public static List<Trade> found() {
		return List.copyOf(found);
	}

	/** Clears half-seen dialog, the repeat guard and the tracker; called when a run starts. */
	public static void reset() {
		PARSER.reset();
		announced.clear();
		offerSpots.clear();
		lastSeen.clear();
		found.clear();
	}
}
