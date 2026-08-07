package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.parse.TraderParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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

	private static final TraderParser PARSER = new TraderParser();
	private static final Map<String, Long> announced = new HashMap<>();

	private TraderWatch() {
	}

	/** Feeds one cleaned chat line; announces when a trade becomes complete. */
	public static void onChatMessage(String line) {
		TraderParser.TradeOffer offer = PARSER.parse(line);
		if (offer == null) return;
		if (!ConfigManager.get().alerts.traderAlerts) return;

		String key = offer.npc() + "|" + offer.critter().name() + "|" + offer.item();
		long now = System.currentTimeMillis();
		Long last = announced.get(key);
		if (last != null && now - last < REPEAT_COOLDOWN_MILLIS) return;
		announced.put(key, now);

		SafariBiome biome = AreaDetector.currentBiome();
		String where = biome == null ? "" : " (" + biome.displayName() + ")";

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
	}
}
