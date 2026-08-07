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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out which Safari biome the player is in, from three independent sources
 * tried in order of trustworthiness.
 *
 * <ol>
 *   <li><b>Scoreboard sidebar</b> — only if it actually names a biome. It does not
 *       appear to on the Safari, so this is kept as a cheap override rather than
 *       relied upon.</li>
 *   <li><b>Tab list</b> — SkyBlock lists the current area as a player-list entry.</li>
 *   <li><b>Position</b> — the Safari is a fixed map and the four biomes sit on a
 *       clean 2x2 grid, so nearest-centre classification is unambiguous.</li>
 * </ol>
 *
 * Use {@code /critters debug} to see what each source reports in-game.
 */
public final class AreaDetector {

	/**
	 * Biome centres on the Safari map, as shipped by SkyHanni's SafariNamesInCenter.
	 * They form a 2x2 grid split near x=-50 and z=0, roughly 46 blocks apart, so the
	 * nearest centre in the XZ plane identifies the biome without ambiguity.
	 */
	private static final Map<SafariBiome, Vec3> BIOME_CENTRES = Map.of(
		SafariBiome.FOREST, new Vec3(-27.1, 66.0, 22.8),
		SafariBiome.HAUNTED, new Vec3(-25.5, 66.0, -23.2),
		SafariBiome.ICY, new Vec3(-73.3, 65.0, -23.4),
		SafariBiome.CAVERN, new Vec3(-72.6, 65.5, 23.9)
	);

	/** Beyond this distance from every centre, position tells us nothing useful. */
	private static final double MAX_CENTRE_DISTANCE = 90.0;

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

	/** Nearest biome centre in the XZ plane, or {@code null} if the player is far away. */
	public static SafariBiome biomeFromPosition() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return null;

		Vec3 pos = client.player.position();
		SafariBiome nearest = null;
		double best = Double.MAX_VALUE;
		for (Map.Entry<SafariBiome, Vec3> entry : BIOME_CENTRES.entrySet()) {
			double distance = horizontalDistance(pos, entry.getValue());
			if (distance < best) {
				best = distance;
				nearest = entry.getKey();
			}
		}
		return best <= MAX_CENTRE_DISTANCE ? nearest : null;
	}

	/** Distance from the player to each biome centre — used by {@code /critters debug}. */
	public static Map<SafariBiome, Double> centreDistances() {
		Minecraft client = Minecraft.getInstance();
		Map<SafariBiome, Double> distances = new LinkedHashMap<>();
		if (client.player == null) return distances;

		Vec3 pos = client.player.position();
		for (SafariBiome biome : SafariBiome.values()) {
			distances.put(biome, horizontalDistance(pos, BIOME_CENTRES.get(biome)));
		}
		return distances;
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
		// Position is only meaningful once we know we are on the Safari island;
		// the same coordinates exist on every other island too.
		return SafariPresence.inSafari() ? biomeFromPosition() : null;
	}

	/**
	 * Whether the player is inside the Critter Safari.
	 *
	 * <p>Text sources are authoritative when they mention the Safari. Otherwise this
	 * falls back to the chat-driven state, since entering is announced but leaving is
	 * not — a server transfer is what ends a run.
	 */
	public static boolean inSafari() {
		for (String line : sidebarLines()) {
			if (mentionsSafari(line)) return true;
		}
		for (String entry : tabListEntries()) {
			if (mentionsSafari(entry)) return true;
		}
		return SafariPresence.inSafari();
	}

	private static boolean mentionsSafari(String text) {
		return text.contains("Critter Safari") || SafariBiome.fromAreaName(text) != null;
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Strips §-codes and the invisible padding Hypixel pads sidebar lines with. */
	private static String strip(String text) {
		return text.replaceAll("§.", "").replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
	}
}
