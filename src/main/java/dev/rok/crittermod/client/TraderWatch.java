package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.parse.TraderParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Reports the roaming Hunter NPCs' shard-for-item trades.
 *
 * <p>Those dialogs are only shown to whoever clicked the NPC, so a trade nobody in the
 * party can use goes unmentioned while someone else is carrying the very item it
 * wants. This prints each offer with the biome it was found in, and can post the same
 * line to party chat.
 */
public final class TraderWatch {

	/** The same offer re-read by clicking the NPC again should not re-announce. */
	private static final long REPEAT_COOLDOWN_MILLIS = 5 * 60 * 1000L;
	/** You are stood at the NPC when its dialog opens, so it is well within this. */
	private static final double SEARCH_RADIUS = 12.0;

	private static final TraderParser PARSER = new TraderParser();
	private static final Map<String, Long> announced = new HashMap<>();
	/** Speaker -> where the player stood when that NPC opened its offer. */
	private static final Map<String, Spot> offerSpots = new HashMap<>();

	/** Where an offer was found: coordinates, and the biome if one could be resolved. */
	private record Spot(int x, int y, int z, SafariBiome biome) {
		String describe() {
			String where = "%d %d %d".formatted(x, y, z);
			return biome == null ? where : biome.displayName() + " " + where;
		}
	}

	private TraderWatch() {
	}

	/** Feeds one cleaned chat line; announces when a trade becomes complete. */
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
		if (!ConfigManager.get().alerts.traderAlerts) return;

		String key = offer.npc() + "|" + offer.critter().name() + "|" + offer.item();
		long now = System.currentTimeMillis();
		Long last = announced.get(key);
		if (last != null && now - last < REPEAT_COOLDOWN_MILLIS) return;
		announced.put(key, now);

		Spot spot = offerSpots.remove(offer.npc());
		String where = spot == null ? "" : " (" + spot.describe() + ")";

		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.getChat().addClientSystemMessage(
				Component.literal("[Critters] ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal(offer.npc() + where + ": ").withStyle(ChatFormatting.AQUA))
					.append(Component.literal(offer.critter().name() + " Shard")
						.withStyle(style(offer)))
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

	/** Legendaries stand out, since those are the trades worth going out of your way for. */
	private static ChatFormatting style(TraderParser.TradeOffer offer) {
		return switch (offer.critter().rarity()) {
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

	/** Clears half-seen dialog and the repeat guard; called when a run starts. */
	public static void reset() {
		PARSER.reset();
		announced.clear();
		offerSpots.clear();
	}
}
