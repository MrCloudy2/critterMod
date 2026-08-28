package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

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
 * the recatch helper and the sparkling call — and each used to sweep the entity list for
 * itself. One sweep, read by all of them.
 */
public final class CritterEntities {

	/** Spawns do not appear fast enough to be worth scanning every tick. */
	private static final int SCAN_INTERVAL_TICKS = 5;
	/** The label sits directly above its mob, so the pairing radius can be tight. */
	private static final double LABEL_TO_MOB_RADIUS = 3.0;
	/** The word a rare variant is assumed to carry in its name. */
	private static final String SPARKLING = "Sparkling";

	private static List<Sighting> sightings = List.of();
	private static long scannedAt;
	private static int ticks;

	/**
	 * One labelled critter. {@code mob} is null when nothing was found under the label.
	 *
	 * <p>{@code sparkling} is read off the label naming it.
	 *
	 * <p>Two flags keep this honest, because the client is handed every critter within
	 * Hypixel's tracking range regardless of what is between you and it:
	 *
	 * <ul>
	 * <li>{@code visible} — on screen and unobstructed. What anything that
	 *     <em>announces</em> should use.
	 * <li>{@code unobstructed} — nothing in the way, but possibly behind you. For things
	 *     that are counted rather than announced, where a number dropping to zero as you
	 *     turned round would look like a bug.
	 * </ul>
	 *
	 * <p>An earlier version reasoned that name tags render through terrain and so nothing
	 * was being revealed; that is only true of entities the game is actually drawing,
	 * which is a much smaller set than the one scanned here.
	 */
	public record Sighting(Critter critter, Entity label, Entity mob, boolean sparkling,
						   boolean visible, boolean unobstructed) {
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
		List<Boolean> sparkling = new ArrayList<>();
		List<Entity> candidates = new ArrayList<>();

		for (Entity entity : client.level.entitiesForRendering()) {
			// The name identifies a label, not the entity type: most are armour stands
			// but a Hideyho arrives as a player.
			String name = entity.hasCustomName()
				? SafariLocation.strip(entity.getCustomName().getString()) : null;
			Critter named = name == null ? null : Critters.byName(name);
			boolean rare = false;

			// A sparkling one is assumed to be named for it. Only this one prefix is
			// tolerated, rather than searching the label for any species name: an armour
			// stand reading "Tepid Shard" must not count as a Tepid.
			if (named == null && name != null && startsWithSparkling(name)) {
				named = Critters.byName(name.substring(SPARKLING.length()).trim());
				rare = named != null;
			}

			if (named != null) {
				labels.add(entity);
				species.add(named);
				sparkling.add(rare);
			} else if (isMobLike(entity)) {
				candidates.add(entity);
			}
		}

		List<Sighting> result = new ArrayList<>(labels.size());
		for (int i = 0; i < labels.size(); i++) {
			Entity label = labels.get(i);
			Entity mob = nearest(candidates, label);
			// Worked out once per sweep rather than per reader: the answer has to be the
			// same for all of them within a sweep — the sparkling call announces once per
			// critter, so a wavering answer would either lose the announcement or repeat
			// it. The ray is the expensive half and is shared between the two flags.
			Entity body = mob != null ? mob : label;
			boolean unobstructed = Visibility.hasLineTo(body);
			boolean visible = unobstructed && Visibility.inView(body.getBoundingBox());
			result.add(new Sighting(species.get(i), label, mob, sparkling.get(i),
				visible, unobstructed));
		}
		return result;
	}

	private static boolean startsWithSparkling(String name) {
		return name.length() > SPARKLING.length()
			&& name.substring(0, SPARKLING.length()).equalsIgnoreCase(SPARKLING);
	}

	/** Excludes the scaffolding entities Hypixel builds its props out of. */
	private static boolean isMobLike(Entity entity) {
		EntityType<?> type = entity.getType();
		return type != EntityTypes.ARMOR_STAND
			&& type != EntityTypes.INTERACTION
			&& type != EntityTypes.ITEM_DISPLAY
			&& type != EntityTypes.BLOCK_DISPLAY
			&& type != EntityTypes.TEXT_DISPLAY
			&& type != EntityTypes.PLAYER
			&& type != EntityTypes.ITEM;
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
