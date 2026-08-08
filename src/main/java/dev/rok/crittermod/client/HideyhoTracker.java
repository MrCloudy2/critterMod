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
 * <p>What the client cannot do is see it before the server has sent it, and Hypixel
 * only sends an entity once you are within its tracking range. So the position is
 * remembered after the entity unloads again: it does not move while hidden, and one
 * pass within range is then enough to keep the mark for the rest of the round. Its own
 * chat lines say when that stops being true — it announces both hiding again and being
 * found — and the mark is dropped on either.
 *
 * <p>Read off the shared sweep in {@link CritterEntities}, which is the same evidence
 * the nearby-spawn counter uses. SkyHanni goes the other way and walks a list of 18
 * known spots, which is why its finder can miss.
 */
public final class HideyhoTracker {

	private static final String NAME = "Hideyho";

	/** Lines that mean the remembered spot is finished with. */
	private static final String[] MOVED_OR_FOUND = {
		"come find me!",
		"No peeking!",
		"you found me!",
		"You found me!",
	};

	private static BlockPos position;
	private static boolean live;

	private HideyhoTracker() {
	}

	public static void tick() {
		BlockPos seen = fromSightings();
		live = seen != null;
		if (seen != null) position = seen;
	}

	/** Where it is, or was last seen. {@code null} when nothing is known this round. */
	public static BlockPos position() {
		return position;
	}

	/** Whether it is loaded right now, as opposed to only remembered. */
	public static boolean live() {
		return live;
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		for (String ending : MOVED_OR_FOUND) {
			if (line.contains(ending)) {
				clear();
				return;
			}
		}
	}

	public static void clear() {
		position = null;
		live = false;
	}

	/** A spot from the last run says nothing about this one. */
	public static void reset() {
		clear();
	}

	private static BlockPos fromSightings() {
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (NAME.equals(sighting.critter().name())) return sighting.body().blockPosition();
		}
		return null;
	}
}
