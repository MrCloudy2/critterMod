package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Map;

/**
 * On-screen banners, sounds and optional party-chat notices for the two legendary
 * encounters, plus biome completion.
 *
 * <p>Both bosses announce themselves in stages, so each is tracked through
 * ready → started → done:
 *
 * <pre>
 * GEMZIE (Cavern, chamber)
 *   A rumbling sound can be heard, and the door … opens...   READY
 *   (no end message — exactly 3 spawn, so 3 catches)         DONE
 *
 * WUMPA (Icy, on a timer)
 *   You hear the sound of massive footsteps …                READY  (~30s out)
 *   The Wumpa has awoken.                                    STARTED
 *   The cave opens up again...                               DONE
 *
 * DOOMSPIRAL (Haunted, player-summoned by a candle ritual)
 *   You used the Soothing Incense to light the candle! …     READY  (ritual underway)
 *   Your ritual summoned a Doomspiral into this world.       STARTED
 *   The Doomspiral retreats back underground...              DONE
 * </pre>
 *
 * <p>Catching either also counts as done, and the ritual has four candles, so the
 * same stage can be signalled more than once — a per-stage cooldown keeps that from
 * firing repeat banners or spamming party chat.
 */
public final class EncounterAlerts implements HudElement {

	private static final long DISPLAY_MILLIS = 8000;
	private static final long STAGE_COOLDOWN_MILLIS = 20_000;
	private static final float SCALE = 2.0f;

	private static final int READY_COLOUR = 0xFFFFAA00;
	private static final int STARTED_COLOUR = 0xFFFF5555;
	private static final int DONE_COLOUR = 0xFF55FF55;

	/** Exactly this many Gemzies spawn each time the chamber opens. */
	private static final int GEMZIE_PER_CHAMBER = 3;

	private static final Map<String, Long> lastFired = new HashMap<>();

	/** Gemzies still to catch in the open chamber; 0 when no chamber is active. */
	private static int gemzieRemaining;

	private static String message;
	private static int colour;
	private static long shownAtMillis;

	public enum Stage {READY, STARTED, DONE}

	/**
	 * Reacts to a cleaned chat line.
	 *
	 * @return true if the line was an encounter announcement
	 */
	public static boolean onChatMessage(String line) {
		// --- Gemzie ---
		if (line.startsWith("A rumbling sound can be heard")) {
			// Exactly three Gemzies spawn per chamber, so the encounter is over once
			// three have been caught by anyone rather than on any message.
			gemzieRemaining = GEMZIE_PER_CHAMBER;
			fire("Gemzie", Stage.READY, "chamber open, 3 to catch", 1.4f);
			return true;
		}

		// --- Wumpa ---
		if (line.startsWith("You hear the sound of massive footsteps")) {
			fire("Wumpa", Stage.READY, "it wakes in ~30s", 1.4f);
			return true;
		}
		if (line.startsWith("The Wumpa has awoken")) {
			fire("Wumpa", Stage.STARTED, "fight is live", 1.8f);
			return true;
		}
		if (line.startsWith("The cave opens up again")) {
			fire("Wumpa", Stage.DONE, "the cave has reopened", 1.2f);
			return true;
		}

		// --- Doomspiral ---
		if (line.startsWith("You used the Soothing Incense to light the candle")) {
			fire("Doomspiral", Stage.READY, "ritual underway", 1.4f);
			return true;
		}
		if (line.startsWith("Your ritual summoned a Doomspiral")) {
			fire("Doomspiral", Stage.STARTED, "fight is live", 1.8f);
			return true;
		}
		if (line.startsWith("The Doomspiral retreats back underground")) {
			fire("Doomspiral", Stage.DONE, "it retreated", 1.2f);
			return true;
		}

		return false;
	}

	/**
	 * Called for every catch this run, by anyone.
	 *
	 * <p>Wumpa and Doomspiral each end when caught. Gemzie has no end message at all —
	 * exactly three spawn per chamber, so the count is what closes it.
	 */
	public static void onCatch(String critterName) {
		switch (critterName) {
			case "Wumpa", "Doomspiral" -> fire(critterName, Stage.DONE, "caught", 1.2f);
			case "Gemzie" -> {
				if (gemzieRemaining <= 0) return;
				if (--gemzieRemaining == 0) {
					fire("Gemzie", Stage.DONE, "all %d caught".formatted(GEMZIE_PER_CHAMBER), 1.2f);
				}
			}
			default -> {
			}
		}
	}

	/** Every species in {@code biome} has now been caught by someone this run. */
	public static void onBiomeComplete(SafariBiome biome) {
		if (!ConfigManager.get().alerts.biomeDoneNotify) return;
		if (onCooldown("biome:" + biome.name())) return;

		announce(biome.displayName() + " Done!", 1.4f);
	}

