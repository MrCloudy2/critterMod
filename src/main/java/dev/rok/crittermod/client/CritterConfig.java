package dev.rok.crittermod.client;

import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;

/**
 * Settings, laid out for MoulConfig — the same config GUI SkyHanni uses.
 *
 * <p>Fields are public and annotated; MoulConfig builds the screen from them and maps
 * them to {@code config/crittermod.json}. See {@link ConfigManager} for the instance
 * and for when the file gets written.
 *
 * <p><strong>Every field that should survive a restart needs {@code @Expose.}</strong>
 * MoulConfig serialises with Gson's {@code excludeFieldsWithoutExposeAnnotation}, so an
 * unannotated field is silently never written and never read — which is what SkyHanni's
 * {@code @Expose} on every config field is for. Values are keyed by field name, so
 * renaming a field resets it unless something migrates the old key across.
 */
public class CritterConfig extends Config {

	/** Where an announcement goes. */
	public enum Broadcast {
		/** Kept to yourself. */
		NONE,
		/** {@code /pc} — the party. */
		PARTY,
		/** {@code /ac} — everyone on the island. */
		ALL;

		/** The Hypixel command, without the slash, or null when nothing is sent. */
		public String command() {
			return switch (this) {
				case PARTY -> "pc";
				case ALL -> "ac";
				case NONE -> null;
			};
		}
	}

	/** How a set of positions is drawn, for the settings that offer the choice. */
	public enum MarkStyle {
		/** Not drawn at all. */
		OFF,
		/** A box where it is, visible only when you could see the thing anyway. */
		HIGHLIGHT,
		/** A box through the terrain, named, with its distance — the full waypoint. */
		WAYPOINT
	}

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
	@Expose
	public DisplayConfig display = new DisplayConfig();

	@Category(name = "Alerts", desc = "Encounter and completion alerts")
	@Expose
	public AlertConfig alerts = new AlertConfig();

	@Category(name = "Party", desc = "What gets announced to your team")
	@Expose
	public PartyConfig party = new PartyConfig();

	@Category(name = "Advanced", desc = "Diagnostics and unfinished work")
	@Expose
	public AdvancedConfig advanced = new AdvancedConfig();

	public static class DisplayConfig {

		@ConfigOption(name = "Progress HUD", desc = "Top-left panel: run timer, party and personal dex, a bar per biome.")
		@ConfigEditorBoolean
		@Expose
		public boolean hudEnabled = true;

		@ConfigOption(name = "Only in the Safari", desc = "Hide the HUD outside the Critter Safari and its entrance.")
		@ConfigEditorBoolean
		@Expose
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
		@Expose
		public boolean uniqueOnly = false;

		@ConfigOption(
			name = "Show nearby spawns",
			desc = "Mark a missing species with how many are loaded around you right now.\n" +
				"§7Read off their name tags, refreshed twice a second.\n" +
				"§7This is not a total and cannot be: the client never sees the far side of\n" +
				"§7the map, and a partymate catching something out of range is never observed.")
		@ConfigEditorBoolean
		@Expose
		public boolean countSpawns = true;

		@ConfigOption(name = "Per-player lines", desc = "Show who is covering which biome, under the biome bars.")
		@ConfigEditorBoolean
		@Expose
		public boolean showPerPlayer = true;

		@ConfigOption(name = "Missing panel", desc = "Top-right list of what is still uncaught in the biome you are standing in.")
		@ConfigEditorBoolean
		@Expose
		public boolean showMissing = true;

		@ConfigOption(name = "Highlight Snooper walls", desc = "Mark unbroken Snooper walls, while you are in the Cavern.")
		@ConfigEditorBoolean
		@Expose
		public boolean highlightSnooperWalls = false;

		@ConfigOption(name = "Highlight Troodon walls", desc = "Mark unbroken Troodon walls, while you are in the Icy biome.")
		@ConfigEditorBoolean
		@Expose
		public boolean highlightTroodonWalls = false;

		@ConfigOption(name = "Highlight bee nests", desc = "Mark bee nests you have not punched yet, while you are in the Forest.")
		@ConfigEditorBoolean
		@Expose
		public boolean highlightNests = false;

