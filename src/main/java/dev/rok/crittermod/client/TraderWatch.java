package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
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
		found.removeIf(t -> t.npc().equals(offer.npc()));
		found.add(new Trade(offer.npc(), offer.critter(), offer.item(), spot));
		while (found.size() > MAX_TRACKED) found.removeFirst();

		if (!ConfigManager.get().alerts.traderAlerts) return;

		String where = spot == null ? "" : " (" + spot.describe() + ")";
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.getChat().addClientSystemMessage(
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