	/** Everything but the Macaw is caught — usually the real finish line for a run. */
	public static void onAllButMacaw() {
		if (!ConfigManager.get().alerts.allButMacawNotify) return;
		announce("Everything except Macaw done!", 1.6f);
	}

	/**
	 * A Macaw has turned up.
	 *
	 * <p>Not a staged encounter like the bosses, so it says the one thing there is to
	 * say. {@code where} is the spot to send people to, or null when the Birdfeeder
	 * announced it from out of range.
	 */
	public static void onMacawSpawn(String where) {
		CritterConfig config = ConfigManager.get();
		if (!config.alerts.macawAlert) return;

		String detail = where == null ? "" : " (" + where + ")";
		banner("MACAW SPAWNED", READY_COLOUR, 1.6f);
		chat("Macaw spawned!" + detail, ChatFormatting.GOLD);

		post(config.party.macaw(), "Macaw spawned!" + detail);
	}

	/** All 37 caught by someone. */
	public static void onAllDone() {
		if (!ConfigManager.get().alerts.allDoneNotify) return;
		announce("Everything Done!", 2.0f);
	}

	private static void announce(String text, float pitch) {
		banner(text, DONE_COLOUR, pitch);
		chat(text, ChatFormatting.GREEN);
		post(ConfigManager.get().party.milestones(), text);
	}

	private static void fire(String boss, Stage stage, String detail, float pitch) {
		if (!alertsOn(boss)) return;
		// Gemzie chambers repeat every few minutes and its ready/done pair can be
		// seconds apart, so the anti-repeat cooldown must not apply to it.
		if (!boss.equals("Gemzie") && onCooldown(boss + ":" + stage)) return;

		String text = "%s %s".formatted(boss.toUpperCase(), stage.name());
		banner(text, switch (stage) {
			case READY -> READY_COLOUR;
			case STARTED -> STARTED_COLOUR;
			case DONE -> DONE_COLOUR;
		}, pitch);
		chat(text + " — " + detail, ChatFormatting.YELLOW);

		post(broadcastFor(boss), "%s %s".formatted(boss, stage.name().toLowerCase()));
	}

	/** Whether this encounter's banners are wanted. Each is settable on its own. */
	private static boolean alertsOn(String boss) {
		CritterConfig.AlertConfig alerts = ConfigManager.get().alerts;
		return switch (boss) {
			case "Gemzie" -> alerts.gemzieAlert;
			case "Wumpa" -> alerts.wumpaAlert;
			case "Doomspiral" -> alerts.doomspiralAlert;
			default -> false;
		};
	}

	/** Who hears about this encounter, which is a separate choice per encounter. */
	private static CritterConfig.Broadcast broadcastFor(String boss) {
		CritterConfig.PartyConfig party = ConfigManager.get().party;
		return switch (boss) {
			case "Gemzie" -> party.gemzie();
			case "Wumpa" -> party.wumpa();
			case "Doomspiral" -> party.doomspiral();
			default -> CritterConfig.Broadcast.NONE;
		};
	}

	/** Sends one line to whoever the setting names, or nowhere. */
	static void post(CritterConfig.Broadcast to, String message) {
		String command = to.command();
		if (command == null) return;
		ChatQueue.enqueue(command + " " + message, true);
	}

	/** True if this stage already fired recently, so it should be suppressed. */
	private static boolean onCooldown(String key) {
		long now = System.currentTimeMillis();
		Long previous = lastFired.get(key);
		if (previous != null && now - previous < STAGE_COOLDOWN_MILLIS) return true;
		lastFired.put(key, now);
		return false;
	}

	private static void banner(String text, int bannerColour, float pitch) {
		message = text;
		colour = bannerColour;
		shownAtMillis = System.currentTimeMillis();

		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, pitch);
		}
	}

	private static void chat(String text, ChatFormatting style) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui == null) return;
		client.gui.getChat().addClientSystemMessage(
			Component.literal("[Critters] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(text).withStyle(style)));
	}

	/** Clears per-stage cooldowns; called when a new run starts. */
	public static void reset() {
		lastFired.clear();
		gemzieRemaining = 0;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (message == null) return;

		long age = System.currentTimeMillis() - shownAtMillis;
		if (age > DISPLAY_MILLIS) {
			message = null;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) return;

		// Fade over the last second so it does not simply vanish.
		int alpha = 0xFF;
		long fadeStart = DISPLAY_MILLIS - 1000;
		if (age > fadeStart) {
			alpha = (int) (0xFF * (DISPLAY_MILLIS - age) / 1000.0);
		}

		Font font = client.font;
		int centreX = (int) (graphics.guiWidth() / (2 * SCALE));
		int y = (int) (graphics.guiHeight() * 0.22 / SCALE);

		graphics.pose().pushMatrix();
		graphics.pose().scale(SCALE, SCALE);
		graphics.centeredText(font, Component.literal(message),
			centreX, y, (alpha << 24) | (colour & 0xFFFFFF));
		graphics.pose().popMatrix();
	}
}