		@ConfigOption(name = "Highlight shard trades", desc = "Mark where a Hunter offered a trade.")
		@ConfigEditorBoolean
		@Expose
		public boolean highlightTrades = false;

		@ConfigOption(
			name = "Highlight mounds",
			desc = "Outline detected Rockmite mounds, while you are in the Cavern.\n" +
				"§7Matched on interaction hitbox size, which is weaker evidence than the\n" +
				"§7walls' fixed positions — check the outlines land on real mounds.")
		@ConfigEditorBoolean
		@Expose
		public boolean highlightMounds = false;

		@ConfigOption(
			name = "Hideyho solver",
			desc = "Mark where Hideyho is hiding, while you are in the Haunted biome.\n" +
				"§7Hiding only moves it: its name tag stays loaded on the client the whole\n" +
				"§7time, which is why the missing panel still counts one while nobody can see\n" +
				"§7it. So this is where it actually is, not a list of known hiding spots.")
		@ConfigEditorBoolean
		@Expose
		public boolean hideyhoSolver = false;

		@ConfigOption(
			name = "Recatch helper",
			desc = "Pin where a critter was when you threw a capsule at it.\n" +
				"§7A throw takes it out of the world and puts the CAPTURING ball off to one\n" +
				"§7side; if the capture fails it comes back to the spot it left from and\n" +
				"§7starts running again. The box marks that spot, at the size the critter\n" +
				"§7was, so the next capsule can be in the air before it is.\n" +
				"§7Cleared by catching it, by it running off, or after 40s.")
		@ConfigEditorBoolean
		@Expose
		public boolean recatchHelper = false;

		@ConfigOption(
			name = "Floor drops",
			desc = "Mark the drops lying on the floor.\n" +
				"§7Hypixel makes one out of three string item displays in a single block,\n" +
				"§7which is what this looks for — the same test Skyblocker uses.\n" +
				"§7§lHighlight§r§7 draws a box where it is, seen only when you could see the\n" +
				"§7drop anyway. §lWaypoint§r§7 draws it through the terrain, named and with\n" +
				"§7its distance.")
		@ConfigEditorDropdown(values = {"Nothing", "Highlight", "Waypoint"})
		@Expose
		public int floorDrops = 0;

		/** The chosen style, guarded against a config file holding something out of range. */
		public MarkStyle floorDropStyle() {
			return floorDrops >= 0 && floorDrops < MarkStyle.values().length
				? MarkStyle.values()[floorDrops] : MarkStyle.OFF;
		}

		@ConfigOption(
			name = "Highlight hard-to-find critters",
			desc = "Outline the mobs of species that are awkward to spot, Bloodbats especially.\n" +
				"§7Uses the client-side glow flag, so they show through walls.")
		@ConfigEditorBoolean
		@Expose
		public boolean highlightHardToFind = true;

		@ConfigOption(
			name = "Remove the darkness effect",
			desc = "Drop the Warden darkness while you are at the Safari.\n" +
				"§7Taken off the client's own copy of your effects, so both the dimming and\n" +
				"§7the fog go — vanilla's Darkness Pulsing accessibility slider only scales\n" +
				"§7the dimming. Nothing is sent to the server.")
		@ConfigEditorBoolean
		@Expose
		public boolean removeDarkness = false;

		@ConfigOption(
			name = "Rockmite mounds",
			desc = "Count the mounds still standing near you, under the Cavern missing list.\n" +
				"§7Mounds are found by their hitbox within scanning range, so this is how many\n" +
				"§7are around you rather than how many are left in the Cavern — it is left off\n" +
				"§7entirely when none are in range, rather than shown as a misleading zero.")
		@ConfigEditorBoolean
		@Expose
		public boolean showMoundCount = true;

		@ConfigOption(
			name = "Snooper walls",
			desc = "List the breakable Cavern walls still standing, under the missing list.\n" +
				"§7They want breaking every run to check behind them.\n" +
				"§7Read from the blocks themselves, so it is exact — but a wall in an unloaded\n" +
				"§7chunk is marked \"?\" rather than guessed at, since air and out-of-range look\n" +
				"§7identical from here.")
		@ConfigEditorBoolean
		@Expose
		public boolean showSnooperWalls = true;

