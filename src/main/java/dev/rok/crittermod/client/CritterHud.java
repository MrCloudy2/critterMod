package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.session.SafariSession;
import dev.rok.crittermod.session.SessionManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compact overlay showing this run's progress: party and personal dex counts
 * overall and per biome, plus who is covering which biome.
 */
public final class CritterHud implements HudElement {

	private static final int LINE_HEIGHT = 10;
	private static final int PADDING = 4;
	private static final int BACKGROUND = 0x90000000;
	private static final int HEADER = 0xFFFFAA00;
	private static final int LABEL = 0xFFAAAAAA;
	private static final int DONE = 0xFF55FF55;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		CritterConfig config = CritterConfig.get();
		if (!config.hudEnabled) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) return;
		if (config.onlyInSafari && SessionManager.current() == null) return;

		SafariSession session = SessionManager.currentOrLast();
		if (session == null) return;

		List<Line> lines = buildLines(session);
		if (lines.isEmpty()) return;

		Font font = client.font;
		int width = 0;
		for (Line line : lines) {
			width = Math.max(width, font.width(line.text()));
		}

		int x = config.hudX;
		int y = config.hudY;
		graphics.fill(x, y, x + width + PADDING * 2, y + lines.size() * LINE_HEIGHT + PADDING * 2, BACKGROUND);

		int textY = y + PADDING;
		for (Line line : lines) {
			graphics.text(font, Component.literal(line.text()), x + PADDING, textY, line.colour());
			textY += LINE_HEIGHT;
		}
	}

	private static List<Line> buildLines(SafariSession session) {
		List<Line> lines = new ArrayList<>();
		int total = Critters.total();

		lines.add(new Line("Critter Safari  " + formatDuration(session.durationMillis()), HEADER));
		lines.add(new Line("Party %d/%d   You %d/%d".formatted(
			session.partyUnique(), total, session.ownUnique(), total),
			session.dexComplete() ? DONE : 0xFFFFFFFF));

		for (SafariBiome biome : SafariBiome.values()) {
			int max = Critters.totalIn(biome);
			boolean complete = session.biomeComplete(biome);
			lines.add(new Line("%-8s %d/%d%s  you %d".formatted(
				biome.displayName(), session.partyUnique(biome), max,
				complete ? " *" : "  ", session.ownUnique(biome)),
				complete ? DONE : 0xFF000000 | biome.colour()));
		}

		if (CritterConfig.get().showPerPlayer) {
			Map<String, Map<SafariBiome, Integer>> perPlayer = session.uniquePerPlayer();
			if (perPlayer.size() > 1) {
				lines.add(new Line("", LABEL));
				perPlayer.forEach((player, counts) ->
					lines.add(new Line("  %s: %s".formatted(player, describe(counts)), LABEL)));
			}
		}

		return lines;
	}

	/** Renders a player's biome coverage as {@code "Icy 9, Haunted 2"}, busiest first. */
	private static String describe(Map<SafariBiome, Integer> counts) {
		return counts.entrySet().stream()
			.sorted(Map.Entry.<SafariBiome, Integer>comparingByValue().reversed())
			.map(e -> e.getKey().displayName() + " " + e.getValue())
			.reduce((a, b) -> a + ", " + b)
			.orElse("-");
	}

	private static String formatDuration(long millis) {
		long seconds = millis / 1000;
		return "%d:%02d".formatted(seconds / 60, seconds % 60);
	}

	private record Line(String text, int colour) {
	}
}
