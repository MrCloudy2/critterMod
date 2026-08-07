package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Works out where the player is on the Critter Safari.
 *
 * <p>Whether they are <em>at</em> the Safari comes from the scoreboard sidebar, which
 * names both the island and the zone — see {@link #inSafari()}. Which <em>biome</em>
 * they are standing in comes from the sidebar or tab list if either names one, and
 * otherwise from position via {@link SafariAreaMap}.
 *
 * <p>Use {@code /critters debug} to see what each source reports in-game.
 */
public final class AreaDetector {

	/** Recomputed only when the player has moved, since the HUD asks every frame. */
	private static double cachedX = Double.NaN;
	private static double cachedY = Double.NaN;
	private static double cachedZ = Double.NaN;
	private static SafariBiome cachedBiome;

	private AreaDetector() {
	}

	// --- sources -------------------------------------------------------------

	/** All sidebar lines as plain text, top to bottom. */
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
			String text = team == null
				? entry.ownerName().getString()
				: PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString();
			lines.add(strip(text));
		}
		return lines;
	}

	/**
	 * Tab-list entries. SkyBlock uses these for metadata rows such as
	 * {@code Area: Critter Safari}, alongside real player names.
	 */
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

	/**
	 * Biome from the player's position, via the precomputed Safari area map.
	 * Returns {@code null} outside the map or in an unnamed area such as the hub.
	 */
	public static SafariBiome biomeFromPosition() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return null;

		Vec3 pos = client.player.position();
		// A metre of movement cannot change the answer, and this is polled per frame.
		if (Math.abs(pos.x - cachedX) < 1 && Math.abs(pos.y - cachedY) < 1 && Math.abs(pos.z - cachedZ) < 1) {
			return cachedBiome;
		}
		cachedX = pos.x;
		cachedY = pos.y;
		cachedZ = pos.z;
		cachedBiome = SafariAreaMap.biomeAt(pos.x, pos.y, pos.z);
		return cachedBiome;
	}

	/** Distance to the nearest mapped node — used by {@code /critters debug}. */
	public static double distanceToNearestNode() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return Double.NaN;
		Vec3 pos = client.player.position();
		return SafariAreaMap.distanceToNearestNode(pos.x, pos.y, pos.z);
	}

	// --- combined ------------------------------------------------------------

	/** Biome named by the sidebar or tab list, if either does. */
	public static SafariBiome biomeFromText() {
		for (String line : sidebarLines()) {
			SafariBiome biome = SafariBiome.fromAreaName(line);
			if (biome != null) return biome;
		}
		for (String entry : tabListEntries()) {
			SafariBiome biome = SafariBiome.fromAreaName(entry);
			if (biome != null) return biome;
		}
		return null;
	}

	/** The biome the player is standing in, or {@code null} if it cannot be determined. */
	public static SafariBiome currentBiome() {
		SafariBiome fromText = biomeFromText();
		if (fromText != null) return fromText;
		// Position is only meaningful once we know we are at the Safari; the same
		// coordinates exist on every other island too. Asked live rather than read
		// from the cached flag, so this cannot disagree with the HUD.
		return inSafari() ? biomeFromPosition() : null;
	}

	/**
	 * Whether the player is at the Critter Safari — inside it, or at the Critter Safari
	 * Entrance in Torrhus Canyon. The tracker is wanted in both.
	 *
	 * <p>A plain scan of every sidebar line for the word "Safari" — nothing else. The
	 * area rows carry prefix glyphs that {@link #strip} removes as private-use
	 * characters, so filtering on those prefixes finds nothing at all.
	 *
	 * <p>The sidebar is authoritative in both directions. It has to be: leaving the
	 * Safari sends no chat message and no transfer notice, so a flag set on entry has
	 * nothing to switch it back off. That was the bug where the HUD followed you across
	 * Torrhus Canyon.
	 */
	public static boolean inSafari() {
		List<String> lines = sidebarLines();
		if (lines.isEmpty()) return SafariPresence.inSafari();

		for (String line : lines) {
			if (line.contains("Safari")) {
				SafariPresence.set(true);
				return true;
			}
		}
		SafariPresence.set(false);
		return false;
	}

	/** Strips §-codes and the invisible padding Hypixel pads sidebar lines with. */
	private static String strip(String text) {
		return text.replaceAll("§.", "").replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
	}
}
