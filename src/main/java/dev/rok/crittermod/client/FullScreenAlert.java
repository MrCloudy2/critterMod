package dev.rok.crittermod.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The full-screen call, for the two things a run turns on.
 *
 * <p>Deliberately louder than the encounter banners. A sparkling critter and a Macaw are
 * both rare, both worth crossing the map for and both easy to walk past — so this takes
 * the whole screen rather than sharing the strip the other alerts use, and it holds long
 * enough to be seen by someone who was looking at their inventory when it landed.
 *
 * <p>The wash is drawn in the caller's colour, so the two read differently at a glance
 * without either needing to be read.
 */
public final class FullScreenAlert implements HudElement {

	private static final long DISPLAY_MILLIS = 7000;
	private static final long FADE_MILLIS = 1200;
	private static final float TITLE_SCALE = 3.0f;
	private static final float SUBTITLE_SCALE = 1.6f;

	/** A sparkling critter. */
	public static final int SPARKLING = 0xFFD700;
	/** A Macaw, in its own rarity's orange so the two calls are not the same flash. */
	public static final int MACAW = 0xFFAA00;

	private static final int WHITE = 0xFFFFFF;

	private static String headline;
	private static String subject;
	private static String where;
	private static int tint = SPARKLING;
	private static long shownAtMillis;

	/**
	 * Puts one on screen.
	 *
	 * @param call    the word across the middle, e.g. {@code SPARKLING!}
	 * @param subject what it is about, e.g. the species
	 * @param detail  where it is, or null when the position is not known
	 * @param colour  the wash and headline colour
	 */
	public static void show(String call, String subject, String detail, int colour) {
		headline = call;
		FullScreenAlert.subject = subject;
		where = detail;
		tint = colour;
		shownAtMillis = System.currentTimeMillis();
	}

	public static void clear() {
		headline = null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (headline == null) return;

		long age = System.currentTimeMillis() - shownAtMillis;
		if (age > DISPLAY_MILLIS) {
			headline = null;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden()) return;

		int alpha = 0xFF;
		long fadeStart = DISPLAY_MILLIS - FADE_MILLIS;
		if (age > fadeStart) alpha = (int) (0xFF * (DISPLAY_MILLIS - age) / (double) FADE_MILLIS);

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		// A wash rather than a curtain: enough to catch the eye at the edge of vision
		// without hiding the critter the alert is about.
		int wash = (alpha / 10) << 24 | tint;
		graphics.fill(0, 0, width, height, wash);
		int band = Math.max(2, height / 90);
		graphics.fill(0, 0, width, band, (alpha << 24) | tint);
		graphics.fill(0, height - band, width, height, (alpha << 24) | tint);

		Font font = client.font;
		graphics.pose().pushMatrix();
		graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
		graphics.centeredText(font, Component.literal(headline),
			(int) (width / (2 * TITLE_SCALE)), (int) (height * 0.36 / TITLE_SCALE),
			(alpha << 24) | tint);
		graphics.pose().popMatrix();

		graphics.pose().pushMatrix();
		graphics.pose().scale(SUBTITLE_SCALE, SUBTITLE_SCALE);
		int subtitleY = (int) (height * 0.47 / SUBTITLE_SCALE);
		graphics.centeredText(font, Component.literal(subject),
			(int) (width / (2 * SUBTITLE_SCALE)), subtitleY, (alpha << 24) | WHITE);
		if (where != null) {
			graphics.centeredText(font, Component.literal(where),
				(int) (width / (2 * SUBTITLE_SCALE)), subtitleY + 12, (alpha << 24) | WHITE);
		}
		graphics.pose().popMatrix();
	}
}
