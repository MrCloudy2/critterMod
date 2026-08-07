package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.parse.TraderParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

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
	/** You are stood at the NPC when its dialog opens, so it is well within this. */
	private static final double SEARCH_RADIUS = 12.0;
	/** Bounds the tracker box; the oldest trade drops off beyond this. */
	private static final int MAX_TRACKED = 6;

	private static final TraderParser PARSER = new TraderParser();
	private static final Map<String, Long> announced = new HashMap<>();
	/** Speaker -> where their offer was found, until the price line completes it. */
	private static final Map<String, Spot> offerSpots = new HashMap<>();
	private static final List<Trade> found = new ArrayList<>();

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

	/** Feeds one cleaned chat line; records and announces when a trade becomes complete. */
	public static void onChatMessage(String line) {
		TraderParser.TradeOffer offer = PARSER.parse(line);

		// Note the spot as the dialog opens. The price line follows seconds later, by
		// which point the player may have turned away from the NPC.
		String opening = PARSER.offerJustRegistered();
		if (opening != null) {
			BlockPos pos = locate(opening);
			if (pos != null) {
				offerSpots.put(opening, new Spot(pos.getX(), pos.getY(), pos.getZ(),
					AreaDetector.currentBiome()));
			}
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

		if (ConfigManager.get().party.traderPartyNotify) {
			ChatQueue.enqueue(shareChannel() + "%s%s: %s Shard for %s".formatted(
				offer.npc(), where, offer.critter().name(), offer.item()), isCommand());
		}
	}

	/**
	 * Where to send people: the NPC's own position if it can be found nearby, otherwise
	 * where the player is standing.
	 *
	 * <p>Hypixel renders an NPC's label on a separate entity from its body, so this
	 * matches on either and takes the nearest. The player is talking to the NPC at this
	 * point, so the fallback is only ever a couple of blocks out.
	 */
	private static BlockPos locate(String npcName) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return null;
		if (client.level == null) return client.player.blockPosition();

		Vec3 origin = client.player.position();
		Entity nearest = null;
		double nearestSq = SEARCH_RADIUS * SEARCH_RADIUS;

		for (Entity entity : client.level.entitiesForRendering()) {
			double distanceSq = entity.position().distanceToSqr(origin);
			if (distanceSq > nearestSq) continue;
			if (!named(entity, npcName)) continue;
			nearestSq = distanceSq;
			nearest = entity;
		}
		return nearest != null ? nearest.blockPosition() : client.player.blockPosition();
	}

	private static boolean named(Entity entity, String npcName) {
		if (entity.hasCustomName() && matches(entity.getCustomName(), npcName)) return true;
		return matches(entity.getDisplayName(), npcName);
	}

	private static boolean matches(Component name, String npcName) {
		return name != null && name.getString().replaceAll("§.", "").contains(npcName);
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

	private static String shareChannel() {
		String channel = ConfigManager.get().party.shareCommand();
		return channel == null || channel.isBlank() ? "" : channel.trim() + " ";
	}

	private static boolean isCommand() {
		String channel = ConfigManager.get().party.shareCommand();
		return channel != null && !channel.isBlank();
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
		found.clear();
	}
}
