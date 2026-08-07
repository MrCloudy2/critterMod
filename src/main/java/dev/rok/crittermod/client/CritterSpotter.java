package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.session.SafariSession;
import dev.rok.crittermod.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

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
 * <p>This only ever sees what is loaded nearby, so the count is a floor that grows as
 * the biome is explored — never an over-count, which is what makes it safe to use as a
 * denominator.
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
		if (session == null || !AreaDetector.inSafari()) return;

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		for (Entity entity : client.level.entitiesForRendering()) {
			// Only the label stands carry the species name; the mob underneath is an
			// ordinary vanilla entity that says nothing useful.
			if (entity.getType() != EntityType.ARMOR_STAND) continue;
			if (!entity.hasCustomName()) continue;

			String label = strip(entity.getCustomName().getString());
			// Exact match, not a substring search: an armour stand reading "Tepid Shard"
			// or a hologram mentioning a species must not be counted as a spawn.
			Critter critter = Critters.byName(label);
			if (critter == null) continue;

			session.markSeen(critter, entity.getUUID());
		}
	}

	private static String strip(String text) {
		return text.replaceAll("§.", "").replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
	}
}
