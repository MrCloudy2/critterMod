package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.session.SafariSession;
import dev.rok.crittermod.session.SessionManager;
import dev.rok.crittermod.session.TrackingMode;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Top-right panel listing what is still uncaught <em>in the biome you are standing
 * in</em> — the working list for whoever is assigned that biome.
 *
 * <p>Species already caught by a partymate count as done, since the run's goal is
 * party-wide coverage. A caught-by-you marker distinguishes the two.
 */
public final class MissingHud implements HudElement {

	private static final int DONE = 0xFF55FF55;
	private static final int DIM = 0xFF888888;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		CritterConfig config = ConfigManager.get();
		if (!config.display.hudEnabled || !config.display.showMissing) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) return;

		SafariBiome biome = AreaDetector.currentBiome();
		if (biome == null) return;

		HudPanel panel = buildPanel(biome, SessionManager.currentOrLast());
		HudBox box = HudBox.MISSING;
		panel.render(graphics, client.font,
			box.pixelX(graphics.guiWidth(), panel, client.font),
			box.pixelY(graphics.guiHeight(), panel, client.font),
			box.scale());
	}

	/** Builds the list for {@code biome}; {@code session} may be null before the first catch. */
	static HudPanel buildPanel(SafariBiome biome, SafariSession session) {
		// Before the first catch there is no session yet, but standing in a biome with
		// nothing caught is exactly when the full list is most useful — so fall back
		// to the whole roster rather than hiding the panel.
		List<Critter> missing = session == null ? Critters.inBiome(biome) : session.missing(biome);

		HudPanel panel = new HudPanel();

		if (missing.isEmpty()) {
			panel.title(biome.displayName() + " Biome", 0xFF000000 | biome.colour());
			panel.line("all " + Critters.totalIn(biome) + " caught", DONE);
		} else {
			panel.title("%s Biome — %d left".formatted(biome.displayName(), missing.size()),
				0xFF000000 | biome.colour());
			for (Critter critter : missing) {
				// A quota species shows how many of its spawns are already taken, since
				// "caught one" is not the same as "done with it".
				int caught = session == null ? 0 : session.partyCatches(critter);
				int total = session == null ? 0 : session.required(critter);
				int attempts = session == null ? 0 : session.attempts(critter);
				String note;
				if (session != null && total > 1 && !TrackingMode.uniqueOnly()) {
					note = caught + "/" + total;
				} else {
					note = attempts > 0 ? attempts + " tried" : "";
				}
				panel.pair(critter.name(), note, CritterHud.rarityColour(critter), DIM);
			}
		}

		return panel;
	}
}
