package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Outlines the mobs of species that are hard to spot.
 *
 * <p>Vanilla draws a glowing entity through walls, which is exactly the wanted
 * behaviour for a Bloodbat tucked into the Haunted geometry. Getting one to glow from
 * the client is the fiddly part: {@code setGlowingTag} does nothing here, because
 * {@code isCurrentlyGlowing} reads the server-synced shared flag on the client side and
 * only consults the tag on the server. So shared flag 6 is set directly, reached
 * through an access widener since it is protected.
 *
 * <p>It is re-applied on every scan because the server owns that flag and will clear it
 * again whenever it resends the entity's metadata.
 *
 * <p>Which mob belongs to which species comes from {@link CritterEntities}, since the
 * name tag is an entity sitting on top of the mob rather than the mob itself.
 */
public final class CritterHighlighter {

	/** Species worth outlining. Bloodbats are the awkward ones to find by eye. */
	private static final Set<String> HIGHLIGHTED = Set.of("Bloodbat");

	/** Entities currently made to glow, so the flag can be taken back off again. */
	private static final Set<UUID> glowing = new HashSet<>();

	private CritterHighlighter() {
	}

	public static void tick() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		if (!ConfigManager.get().display.highlightHardToFind || !SafariLocation.inSafari()) {
			clear();
			return;
		}

		Set<UUID> wanted = new HashSet<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!HIGHLIGHTED.contains(sighting.critter().name())) continue;
			// With no mob found, the label itself glows: invisible, but better than
			// silently highlighting nothing.
			Entity target = sighting.body();
			setGlow(target, true);
			wanted.add(target.getUUID());
		}

		// Anything that has stopped qualifying — caught, moved away, or the feature
		// turned off — must have the flag removed or it stays lit for the session.
		for (Entity entity : client.level.entitiesForRendering()) {
			if (glowing.contains(entity.getUUID()) && !wanted.contains(entity.getUUID())) {
				setGlow(entity, false);
			}
		}
		glowing.clear();
		glowing.addAll(wanted);
	}

	/** Drops every outline; called when the feature is off or a run ends. */
	public static void clear() {
		Minecraft client = Minecraft.getInstance();
		if (client.level != null && !glowing.isEmpty()) {
			for (Entity entity : client.level.entitiesForRendering()) {
				if (glowing.contains(entity.getUUID())) setGlow(entity, false);
			}
		}
		glowing.clear();
	}

	/** Flag 6 is the glow bit; the tag equivalent is ignored on the client. */
	private static void setGlow(Entity entity, boolean glow) {
		entity.setSharedFlag(6, glow);
	}
}
