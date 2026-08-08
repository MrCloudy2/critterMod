package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * The critters the client can currently see, scanned once per pass.
 *
 * <p>Hypixel labels every critter with an entity whose custom name is exactly the
 * species name, sitting on top of the mob rather than being it. So a sighting is two
 * entities: the label that says what it is, and the body underneath that says where it
 * is and how big it is.
 *
 * <p>Four features want this — the nearby-spawn counter, the Bloodbat outlines, the
 * Hideyho solver and the recatch helper — and each used to sweep the entity list for
 * itself. One sweep, read by all of them.
 */
public final class CritterEntities {

	/** Spawns do not appear fast enough to be worth scanning every tick. */
	private static final int SCAN_INTERVAL_TICKS = 5;
	/** The label sits directly above its mob, so the pairing radius can be tight. */
	private static final double LABEL_TO_MOB_RADIUS = 3.0;

	private static List<Sighting> sightings = List.of();
	private static long scannedAt;
	private static int ticks;

	/** One labelled critter. {@code mob} is null when nothing was found under the label. */
	public record Sighting(Critter critter, Entity label, Entity mob) {
		/** The entity to point at: the mob if there is one, otherwise the label itself. */
		public Entity body() {
			return mob != null ? mob : label;
		}
	}

	private CritterEntities() {
	}

	public static void tick() {
		if (++ticks < SCAN_INTERVAL_TICKS) return;
		ticks = 0;

		Minecraft client = Minecraft.getInstance();
		scannedAt = System.currentTimeMillis();
		if (client.level == null || !SafariLocation.inSafari()) {
			sightings = List.of();
			return;
		}
		sightings = scan(client);
	}

	/** Every critter currently labelled in the world. Never null; empty outside the Safari. */
	public static List<Sighting> all() {
		return sightings;
	}

	/**
	 * When the list was last rebuilt.
	 *
	 * <p>Between sweeps {@link #all()} returns the previous answer, so anything keeping
	 * its own timestamps has to key off this rather than off the tick it read them on.
	 */
	public static long scannedAt() {
		return scannedAt;
	}

	private static List<Sighting> scan(Minecraft client) {
		List<Entity> labels = new ArrayList<>();
		List<Critter> species = new ArrayList<>();
		List<Entity> candidates = new ArrayList<>();

		for (Entity entity : client.level.entitiesForRendering()) {
			// The name identifies a label, not the entity type: most are armour stands
			// but a Hideyho arrives as a player.
			Critter named = entity.hasCustomName()
				? Critters.byName(SafariLocation.strip(entity.getCustomName().getString())) : null;
			if (named != null) {
				labels.add(entity);
				species.add(named);
			} else if (isMobLike(entity)) {
				candidates.add(entity);
			}
		}

		List<Sighting> result = new ArrayList<>(labels.size());
		for (int i = 0; i < labels.size(); i++) {
			result.add(new Sighting(species.get(i), labels.get(i), nearest(candidates, labels.get(i))));
		}
		return result;
	}

	/** Excludes the scaffolding entities Hypixel builds its props out of. */
	private static boolean isMobLike(Entity entity) {
		EntityType<?> type = entity.getType();
		return type != EntityType.ARMOR_STAND
			&& type != EntityType.INTERACTION
			&& type != EntityType.ITEM_DISPLAY
			&& type != EntityType.BLOCK_DISPLAY
			&& type != EntityType.TEXT_DISPLAY
			&& type != EntityType.PLAYER
			&& type != EntityType.ITEM;
	}

	private static Entity nearest(List<Entity> candidates, Entity label) {
		Entity best = null;
		double bestSq = LABEL_TO_MOB_RADIUS * LABEL_TO_MOB_RADIUS;
		for (Entity candidate : candidates) {
			double distanceSq = candidate.position().distanceToSqr(label.position());
			if (distanceSq >= bestSq) continue;
			bestSq = distanceSq;
			best = candidate;
		}
		return best;
	}
}
