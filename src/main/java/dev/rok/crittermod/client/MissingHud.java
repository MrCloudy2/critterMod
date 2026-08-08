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
	private static final int WALL = 0xFFFFAA00;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		CritterConfig config = ConfigManager.get();
		if (!config.display.hudEnabled || !config.display.showMissing) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) return;

		SafariBiome biome = SafariLocation.biome();
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

		// A species already accounted for still deserves listing while one of them is
		// stood in front of you: every catch is another shard, so "done" only ever
		// meant done for the dex, never nothing left to take.
		List<Critter> alsoHere = session == null ? List.of()
			: Critters.inBiome(biome).stream()
				.filter(c -> !missing.contains(c))
				.filter(c -> session.nearby(c) > 0)
				.toList();

		HudPanel panel = new HudPanel();

		if (missing.isEmpty() && alsoHere.isEmpty()) {
			panel.title(biome.displayName() + " Biome", 0xFF000000 | biome.colour());
			panel.line("all " + Critters.totalIn(biome) + " caught", DONE);
		} else if (missing.isEmpty()) {
			panel.title(biome.displayName() + " Biome — all caught", DONE);
		} else {
			panel.title("%s Biome — %d left".formatted(biome.displayName(), missing.size()),
				0xFF000000 | biome.colour());
			for (Critter critter : missing) {
				// A quota species shows how many of its spawns are already taken, since
				// "caught one" is not the same as "done with it".
				int caught = session == null ? 0 : session.partyCatches(critter);
				int total = session == null ? 0 : session.required(critter);
				int near = session == null ? 0 : session.nearby(critter);
				int attempts = session == null ? 0 : session.attempts(critter);

				// Nearby beats everything: it means one is in front of you right now.
				// Then quota progress, then an attempt count meaning it keeps escaping.
				String note;
				if (near > 0) note = near + " near";
				else if (total > 1 && !TrackingMode.uniqueOnly()) note = caught + "/" + total;
				else note = attempts > 0 ? attempts + " tried" : "";
				panel.pair(critter.name(), note, CritterHud.rarityColour(critter), DIM);
			}
		}

		// Anything the run can no longer produce is stated outright rather than just
		// dropping off the list, so its absence does not look like a tracking bug.
		if (session != null) {
			List<Critter> gone = Critters.inBiome(biome).stream()
				.filter(session::isUnavailable)
				.toList();
			if (!gone.isEmpty()) {
				panel.blank();
				for (Critter critter : gone) {
					panel.pair(critter.name(), "none this run", DIM, DIM);
				}
			}
		}

		// Listed after the outstanding ones, dimmer, so they read as a bonus rather
		// than as something still owed.
		if (!alsoHere.isEmpty()) {
			panel.blank();
			panel.line("also here", DIM);
			for (Critter critter : alsoHere) {
				panel.pair(critter.name(), session.nearby(critter) + " near", DONE, DIM);
			}
		}

		appendWalls(panel, biome, WallTracker.SNOOPER, ConfigManager.get().display.showSnooperWalls);
		appendWalls(panel, biome, WallTracker.TROODON, ConfigManager.get().display.showTroodonWalls);
		appendNests(panel, biome);
		appendMounds(panel, biome);
		return panel;
	}

	/**
	 * Reports the mounds broken this run. There is no "left to break" figure to give —
	 * mounds are only known from chat as they fall apart — and only about a fifth hold
	 * a Rockmite anyway, so the useful number is how many rolls have been taken.
	 */
	private static void appendMounds(HudPanel panel, SafariBiome biome) {
		if (biome != SafariBiome.CAVERN) return;
		if (!ConfigManager.get().advanced.showMounds) return;
		if (MoundTracker.broken() == 0) return;

		panel.blank();
		panel.pair("Mounds broken", "%d → %d Rockmite".formatted(
			MoundTracker.broken(), MoundTracker.rockmites()), WALL, DIM);
	}

	/**
	 * Lists the bee nests still to punch. Honeybugs come from these, and unlike the
	 * walls they have to be found by sweeping rather than read off fixed positions —
	 * so the count is of nests come across, not of nests on the map.
	 */
	private static void appendNests(HudPanel panel, SafariBiome biome) {
		if (biome != SafariBiome.FOREST) return;
		if (!ConfigManager.get().display.showNests) return;

		List<NestTracker.Nest> nests = NestTracker.nests();
		if (nests.isEmpty()) return;

		long unpunched = nests.stream().filter(NestTracker.Nest::unpunched).count();
		panel.blank();
		if (unpunched == 0) {
			panel.line("all %d nests punched".formatted(nests.size()), DONE);
			return;
		}

		panel.title("Bee nests to punch: " + unpunched, WALL);
		nests.stream().filter(NestTracker.Nest::unpunched).limit(5).forEach(nest ->
			panel.pair("  %d %d %d".formatted(nest.pos().getX(), nest.pos().getY(), nest.pos().getZ()),
				"%dm".formatted(Math.round(nest.distance())), WALL, DIM));
	}

	/**
	 * Lists one set of walls still to break, while you are in the biome they are in.
	 * They want doing every run to check behind them, and being blocks rather than
	 * entities they can be read exactly.
	 */
	private static void appendWalls(HudPanel panel, SafariBiome biome, WallTracker tracker, boolean show) {
		if (biome != tracker.biome() || !show) return;

		List<WallTracker.Wall> walls = tracker.walls();
		if (walls.isEmpty()) return;

		long intact = walls.stream().filter(w -> w.state() == WallTracker.State.INTACT).count();
		long unknown = walls.stream().filter(w -> w.state() == WallTracker.State.UNKNOWN).count();
		if (intact == 0 && unknown == 0) {
			panel.blank();
			panel.line(tracker.name() + " walls all broken", DONE);
			return;
		}

		panel.blank();
		panel.title("%s walls to break: %d%s".formatted(
			tracker.name(), intact, unknown > 0 ? " (+" + unknown + "?)" : ""), WALL);
		for (WallTracker.Wall wall : walls) {
			if (wall.state() == WallTracker.State.BROKEN) continue;
			String mark = wall.state() == WallTracker.State.UNKNOWN ? "?" : "";
			panel.pair("  %d %d %d%s".formatted(
					wall.pos().getX(), wall.pos().getY(), wall.pos().getZ(), mark),
				"%dm".formatted(Math.round(wall.distance())), WALL, DIM);
		}
	}
}
