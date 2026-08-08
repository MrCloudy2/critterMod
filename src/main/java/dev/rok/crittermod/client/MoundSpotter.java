package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the Rockmite mounds, which are interaction entities rather than blocks.
 *
 * <p>A mound reported a 0.70 x 0.50 hitbox with no name, so that shape is what is
 * matched. Interaction entities are used for plenty of other things, hence the size
 * filter and the restriction to the Cavern — this is deliberately narrow until the
 * detections have been checked against real mound positions.
 */
public final class MoundSpotter {

	private static final double WIDTH = 0.70;
	private static final double HEIGHT = 0.50;
	/** Generous enough for variation between mounds, tight enough to exclude other props. */
	private static final double TOLERANCE = 0.15;
	private static final double SCAN_RADIUS = 64.0;

	private MoundSpotter() {
	}

	/** Interaction entities near the player whose hitbox matches a mound. */
	public static List<BlockPos> mounds() {
		Minecraft client = Minecraft.getInstance();
		List<BlockPos> found = new ArrayList<>();
		if (client.level == null || client.player == null) return found;

		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity.getType() != EntityType.INTERACTION) continue;
			if (entity.position().distanceToSqr(client.player.position()) > SCAN_RADIUS * SCAN_RADIUS) continue;

			double w = entity.getBoundingBox().getXsize();
			double h = entity.getBoundingBox().getYsize();
			if (Math.abs(w - WIDTH) > TOLERANCE || Math.abs(h - HEIGHT) > TOLERANCE) continue;
			found.add(entity.blockPosition());
		}
		return found;
	}

	/** Every interaction entity nearby with its size, for checking what is really out there. */
	public static List<String> describeAll() {
		Minecraft client = Minecraft.getInstance();
		List<String> lines = new ArrayList<>();
		if (client.level == null || client.player == null) return lines;

		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity.getType() != EntityType.INTERACTION) continue;
			double distance = Math.sqrt(entity.position().distanceToSqr(client.player.position()));
			if (distance > SCAN_RADIUS) continue;
			BlockPos pos = entity.blockPosition();
			lines.add("  %.2f x %.2f  %d %d %d  %.0fm".formatted(
				entity.getBoundingBox().getXsize(), entity.getBoundingBox().getYsize(),
				pos.getX(), pos.getY(), pos.getZ(), distance));
		}
		return lines;
	}
}