		@ConfigOption(
			name = "Troodon walls",
			desc = "List the breakable Icy walls still standing, under the missing list.\n" +
				"§7Same three fixed positions every run, read the same way as the Snooper walls.")
		@ConfigEditorBoolean
		@Expose
		public boolean showTroodonWalls = true;

		@ConfigOption(
			name = "Bee nests",
			desc = "List the bee nests still to punch, under the Forest missing list.\n" +
				"§7Honeybugs come from these. They have no fixed positions, so they are found\n" +
				"§7by sweeping nearby blocks — the count is nests you have come across, not\n" +
				"§7every nest on the map.")
		@ConfigEditorBoolean
		@Expose
		public boolean showNests = true;

		@ConfigOption(
			name = "Hunter trade tracker",
			desc = "Box listing the Hunter NPC offers found this run, nearest first.\n" +
				"§7The chat report scrolls away; this keeps them to hand.")
		@ConfigEditorBoolean
		@Expose
		public boolean showTrades = true;

		@ConfigOption(name = "Edit positions", desc = "Drag the boxes that are on screen to move them, and scroll over one to resize it.")
		@ConfigEditorButton(runnableId = 1, buttonText = "Edit")
		@Expose
		public boolean editPositions = false;

		@ConfigOption(name = "Progress HUD scale", desc = "Size of the top-left panel.")
		@ConfigEditorSlider(minValue = 0.5f, maxValue = 3.0f, minStep = 0.05f)
		@Expose
		public float progressScale = HudBox.DEFAULT_SCALE;

		@ConfigOption(name = "Missing panel scale", desc = "Size of the missing-species list.")
		@ConfigEditorSlider(minValue = 0.5f, maxValue = 3.0f, minStep = 0.05f)
		@Expose
		public float missingScale = HudBox.DEFAULT_SCALE;

		@ConfigOption(name = "Trade tracker scale", desc = "Size of the Hunter trade box.")
		@ConfigEditorSlider(minValue = 0.5f, maxValue = 3.0f, minStep = 0.05f)
		@Expose
		public float tradesScale = HudBox.DEFAULT_SCALE;

		// --- colours -------------------------------------------------------------

		/** Accordion id for the colour pickers, kept together so they fold away. */
		private static final int COLOURS = 2;

		@ConfigOption(name = "Waypoint colours", desc = "The colour of each kind of mark.")
		@ConfigEditorAccordion(id = COLOURS)
		public boolean coloursAccordion = false;

		@ConfigOption(name = "Snooper walls", desc = "")
		@ConfigEditorColour
		@ConfigAccordionId(id = COLOURS)
		@Expose
		public String snooperWallColour = colour(0xFF, 0xAA, 0x00);

		@ConfigOption(name = "Troodon walls", desc = "")
		@ConfigEditorColour
		@ConfigAccordionId(id = COLOURS)
		@Expose
		public String troodonWallColour = colour(0x55, 0xAA, 0xFF);

		@ConfigOption(name = "Bee nests", desc = "")
		@ConfigEditorColour
		@ConfigAccordionId(id = COLOURS)
		@Expose
		public String nestColour = colour(0x55, 0xFF, 0x55);

		@ConfigOption(name = "Hunter trades", desc = "")
		@ConfigEditorColour
		@ConfigAccordionId(id = COLOURS)
		@Expose
		public String tradeColour = colour(0x55, 0xFF, 0xFF);

		@ConfigOption(name = "Hideyho", desc = "")
		@ConfigEditorColour
		@ConfigAccordionId(id = COLOURS)
		@Expose
		public String hideyhoColour = colour(0xFF, 0x55, 0xFF);

		@ConfigOption(name = "Rockmite mounds", desc = "")
		@ConfigEditorColour
		@ConfigAccordionId(id = COLOURS)
		@Expose
		public String moundColour = colour(0xCC, 0x77, 0x44);

