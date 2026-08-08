package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.session.SafariSession;
import dev.rok.crittermod.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Counts how many of each species actually spawned, by watching the world.
 *
 * <p>Most species spawn a randomised number per run, so no fixed table can say how
 * many there are to catch. Hypixel labels each critter with an armour stand whose
 * custom name is exactly the species name and which carries its own UUID, so distinct
 * spawns can simply be counted:
 *
 * <pre>
 * Tepid         armor_stand 27m  Tepid  uuid 828fcb13
 * Mantis Shrimp armor_stand 41m  Mantis Shrimp  uuid e20b2da5
 * </pre>
 *
 * <p>This reports only what is loaded <em>right now</em>. It is not a total and cannot
 * be: the client never sees the far side of the map, and a partymate catching something
 * out of render distance is never observed. Counting distinct entity ids over time does
 * not fix that and adds its own error — a critter that escapes a capsule returns as a
 * new entity, so such a total only ever climbs.
 *
 * <p>What it is good for is "there is one of these next to you that nobody has caught".
 */
public final class CritterSpotter {

	/** Scanning every tick is wasted work; spawns do not appear that fast. */
	private static final int SCAN_INTERVAL_TICKS = 10;

	private static int ticks;

	private CritterSpotter() {
	}

	public static void tick() {
		if (++ticks < SCAN_INTERVAL_TICKS) return;
		ticks = 0;

		if (!ConfigManager.get().display.countSpawns) return;

		SafariSession session = SessionManager.current();
		if (session == null || !SafariLocation.inSafari()) return;

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		Map<Critter, Integer> present = new HashMap<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			// Usually an armour stand, but not always — a Hideyho label arrives as a
			// player entity — so the name is what identifies it, not the type.
			if (!entity.hasCustomName()) continue;

			String label = strip(entity.getCustomName().getString());
			// Exact match, not a substring search: an armour stand reading "Tepid Shard"
			// or a hologram mentioning a species must not be counted as a spawn.
			Critter critter = Critters.byName(label);
			if (critter == null) continue;

			present.merge(critter, 1, Integer::sum);
		}
		// Replaced wholesale rather than merged, so anything caught or despawned since
		// the last scan simply drops out.
		session.setNearby(present);
	}

	private static String strip(String text) {
		return text.replaceAll("§.", "").replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
	}
}
