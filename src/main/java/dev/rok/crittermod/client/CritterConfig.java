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

	@Category(name = "Advanced", desc = "Diagnostics and unfinished work")
	public AdvancedConfig advanced = new AdvancedConfig();

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

		@ConfigOption(
			name = "Show nearby spawns",
			desc = "Mark a missing species with how many are loaded around you right now.\n" +
				"§7Read off their name tags, refreshed twice a second.\n" +
				"§7This is not a total and cannot be: the client never sees the far side of\n" +
				"§7the map, and a partymate catching something out of range is never observed.")
		@ConfigEditorBoolean
		public boolean countSpawns = true;

		@ConfigOption(name = "Per-player lines", desc = "Show who is covering which biome, under the biome bars.")
		@ConfigEditorBoolean
		public boolean showPerPlayer = true;

		@ConfigOption(name = "Missing panel", desc = "Top-right list of what is still uncaught in the biome you are standing in.")
		@ConfigEditorBoolean
		public boolean showMissing = true;

		@ConfigOption(name = "Highlight walls", desc = "Mark unbroken Cavern walls.")
		@ConfigEditorBoolean
		public boolean highlightWalls = false;

		@ConfigOption(name = "Highlight bee nests", desc = "Mark bee nests you have not punched yet.")
		@ConfigEditorBoolean
		public boolean highlightNests = false;

		@ConfigOption(name = "Highlight shard trades", desc = "Mark where a Hunter offered a trade.")
		@ConfigEditorBoolean
		public boolean highlightTrades = false;

		@ConfigOption(
			name = "Highlight mounds",
			desc = "Outline detected Rockmite mounds.\n" +
				"§7Matched on interaction hitbox size, which is weaker evidence than the\n" +
				"§7walls' fixed positions — check the outlines land on real mounds.")
		@ConfigEditorBoolean
		public boolean highlightMounds = false;

		@ConfigOption(
			name = "Highlight hard-to-find critters",
			desc = "Outline the mobs of species that are awkward to spot, Bloodbats especially.\n" +
				"§7Uses the client-side glow flag, so they show through walls.")
		@ConfigEditorBoolean
		public boolean highlightHardToFind = true;

		@ConfigOption(
			name = "Cavern walls",
			desc = "List the breakable Cavern walls still standing, under the missing list.\n" +
				"§7They want breaking every run to check behind them.\n" +
				"§7Read from the blocks themselves, so it is exact — but a wall in an unloaded\n" +
				"§7chunk is marked \"?\" rather than guessed at, since air and out-of-range look\n" +
				"§7identical from here.")
		@ConfigEditorBoolean
		public boolean showWalls = true;

		@ConfigOption(
			name = "Bee nests",
			desc = "List the bee nests still to punch, under the Forest missing list.\n" +
				"§7Honeybugs come from these. They have no fixed positions, so they are found\n" +
				"§7by sweeping nearby blocks — the count is nests you have come across, not\n" +
				"§7every nest on the map.")
		@ConfigEditorBoolean
		public boolean showNests = true;

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

	public static class AdvancedConfig {

		@ConfigOption(
			name = "Diagnostic commands",
			desc = "Register /critters debug, entities, nearby and block.\n" +
				"§7Tools for working out how the Safari represents things; of no use in a\n" +
				"§7normal run. Takes effect on the next world join, since commands are\n" +
				"§7registered as you connect.")
		@ConfigEditorBoolean
		public boolean debugCommands = false;

		@ConfigOption(
			name = "Rockmite mounds",
			desc = "Count the mounds broken this run and how many held a Rockmite.\n" +
				"§7Off by default: only the outcome is known, from chat. How many mounds are\n" +
				"§7left nearby needs whatever a mound is to the client, which is still open.")
		@ConfigEditorBoolean
		public boolean showMounds = false;
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
				"§7The Macaw is RNG and not guaranteed to appear in a run at all, so this is\n" +
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