		@ConfigOption(name = "Recatch spot", desc = "")
		@ConfigEditorColour
		@ConfigAccordionId(id = COLOURS)
		@Expose
		public String recatchColour = colour(0xFF, 0xFF, 0x55);

		@ConfigOption(name = "Floor drops", desc = "")
		@ConfigEditorColour
		@ConfigAccordionId(id = COLOURS)
		@Expose
		public String floorDropColour = colour(0x55, 0xFF, 0xAA);

		/**
		 * An opaque, non-chroma colour in MoulConfig's own format.
		 *
		 * <p>{@code speed:alpha:r:g:b} — speed 0 meaning it does not cycle. Stored as that
		 * string rather than as an int so the picker can offer alpha and chroma at all.
		 */
		private static String colour(int red, int green, int blue) {
			return ChromaColour.Companion.special(0, 255, red, green, blue);
		}

		// Positions are fractions of the screen, so a box stays put across resolution
		// and GUI-scale changes. Set by dragging in the editor, not by hand.
		@Expose
		public float progressX = 0.004f;
		@Expose
		public float progressY = 0.006f;
		@Expose
		public float missingX = 0.78f;
		@Expose
		public float missingY = 0.006f;
		@Expose
		public float tradesX = 0.006f;
		@Expose
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
		@Expose
		public boolean debugCommands = false;

		@ConfigOption(
			name = "Rockmite mounds",
			desc = "Count the mounds broken this run and how many held a Rockmite.\n" +
				"§7Off by default: only the outcome is known, from chat. How many mounds are\n" +
				"§7left nearby needs whatever a mound is to the client, which is still open.")
		@ConfigEditorBoolean
		@Expose
		public boolean showMounds = false;
	}

	public static class AlertConfig {

		@ConfigOption(
			name = "Play a sound",
			desc = "Play a note alongside every banner this mod shows.\n" +
				"§7Off by default: the banner is already in the middle of the screen, and a\n" +
				"§7run fires plenty of them. Covers the encounters, the completions and the\n" +
				"§7Macaw call — the pitch still varies by what happened.")
		@ConfigEditorBoolean
		@Expose
		public boolean alertSound = false;

		@ConfigOption(
			name = "Gemzie alerts",
			desc = "Banner and sound as the Gemzie encounter progresses.\n" +
				"§7Chamber opens, then the three catches that close it.")
		@ConfigEditorBoolean
		@Expose
		public boolean gemzieAlert = true;

		@ConfigOption(
			name = "Wumpa alerts",
			desc = "Banner and sound as the Wumpa encounter progresses.\n" +
				"§7Footsteps ~30s out, awoken, then the cave reopening.")
		@ConfigEditorBoolean
		@Expose
		public boolean wumpaAlert = true;

		@ConfigOption(
			name = "Doomspiral alerts",
			desc = "Banner and sound as the Doomspiral encounter progresses.\n" +
				"§7Candle ritual, summoned, then it retreating.")
		@ConfigEditorBoolean
		@Expose
		public boolean doomspiralAlert = true;

		@ConfigOption(name = "Biome complete", desc = "Announce \"<Biome> Done!\" once every species there has been caught by someone.")
		@ConfigEditorBoolean
		@Expose
		public boolean biomeDoneNotify = false;

		@ConfigOption(
			name = "All but Macaw",
			desc = "Announce \"Everything except Macaw done!\" when the only species left is the Macaw.\n" +
				"§7The Macaw is RNG and not guaranteed to appear in a run at all, so this is\n" +
				"§7usually the real finish line.")
		@ConfigEditorBoolean
		@Expose
		public boolean allButMacawNotify = false;

		@ConfigOption(name = "Everything done", desc = "Announce \"Everything Done!\" once all 37 species have been caught by someone.")
		@ConfigEditorBoolean
		@Expose
		public boolean allDoneNotify = false;

		@ConfigOption(
			name = "Macaw spawned",
			desc = "Banner and chat line the moment a Macaw turns up.\n" +
				"§7The one species a run is not guaranteed to give at all, so a spawn is\n" +
				"§7news. Noticed from the Birdfeeder's own announcement, which arrives\n" +
				"§7wherever you are, and from one appearing in the world, which comes with\n" +
				"§7coordinates.")
		@ConfigEditorBoolean
		@Expose
		public boolean macawAlert = true;

