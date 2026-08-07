package dev.rok.crittermod.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Movable box listing the Hunter trades found this run, nearest first.
 *
 * <p>The chat report scrolls away; this keeps the offers to hand, so a quest item
 * picked up later can still be spent on one found half an hour earlier.
 */
public final class TradeHud implements HudElement {

	private static final int HEADER = 0xFFFFAA00;
	private static final int DIM = 0xFF888888;
	private static final int ITEM = 0xFFFFFF55;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		CritterConfig config = ConfigManager.get();
		if (!config.display.hudEnabled || !config.display.showTrades) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) return;
		if (config.display.onlyInSafari && !AreaDetector.inSafari()) return;

		HudPanel panel = buildPanel();
		if (panel == null) return;

		HudBox box = HudBox.TRADES;
		panel.render(graphics, client.font,
			box.pixelX(graphics.guiWidth(), panel, client.font),
			box.pixelY(graphics.guiHeight(), panel, client.font),
			box.scale());
	}

	/** The list of trades, or {@code null} when none have been found this run. */
	static HudPanel buildPanel() {
		List<TraderWatch.Trade> trades = TraderWatch.found();
		if (trades.isEmpty()) return null;

		HudPanel panel = new HudPanel();
		panel.title("Hunter Trades", HEADER);

		// Nearest first, so the one worth walking to is at the top.
		trades.stream()
			.sorted((a, b) -> Double.compare(distance(a), distance(b)))
			.forEach(trade -> {
				panel.pair(trade.critter().name() + " ← " + trade.item(), "",
					0xFF000000 | trade.critter().rarity().colour(), ITEM);
				panel.line("  " + describe(trade), DIM);
			});
		return panel;
	}

	/** {@code "Icy -106 87 -7 · 34m"}, dropping whatever is unknown. */
	private static String describe(TraderWatch.Trade trade) {
		if (trade.spot() == null) return trade.npc();
		double distance = trade.spot().distanceFromPlayer();
		String where = trade.spot().describe();
		return distance < 0 ? where : "%s · %dm".formatted(where, Math.round(distance));
	}

	/** Unknown positions sort last rather than first. */
	private static double distance(TraderWatch.Trade trade) {
		if (trade.spot() == null) return Double.MAX_VALUE;
		double distance = trade.spot().distanceFromPlayer();
		return distance < 0 ? Double.MAX_VALUE : distance;
	}
}
