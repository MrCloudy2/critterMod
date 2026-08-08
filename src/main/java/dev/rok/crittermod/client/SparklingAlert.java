package dev.rok.crittermod.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The full-screen call for a sparkling critter.
 *
 * <p>Deliberately louder than the encounter banners. A sparkling one is rare, worth
 * crossing the map for, and easy to walk past — so this takes the whole screen rather
 * than sharing the strip the other alerts use, and it holds for long enough to be seen
 * by someone who was looking at their inventory when it landed.
 */
public final class SparklingAlert implements HudElement {

	private static final long DISPLAY_MILLIS = 7000;
	private static final long FADE_MILLIS = 1200;
	private static final float TITLE_SCALE = 3.0f;
	private static final float SUBTITLE_SCALE = 1.6f;

	private static final int GOLD = 0xFFD700;
	private static final int WHITE = 0xFFFFFF;

	private static String species;
	private static String where;
	private static long shownAtMillis;

	/** Puts one on screen. {@code where} is the biome and distance, or null if unknown. */
	public static void show(String critterName, String detail) {
		species = critterName;
		where = detail;
		shownAtMillis = System.currentTimeMillis();
	}

	public static void clear() {
		species = null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (species == null) return;

		long age = System.currentTimeMillis() - shownAtMillis;
		if (age > DISPLAY_MILLIS) {
			species = null;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) return;

		int alpha = 0xFF;
		long fadeStart = DISPLAY_MILLIS - FADE_MILLIS;
		if (age > fadeStart) alpha = (int) (0xFF * (DISPLAY_MILLIS - age) / (double) FADE_MILLIS);

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		// A wash rather than a curtain: enough to catch the eye at the edge of vision
		// without hiding the critter the alert is about.
		int wash = (alpha / 10) << 24 | GOLD;
		graphics.fill(0, 0, width, height, wash);
		int band = Math.max(2, height / 90);
		graphics.fill(0, 0, width, band, (alpha << 24) | GOLD);
		graphics.fill(0, height - band, width, height, (alpha << 24) | GOLD);

		Font font = client.font;
		graphics.pose().pushMatrix();
		graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
		graphics.centeredText(font, Component.literal("SPARKLING!"),
			(int) (width / (2 * TITLE_SCALE)), (int) (height * 0.36 / TITLE_SCALE),
			(alpha << 24) | GOLD);
		graphics.pose().popMatrix();

		graphics.pose().pushMatrix();
		graphics.pose().scale(SUBTITLE_SCALE, SUBTITLE_SCALE);
		int subtitleY = (int) (height * 0.47 / SUBTITLE_SCALE);
		graphics.centeredText(font, Component.literal(species),
			(int) (width / (2 * SUBTITLE_SCALE)), subtitleY, (alpha << 24) | WHITE);
		if (where != null) {
			graphics.centeredText(font, Component.literal(where),
				(int) (width / (2 * SUBTITLE_SCALE)), subtitleY + 12, (alpha << 24) | WHITE);
		}
		graphics.pose().popMatrix();
	}
}