		@ConfigOption(
			name = "Hunter trades",
			desc = "Report the roaming Hunter NPCs' shard-for-item offers, with the biome they are in.\n" +
				"§7Their dialog is only shown to whoever clicked them, so an offer can go unused\n" +
				"§7while someone else is carrying exactly the item it wants.")
		@ConfigEditorBoolean
		@Expose
		public boolean traderAlerts = true;
	}

	public static class PartyConfig {

		// Each announcement chooses its own audience: a party wants the encounter
		// stages, while a Macaw or a spare trade is worth the whole island hearing.
		// Lines are spaced 1.2s apart whatever the channel; Hypixel drops faster bursts.

		@ConfigOption(name = "Gemzie stages", desc = "Where the Gemzie encounter stages are posted.")
		@ConfigEditorDropdown(values = {"Nobody", "Party chat", "All chat"})
		@Expose
		public int gemzieBroadcast = 1;

		@ConfigOption(name = "Wumpa stages", desc = "Where the Wumpa encounter stages are posted.")
		@ConfigEditorDropdown(values = {"Nobody", "Party chat", "All chat"})
		@Expose
		public int wumpaBroadcast = 1;

		@ConfigOption(name = "Doomspiral stages", desc = "Where the Doomspiral encounter stages are posted.")
		@ConfigEditorDropdown(values = {"Nobody", "Party chat", "All chat"})
		@Expose
		public int doomspiralBroadcast = 1;

		@ConfigOption(
			name = "Completions",
			desc = "Where \"<Biome> Done!\" and the whole-run milestones are posted.")
		@ConfigEditorDropdown(values = {"Nobody", "Party chat", "All chat"})
		@Expose
		public int milestoneBroadcast = 1;

		@ConfigOption(
			name = "Macaw spawns",
			desc = "Where a Macaw spawn is posted, with its position if the client can see it.\n" +
				"§7Whoever is working another biome wants to know there is something to come\n" +
				"§7back for.")
		@ConfigEditorDropdown(values = {"Nobody", "Party chat", "All chat"})
		@Expose
		public int macawBroadcast = 1;

		@ConfigOption(name = "Hunter trades", desc = "Where the roaming Hunter NPCs' offers are posted.")
		@ConfigEditorDropdown(values = {"Nobody", "Party chat", "All chat"})
		@Expose
		public int traderBroadcast = 1;

		@ConfigOption(
			name = "Take partymates' trades",
			desc = "Read trades announced by other people running this mod, off party chat.\n" +
				"§7A Hunter's dialog is only shown to whoever clicked it, so their client is\n" +
				"§7the only one that knows the offer — this puts it in your tracker and on a\n" +
				"§7waypoint just as if you had found it.")
		@ConfigEditorBoolean
		@Expose
		public boolean acceptSharedTrades = true;

		@ConfigOption(name = "Post /critters share to", desc = "Where the missing list goes when you share it by hand.")
		@ConfigEditorDropdown(values = {"Party chat", "All chat", "Normal chat"})
		@Expose
		public int shareChannel = 0;

		/** The Hypixel command for the manual share channel, without the slash. */
		public String shareCommand() {
			return switch (shareChannel) {
				case 1 -> "ac";
				case 2 -> "";
				default -> "pc";
			};
		}

		public Broadcast gemzie() {
			return broadcast(gemzieBroadcast);
		}

		public Broadcast wumpa() {
			return broadcast(wumpaBroadcast);
		}

		public Broadcast doomspiral() {
			return broadcast(doomspiralBroadcast);
		}

		public Broadcast milestones() {
			return broadcast(milestoneBroadcast);
		}

		public Broadcast macaw() {
			return broadcast(macawBroadcast);
		}

		public Broadcast trades() {
			return broadcast(traderBroadcast);
		}

		/** Guards against a config file holding an index that is no longer a choice. */
		private static Broadcast broadcast(int index) {
			return index >= 0 && index < Broadcast.values().length
				? Broadcast.values()[index] : Broadcast.NONE;
		}
	}
}
