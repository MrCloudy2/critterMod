package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * Finds the hiding Hideyho.
 *
 * <p>Hideyho hides somewhere in the Haunted biome and asks you to come and find it.
 * The thing is that hiding is only a matter of where it stands: the labelled entity
 * stays loaded on the client the whole time, which is why the missing panel keeps
 * reporting "1 near" while nobody can see it. Marking that entity's position is
 * therefore not a lookup table of hiding spots — it is where it actually is, this run.
 *
 * <p>Matched on the name tag rather than the mob, exactly as the nearby-spawn counter
 * does, since that is the evidence the entity is there at all. SkyHanni goes the other
 * way and walks a list of 18 known spots, which is why its finder can miss.
 *
 * <p>Scanned on a timer rather than per frame: it does not move while hidden, and
 * re-hiding gives plenty of time to catch up.
 */
public final class HideyhoSolver {

	private static final String NAME = "Hideyho";
	private static final int SCAN_INTERVAL_TICKS = 10;

	private static BlockPos found;
	private static int ticks;

	private HideyhoSolver() {
	}

	public static void tick() {
		if (++ticks < SCAN_INTERVAL_TICKS) return;
		ticks = 0;

		// Deliberately not gated on the setting: whether one is loaded at all is what
		// {@code /critters waypoints} is for, and the setting decides whether the
		// position is drawn, not whether it is known.
		if (!SafariLocation.inSafari()) {
			found = null;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			found = null;
			return;
		}

		for (Entity entity : client.level.entitiesForRendering()) {
			// The label is a player entity rather than the armour stand most critters
			// get, so the name is what identifies it, not the type.
			if (!entity.hasCustomName()) continue;
			if (!NAME.equals(SafariLocation.strip(entity.getCustomName().getString()))) continue;
			found = entity.blockPosition();
			return;
		}
		found = null;
	}

	/** Where it is hiding, or {@code null} if no Hideyho is loaded. */
	public static BlockPos position() {
		return found;
	}
}
