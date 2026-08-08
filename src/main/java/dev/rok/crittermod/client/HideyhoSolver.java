package dev.rok.crittermod.client;

import net.minecraft.core.BlockPos;

/**
 * Finds the hiding Hideyho.
 *
 * <p>Hideyho hides somewhere in the Haunted biome and asks you to come and find it.
 * The thing is that hiding is only a matter of where it stands: the labelled entity
 * stays loaded on the client the whole time, which is why the missing panel keeps
 * reporting "1 near" while nobody can see it. Marking that entity's position is
 * therefore not a lookup table of hiding spots — it is where it actually is, this run.
 *
 * <p>Read off the shared sweep in {@link CritterEntities}, which is the same evidence
 * the nearby-spawn counter uses. SkyHanni goes the other way and walks a list of 18
 * known spots, which is why its finder can miss.
 */
public final class HideyhoSolver {

	private static final String NAME = "Hideyho";

	private HideyhoSolver() {
	}

	/** Where it is hiding, or {@code null} if no Hideyho is loaded. */
	public static BlockPos position() {
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (NAME.equals(sighting.critter().name())) return sighting.body().blockPosition();
		}
		return null;
	}
}
