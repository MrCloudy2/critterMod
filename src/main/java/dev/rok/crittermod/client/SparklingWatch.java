package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Calls out a sparkling critter the moment one is loaded.
 *
 * <p>Chat already says when someone <em>catches</em> one — {@code SPARKLING! Jaegerss
 * caught a SPARKLING Rockmite!} — which is too late to be of use. Finding one before
 * that is done by eye today; parties resort to "theres a sparkling somewhere in icy".
 *
 * <p>This reads the name tag, on the assumption that a sparkling one is named for it.
 * That assumption is the weak part: if the tag turns out to read the same as any other
 * critter, nothing here will ever fire, and the particles around it are the evidence to
 * use instead — see the notes on identifying them. Nothing else in the mod depends on
 * this, so a wrong assumption costs a feature rather than breaking a run.
 */
public final class SparklingWatch {

	/** Labels already called out, so it announces once rather than every sweep. */
	private static final Set<UUID> announced = new HashSet<>();

	private SparklingWatch() {
	}

	public static void tick() {
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!sighting.sparkling()) continue;
			// Keyed on the label rather than the mob: the label is what named it, and it
			// is the entity that survives the pairing being ambiguous.
			if (!announced.add(sighting.label().getUUID())) continue;
			alert(sighting);
		}
	}

	private static void alert(CritterEntities.Sighting sighting) {
		CritterConfig config = ConfigManager.get();
		BlockPos pos = sighting.body().blockPosition();
		String where = describe(pos);

		if (config.alerts.sparklingAlert) {
			SparklingAlert.show(sighting.critter().name(), where);
		}
		EncounterAlerts.post(config.party.sparkling(),
			"SPARKLING %s! (%s)".formatted(sighting.critter().name(), where));
	}

	/** Biome and coordinates, so a partymate can be sent straight to it. */
	private static String describe(BlockPos pos) {
		var biome = SafariAreaMap.biomeAt(pos.getX(), pos.getY(), pos.getZ());
		String coords = "%d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ());
		return biome == null ? coords : biome.displayName() + " " + coords;
	}

	/** How far the alerted critter is, for the player's own line. Unused when none. */
	public static double distanceTo(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return -1;
		return Math.sqrt(client.player.position()
			.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
	}

	/** A new run has its own sparklings; the last one's are gone. */
	public static void reset() {
		announced.clear();
		SparklingAlert.clear();
	}
}
