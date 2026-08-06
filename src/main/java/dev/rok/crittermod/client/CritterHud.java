package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.session.SafariSession;
import dev.rok.crittermod.session.SessionManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/**
 * Top-left overview panel: party and personal dex progress for this run, a bar per
 * biome, and who is covering which biome.
 */
public final class CritterHud implements HudElement {

	private static final int HEADER = 0xFFFFAA00;
	private static final int LABEL = 0xFFBBBBBB;
	private static final int DIM = 0xFF888888;
	private static final int WHITE = 0xFFFFFFFF;
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

		int total = Critters.total();
		boolean live = SessionManager.current() != null;

		HudPanel panel = new HudPanel();
		panel.title(live
			? "Critter Safari  " + formatDuration(session.durationMillis())
			: "Critter Safari (last run)", HEADER);

		panel.bar("Party", session.partyUnique(), total, LABEL,
			session.dexComplete() ? DONE : WHITE);
		panel.bar("You", session.ownUnique(), total, LABEL, 0xFF55FFFF);
		panel.blank();

		for (SafariBiome biome : SafariBiome.values()) {
			int max = Critters.totalIn(biome);
			boolean complete = session.biomeComplete(biome);
			panel.bar(complete ? biome.displayName() + " *" : biome.displayName(),
				session.partyUnique(biome), max,
				0xFF000000 | biome.colour(),
				complete ? DONE : 0xFF000000 | biome.colour());
		}

		if (config.showPerPlayer) {
			Map<String, Map<SafariBiome, Integer>> perPlayer = session.uniquePerPlayer();
			if (perPlayer.size() > 1) {
				panel.blank();
				perPlayer.forEach((player, counts) ->
					panel.pair(player, describe(counts), 0xFF55FFFF, DIM));
			}
		}

		panel.render(graphics, client.font, config.hudX, config.hudY, false);
	}

	/** A player's coverage as {@code "Icy 9, Haunted 2"}, busiest biome first. */
	private static String describe(Map<SafariBiome, Integer> counts) {
		return counts.entrySet().stream()
			.sorted(Map.Entry.<SafariBiome, Integer>comparingByValue().reversed())
			.map(e -> e.getKey().displayName() + " " + e.getValue())
			.reduce((a, b) -> a + ", " + b)
			.orElse("-");
	}

	static String formatDuration(long millis) {
		long seconds = millis / 1000;
		return "%d:%02d".formatted(seconds / 60, seconds % 60);
	}

	static int rarityColour(Critter critter) {
		return 0xFF000000 | critter.rarity().colour();
	}
}
