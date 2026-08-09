package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches which biome the run has made your Hunting Hotspot.
 *
 * <p>Hypixel says so once, about eight seconds after you walk in:
 * {@code HOTSPOT! Your Hunting Hotspot is the Icy Biome!}
 *
 * <p>It is <em>yours</em> — everyone in the party is told a different one — which is
 * exactly why it is worth saying out loud. Four people deciding who takes which biome
 * have four hotspots between them, and only their own client was told.
 */
public final class HotspotWatch {

	/**
	 * Anchored at the start, so it cannot match the same sentence quoted inside
	 * something a player typed. The chat gate already filters those out; this is the
	 * second lock on the same door.
	 */
	private static final Pattern HOTSPOT =
		Pattern.compile("^HOTSPOT! Your Hunting Hotspot is the (.+?) Biome!$");

	private static SafariBiome hotspot;

	private HotspotWatch() {
	}

	/** The biome that is your hotspot this run, or {@code null} before it is announced. */
	public static SafariBiome biome() {
		return hotspot;
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		Matcher matcher = HOTSPOT.matcher(line);
		if (!matcher.matches()) return;

		SafariBiome found = SafariBiome.fromDisplayName(matcher.group(1));
		// An unrecognised biome name means Hypixel has changed something; better to
		// leave the panel blank than to report a guess.
		if (found == null) return;
		hotspot = found;
		announce(found);
	}

	private static void announce(SafariBiome biome) {
		CritterConfig config = ConfigManager.get();
		if (config.alerts.hotspotAlert) {
			Minecraft client = Minecraft.getInstance();
			if (client.gui != null) {
				client.gui.getChat().addClientSystemMessage(
					Component.literal("[Critters] ").withStyle(ChatFormatting.GOLD)
						.append(Component.literal("Your hotspot: ").withStyle(ChatFormatting.GRAY))
						.append(Component.literal(biome.displayName()).withStyle(ChatFormatting.YELLOW)));
			}
		}
		EncounterAlerts.post(config.party.hotspot(), "My hotspot: " + biome.displayName());
	}

	/** A hotspot belongs to one run; the next one is told its own. */
	public static void reset() {
		hotspot = null;
	}
}
