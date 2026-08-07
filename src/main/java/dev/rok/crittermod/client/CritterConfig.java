package dev.rok.crittermod.client;

import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;

/**
 * Settings, laid out for MoulConfig — the same config GUI SkyHanni uses.
 *
 * <p>Fields are public and annotated; MoulConfig builds the screen from them and
 * owns loading and saving, so there is no manual persistence here. See
 * {@link ConfigManager} for the instance and the file it lives in.
 */
public class CritterConfig extends Config {

	@Override
	public StructuredText getTitle() {
		return StructuredText.of("Critter Safari Tracker");
	}

	@Override
	public boolean shouldAutoFocusSearchbar() {
		return false;
	}

	/** Runnable id for the "Edit positions" button in the Display category. */
	private static final int EDIT_POSITIONS = 1;

	@Override
	public void executeRunnable(int runnableId) {
		if (runnableId == EDIT_POSITIONS) {
			HudEditorScreen.open();
			return;
		}
		super.executeRunnable(runnableId);
	}

	@Override
	public boolean isValidRunnable(int runnableId) {
		return runnableId == EDIT_POSITIONS || super.isValidRunnable(runnableId);
	}

	@Category(name = "Display", desc = "The on-screen panels")
	public DisplayConfig display = new DisplayConfig();

	@Category(name = "Alerts", desc = "Encounter and completion alerts")
	public AlertConfig alerts = new AlertConfig();

	@Category(name = "Party", desc = "What gets announced to your team")
	public PartyConfig party = new PartyConfig();

	public static class DisplayConfig {

		@ConfigOption(name = "Progress HUD", desc = "Top-left panel: run timer, party and personal dex, a bar per biome.")
		@ConfigEditorBoolean
		public boolean hudEnabled = true;

		@ConfigOption(name = "Only in the Safari", desc = "Hide the HUD outside the Critter Safari and its entrance.")
		@ConfigEditorBoolean
		public boolean onlyInSafari = true;

		@ConfigOption(
			name = "Count unique only",
			desc = "Treat a species as done at the first catch.\n" +
				"§7Off by default: species that spawn a fixed number of times per run stay\n" +
				"§7listed until every one is caught, so a run is only finished when it has\n" +
				"§7given up every shard it can.\n" +
				"§7Quotas: §fGemzie 3 · Troodon 3 · Gazer 2 · Billygoat 1 · Hideyho 1 ·\n" +
				"§fWumpa 1 · Doomspiral 1")
		@ConfigEditorBoolean
		public boolean uniqueOnly = false;

		@ConfigOption(name = "Per-player lines", desc = "Show who is covering which biome, under the biome bars.")
		@ConfigEditorBoolean
		public boolean showPerPlayer = true;

		@ConfigOption(name = "Missing panel", desc = "Top-right list of what is still uncaught in the biome you are standing in.")
		@ConfigEditorBoolean
		public boolean showMissing = true;

		@ConfigOption(
			name = "Hunter trade tracker",
			desc = "Box listing the Hunter NPC offers found this run, nearest first.\n" +
				"§7The chat report scrolls away; this keeps them to hand.")
		@ConfigEditorBoolean
		public boolean showTrades = true;

		@ConfigOption(name = "Edit positions", desc = "Drag the boxes that are on screen to move them, and scroll over one to resize it.")
		@ConfigEditorButton(runnableId = 1, buttonText = "Edit")
		public boolean editPositions = false;

		@ConfigOption(name = "Progress HUD scale", desc = "Size of the top-left panel.")
		@ConfigEditorSlider(minValue = 0.5f, maxValue = 3.0f, minStep = 0.05f)
		public float progressScale = HudBox.DEFAULT_SCALE;

		@ConfigOption(name = "Missing panel scale", desc = "Size of the missing-species list.")
		@ConfigEditorSlider(minValue = 0.5f, maxValue = 3.0f, minStep = 0.05f)
		public float missingScale = HudBox.DEFAULT_SCALE;

		@ConfigOption(name = "Trade tracker scale", desc = "Size of the Hunter trade box.")
		@ConfigEditorSlider(minValue = 0.5f, maxValue = 3.0f, minStep = 0.05f)
		public float tradesScale = HudBox.DEFAULT_SCALE;

		// Positions are fractions of the screen, so a box stays put across resolution
		// and GUI-scale changes. Set by dragging in the editor, not by hand.
		public float progressX = 0.004f;
		public float progressY = 0.006f;
		public float missingX = 0.78f;
		public float missingY = 0.006f;
		public float tradesX = 0.006f;
		public float tradesY = 0.62f;
	}

	public static class AlertConfig {

		@ConfigOption(
			name = "Encounter alerts",
			desc = "Banner and sound as each legendary encounter progresses.\n" +
				"§7Gemzie: §fchamber opens, then 3 catches.\n" +
				"§7Wumpa: §ffootsteps, awoken, cave reopens.\n" +
				"§7Doomspiral: §fcandle ritual, summoned, retreats.")
		@ConfigEditorBoolean
		public boolean bossAlerts = true;

		@ConfigOption(name = "Biome complete", desc = "Announce \"<Biome> Done!\" once every species there has been caught by someone.")
		@ConfigEditorBoolean
		public boolean biomeDoneNotify = false;

		@ConfigOption(
			name = "All but Macaw",
			desc = "Announce \"Everything except Macaw done!\" when the only species left is the Macaw.\n" +
				"§7The Macaw only comes to the Birdfeeder and is not guaranteed in a run, so this is\n" +
				"§7usually the real finish line.")
		@ConfigEditorBoolean
		public boolean allButMacawNotify = false;

		@ConfigOption(name = "Everything done", desc = "Announce \"Everything Done!\" once all 37 species have been caught by someone.")
		@ConfigEditorBoolean
		public boolean allDoneNotify = false;

		@ConfigOption(
			name = "Hunter trades",
			desc = "Report the roaming Hunter NPCs' shard-for-item offers, with the biome they are in.\n" +
				"§7Their dialog is only shown to whoever clicked them, so an offer can go unused\n" +
				"§7while someone else is carrying exactly the item it wants.")
		@ConfigEditorBoolean
		public boolean traderAlerts = true;
	}

	public static class PartyConfig {

		@ConfigOption(
			name = "Announce to party",
			desc = "Post encounter stages to chat so your team sees them too.\n" +
				"§7Lines are spaced 1.2s apart; Hypixel drops faster bursts.")
		@ConfigEditorBoolean
		public boolean bossPartyNotify = true;

		@ConfigOption(name = "Announce Hunter trades", desc = "Post the roaming Hunter NPCs' offers to chat so your team can use them.")
		@ConfigEditorBoolean
		public boolean traderPartyNotify = true;

		@ConfigOption(name = "Post to", desc = "Where /critters share and the announcements are sent.")
		@ConfigEditorDropdown(values = {"Party chat", "All chat", "Normal chat"})
		public int shareChannel = 0;

		/** The Hypixel command for the selected channel, without the slash. */
		public String shareCommand() {
			return switch (shareChannel) {
				case 1 -> "ac";
				case 2 -> "";
				default -> "pc";
			};
		}
	}
}
