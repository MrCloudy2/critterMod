package dev.rok.crittermod.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.rok.crittermod.CritterMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small JSON-backed settings file at {@code config/crittermod.json}. */
public final class CritterConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE =
		FabricLoader.getInstance().getConfigDir().resolve("crittermod.json");

	private static CritterConfig instance;

	public boolean hudEnabled = true;
	/** Show a per-player unique-count line for each partymate under the biome rows. */
	public boolean showPerPlayer = true;
	/** Hide the HUD when not in the Critter Safari. */
	public boolean onlyInSafari = true;
	/** Top-right panel listing what is still uncaught in the biome you are standing in. */
	public boolean showMissing = true;
	/** On-screen banner and sound for Gemzie, Wumpa and Doomspiral encounter stages. */
	public boolean bossAlerts = true;
	/** Also announce those stages to party chat. */
	public boolean bossPartyNotify = true;
	/** Announce "<Biome> Done!" when every species there has been caught by someone. */
	public boolean biomeDoneNotify = false;
	/**
	 * Chat command {@code /critters share} posts through, without the slash.
	 * {@code "pc"} is party chat; {@code "ac"} is all chat. Blank posts to normal chat.
	 */
	public String shareCommand = "pc";
	public int hudX = 4;
	public int hudY = 4;

	public static CritterConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	private static CritterConfig load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				CritterConfig loaded = GSON.fromJson(reader, CritterConfig.class);
				if (loaded != null) return loaded;
			} catch (IOException | RuntimeException e) {
				CritterMod.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		return new CritterConfig();
	}

	public void save() {
		try (Writer writer = Files.newBufferedWriter(FILE)) {
			GSON.toJson(this, writer);
		} catch (IOException e) {
			CritterMod.LOGGER.warn("Could not write {}", FILE, e);
		}
	}
}
