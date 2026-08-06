package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the SkyBlock sidebar to work out where the player is.
 *
 * <p>Hypixel writes the current area into the scoreboard as {@code ⏣ Forest Biome}.
 * The visible text lives in each score holder's team prefix/suffix rather than in
 * the holder name, so lines have to be reassembled the same way vanilla renders
 * them.
 */
public final class AreaDetector {

	private AreaDetector() {
	}

	/** All sidebar lines as plain text, top to bottom, colour codes stripped. */
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
			lines.add(stripFormatting(text));
		}
		return lines;
	}

	/** The biome the player is currently standing in, or {@code null} if not in one. */
	public static SafariBiome currentBiome() {
		for (String line : sidebarLines()) {
			SafariBiome biome = SafariBiome.fromAreaName(line);
			if (biome != null) return biome;
		}
		return null;
	}

	/**
	 * Whether the player is inside the Critter Safari. True both in the four biomes
	 * and in the shared hub area the sidebar labels {@code Critter Safari}.
	 */
	public static boolean inSafari() {
		for (String line : sidebarLines()) {
			if (line.contains("Critter Safari") || SafariBiome.fromAreaName(line) != null) return true;
		}
		return false;
	}

	/**
	 * Strips §-codes and the zero-width/section junk Hypixel pads sidebar lines with,
	 * so {@code "⏣ Forest Biome"} compares cleanly.
	 */
	private static String stripFormatting(String text) {
		return text.replaceAll("§.", "").replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
	}
}
