package dev.rok.crittermod.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * On-screen alert for the Wumpa encounter in the Icy Biome.
 *
 * <p>Hypixel announces the fight in three steps, and the useful one to react to is
 * the first — that is when the chamber is open and you can go in:
 * <pre>
 * A rumbling sound can be heard, and the door at the back of the chamber opens...
 * You hear the sound of massive footsteps echoing through the Icy Biome...   (~30s later)
 * The Wumpa has awoken.                                                      (fight live)
 * </pre>
 */
public final class WumpaAlert implements HudElement {

	private static final long DISPLAY_MILLIS = 8000;
	private static final float SCALE = 2.0f;

	private static String message;
	private static int colour;
	private static long shownAtMillis;

	/**
	 * Reacts to a cleaned chat line.
	 *
	 * @return true if the line was a Wumpa announcement
	 */
	public static boolean onChatMessage(String line) {
		if (!CritterConfig.get().wumpaAlert) return false;

		if (line.startsWith("A rumbling sound can be heard")) {
			trigger("WUMPA READY", 0xFFFFAA00, "the chamber door is open", 1.0f);
			return true;
		}
		if (line.startsWith("You hear the sound of massive footsteps")) {
			trigger("WUMPA INCOMING", 0xFFFF5555, "it wakes in ~30s", 1.4f);
			return true;
		}
		if (line.startsWith("The Wumpa has awoken")) {
			trigger("WUMPA AWAKE", 0xFFFF5555, "fight is live", 1.8f);
			return true;
		}
		return false;
	}

	private static void trigger(String banner, int bannerColour, String chatDetail, float pitch) {
		message = banner;
		colour = bannerColour;
		shownAtMillis = System.currentTimeMillis();

		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;

		client.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, pitch);
		client.gui.getChat().addClientSystemMessage(
			Component.literal("[Critters] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(banner + " — " + chatDetail)
					.withStyle(ChatFormatting.YELLOW)));
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

		// Fade out over the last second so it does not simply vanish.
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
