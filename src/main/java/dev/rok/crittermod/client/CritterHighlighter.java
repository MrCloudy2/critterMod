package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Outlines the mobs of species that are hard to spot.
 *
 * <p>Uses the client-side glowing flag, so this needs no rendering code and no mixin:
 * a glowing entity is drawn through walls by vanilla, which is exactly the wanted
 * behaviour for something like a Bloodbat tucked into the Haunted geometry.
 *
 * <p>The name tag is an armour stand sitting on top of the mob rather than the mob
 * itself, so the stand locates the species and the actual creature just beneath it
 * gets the outline.
 */
public final class CritterHighlighter {

	private static final int SCAN_INTERVAL_TICKS = 10;
	/** The label sits directly above its mob, so the pairing radius can be tight. */
	private static final double LABEL_TO_MOB_RADIUS = 3.0;

	/** Species worth outlining. Bloodbats are the awkward ones to find by eye. */
	private static final Set<String> HIGHLIGHTED = Set.of("Bloodbat");

	/** Entities currently made to glow, so the flag can be taken back off again. */
	private static final Set<UUID> glowing = new HashSet<>();

	private static int ticks;

	private CritterHighlighter() {
	}

	public static void tick() {
		if (++ticks < SCAN_INTERVAL_TICKS) return;
		ticks = 0;

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		if (!ConfigManager.get().display.highlightHardToFind || !AreaDetector.inSafari()) {
			clear();
			return;
		}

		List<Entity> labels = new ArrayList<>();
		List<Entity> candidates = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			// The name identifies a label, not the entity type: most are armour stands
			// but a Hideyho arrives as a player.
			Critter named = entity.hasCustomName()
				? Critters.byName(strip(entity.getCustomName().getString())) : null;
			if (named != null) {
				if (HIGHLIGHTED.contains(named.name())) labels.add(entity);
			} else if (isMobLike(entity)) {
				candidates.add(entity);
			}
		}

		Set<UUID> wanted = new HashSet<>();
		for (Entity label : labels) {
			Entity mob = nearest(candidates, label);
			// With no mob found, glow the stand itself: invisible, but better than
			// silently highlighting nothing.
			Entity target = mob != null ? mob : label;
			target.setGlowingTag(true);
			wanted.add(target.getUUID());
		}

		// Anything that has stopped qualifying — caught, moved away, or the feature
		// turned off — must have the flag removed or it stays lit for the session.
		for (Entity entity : client.level.entitiesForRendering()) {
			if (glowing.contains(entity.getUUID()) && !wanted.contains(entity.getUUID())) {
				entity.setGlowingTag(false);
			}
		}
		glowing.clear();
		glowing.addAll(wanted);
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

	/** Drops every outline; called when the feature is off or a run ends. */
	public static void clear() {
		Minecraft client = Minecraft.getInstance();
		if (client.level != null && !glowing.isEmpty()) {
			for (Entity entity : client.level.entitiesForRendering()) {
				if (glowing.contains(entity.getUUID())) entity.setGlowingTag(false);
			}
		}
		glowing.clear();
	}

	private static String strip(String text) {
		return text.replaceAll("§.", "").replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
	}
}
