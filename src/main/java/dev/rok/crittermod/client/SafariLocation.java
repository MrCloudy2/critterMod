package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one place that answers "where is the player".
 *
 * <p>Resolved once per client tick and cached; everything else reads {@link #inSafari()}
 * and {@link #biome()} rather than working it out for itself. Several independent
 * guesses is how the HUD ended up stuck on in the Hub — each source could turn the flag
 * on and only some could turn it off.
 *
 * <p>The authority is SkyBlock's own area line, the same one SkyHanni keys off:
 * a scoreboard line of the shape {@code §7⏣ §bHub}, with the tab list's {@code Area: …}
 * row as the other place the server states it. It is authoritative in <em>both</em>
 * directions — an area line that does not say Safari means the player is not at the
 * Safari, whatever anything else thinks, and it clears the chat flag.
 *
 * <p>The rest only fills the gap where the server has not stated an area yet, right
 * after a transfer: a critter name tag in the world, then the entry message from chat.
 * The chat flag is also dropped on every world change, since a Hypixel server switch
 * always reconnects and leaving the Safari is never announced.
 */
public final class SafariLocation {

	/** Where the current answer came from; shown by {@code /critters debug}. */
	public enum Source {
		/** The server's own area line — the only self-clearing source. */
		AREA_LINE,
		/** A critter name tag in the world, used before the area line arrives. */
		CRITTER_LABELS,
		/** The "entered Critter Safari!" message, used before anything else lands. */
		CHAT,
		/** Nothing says the player is at the Safari. */
		NONE
	}

	/**
	 * SkyHanni's scoreboard area pattern: a colour code, the area symbol, then the name.
	 * The symbol is matched as "any character" on purpose — Hypixel renders it with a
	 * private-use glyph in places, which is exactly what stripping formatting removes.
	 */
	private static final Pattern AREA_LINE = Pattern.compile("\\s*§.(?<symbol>.) §.(?<area>.*)");

	/** Symbols on scoreboard lines that shape like an area line but are not one. */
	private static final String NON_AREA_SYMBOLS = "♲☀";

	private static final String TAB_AREA_PREFIX = "Area: ";

	private static String area;
	private static boolean inSafari;
	private static SafariBiome biome;
	private static Source source = Source.NONE;

	/** Set by the entry message; only consulted while the server has stated no area. */
	private static boolean chatEntered;

	private SafariLocation() {
	}

	// --- the answer ----------------------------------------------------------

	/** Whether the player is at the Critter Safari, its entrance included. */
	public static boolean inSafari() {
		return inSafari;
	}

	/** The biome being stood in, or {@code null} when it cannot be determined. */
	public static SafariBiome biome() {
		return biome;
	}

	/** The area the server says the player is in, or {@code null} if it has not said. */
	public static String area() {
		return area;
	}

	public static Source source() {
		return source;
	}

	// --- updating ------------------------------------------------------------

	/** Recomputes the location. Called once per client tick, before anything reads it. */
	public static void tick() {
		String stated = statedArea();
		if (stated != null) {
			area = stated;
			inSafari = stated.contains("Safari");
			source = Source.AREA_LINE;
			// The server has spoken, so the chat flag has nothing left to add. Clearing
			// it here is what stops a run "following" the player out to the Hub.
			chatEntered = inSafari;
		} else if (critterLabelsNearby()) {
			area = null;
			inSafari = true;
			source = Source.CRITTER_LABELS;
		} else if (chatEntered) {
			area = null;
			inSafari = true;
			source = Source.CHAT;
		} else {
			area = null;
			inSafari = false;
			source = Source.NONE;
		}

		biome = inSafari ? resolveBiome() : null;
	}

	/**
	 * Notes the entry message. Applied immediately as well as recorded, so a catch
	 * arriving in the same tick as the banner is not treated as happening elsewhere.
	 */
	public static void onChatMessage(String line) {
		if (line.endsWith("entered Critter Safari!")) markEntered();
	}

	/** Marks the player as being at the Safari on evidence other than the area line. */
	public static void markEntered() {
		chatEntered = true;
		inSafari = true;
		if (source == Source.NONE) source = Source.CHAT;
	}

	/**
	 * Forgets everything on a world change.
	 *
	 * <p>Hypixel never announces leaving the Safari, but every island change reconnects
	 * the play connection, so this is the one moment the chat flag can be known stale.
	 */
	public static void onWorldChange() {
		chatEntered = false;
		inSafari = false;
		area = null;
		biome = null;
		source = Source.NONE;
	}

	// --- reading the server's area line --------------------------------------

	/** The area SkyBlock states, from the scoreboard or the tab list, or {@code null}. */
	private static String statedArea() {
		for (String raw : sidebarLines()) {
			Matcher matcher = AREA_LINE.matcher(raw);
			if (!matcher.matches()) continue;
			String symbol = matcher.group("symbol");
			if (!symbol.isEmpty() && NON_AREA_SYMBOLS.indexOf(symbol.charAt(0)) >= 0) continue;
			String name = strip(matcher.group("area"));
			if (!name.isEmpty()) return name;
		}
		for (String entry : tabListEntries()) {
			if (entry.startsWith(TAB_AREA_PREFIX)) {
				String name = entry.substring(TAB_AREA_PREFIX.length()).trim();
				if (!name.isEmpty()) return name;
			}
		}
		return null;
	}

	/** Sidebar lines with their formatting intact, top to bottom. */
	public static List<String> sidebarLines() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return List.of();

		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) return List.of();

		List<String> lines = new ArrayList<>();
		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
			if (entry.isHidden()) continue;
			PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
			lines.add(team == null
				? entry.ownerName().getString()
				: PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString());
		}
		return lines;
	}

	/** Tab-list entries, stripped. SkyBlock puts metadata rows among the player names. */
	public static List<String> tabListEntries() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) return List.of();

		List<String> entries = new ArrayList<>();
		for (PlayerInfo info : client.player.connection.getOnlinePlayers()) {
			if (info.getTabListDisplayName() == null) continue;
			String text = strip(info.getTabListDisplayName().getString());
			if (!text.isEmpty()) entries.add(text);
		}
		return entries;
	}

	// --- which biome ---------------------------------------------------------

	/**
	 * The biome within the Safari.
	 *
	 * <p>The area line names the Safari but not the biome inside it, so this is nearly
	 * always answered from position against the mapped areas.
	 */
	private static SafariBiome resolveBiome() {
		if (area != null) {
			SafariBiome named = SafariBiome.fromAreaName(area);
			if (named != null) return named;
		}
		return biomeFromPosition();
	}

	/** Biome from the player's position, or {@code null} outside the mapped areas. */
	public static SafariBiome biomeFromPosition() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return null;
		Vec3 pos = client.player.position();
		return SafariAreaMap.biomeAt(pos.x, pos.y, pos.z);
	}

	/** Distance to the nearest mapped node — used by {@code /critters debug}. */
	public static double distanceToNearestNode() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return Double.NaN;
		Vec3 pos = client.player.position();
		return SafariAreaMap.distanceToNearestNode(pos.x, pos.y, pos.z);
	}

	/**
	 * Whether any critter name tag is loaded.
	 *
	 * <p>Hypixel labels every critter with an entity whose custom name is exactly the
	 * species name, and those exist nowhere else. Any entity type counts: most are
	 * armour stands but a Hideyho arrives as a player.
	 */
	public static boolean critterLabelsNearby() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return false;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!entity.hasCustomName()) continue;
			if (Critters.byName(strip(entity.getCustomName().getString())) != null) return true;
		}
		return false;
	}

	/** Strips §-codes and the invisible padding Hypixel puts in sidebar lines. */
	public static String strip(String text) {
		return text.replaceAll("§.", "").replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
	}
}
