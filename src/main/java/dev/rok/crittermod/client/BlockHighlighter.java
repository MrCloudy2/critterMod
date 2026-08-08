package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights marked positions with a glowing block, visible through walls.
 *
 * <p>Builds on the one thing already proven to work here: an entity with shared flag 6
 * gets a vanilla outline drawn through terrain, which is what made the Bloodbats
 * visible. A block display is spawned client-side at each marked position, given a
 * block to show and that flag, so the outline lands on the spot itself rather than
 * being reconstructed on the HUD.
 *
 * <p>The display copies whatever block already stands at the position, so with the glow
 * suppressed by a wall in front of it nothing looks different — a lit block sitting in
 * the open was far too loud. Only the outline distinguishes it.
 *
 * <p>These entities exist only on this client. Their ids are large and negative so they
 * cannot collide with the ids the server hands out.
 */
public final class BlockHighlighter {

	private static final int REFRESH_INTERVAL_TICKS = 20;
	/** Server ids count up from zero, so nothing will ever be allocated down here. */
	private static final int ID_BASE = -900_000;

	private static final List<Entity> spawned = new ArrayList<>();
	private static int ticks;
	private static int nextId = ID_BASE;

	private BlockHighlighter() {
	}

	public static void tick() {
		if (++ticks < REFRESH_INTERVAL_TICKS) return;
		ticks = 0;

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		if (!AreaDetector.inSafari()) {
			clear();
			return;
		}

		// Rebuilt wholesale each pass: a wall broken or a nest punched since last time
		// simply stops being in the list, with no bookkeeping to get out of step.
		clear();
		for (Markers.Marker marker : Markers.collect()) {
			spawn(marker.pos());
		}
	}

	private static void spawn(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		// Copy the block that is actually there. Air positions — a trade spot in the
		// open — get glass, which is the least obtrusive thing that still has a shape
		// for the outline to trace.
		BlockState existing = client.level.getBlockState(pos);
		BlockState shown = existing.isAir() ? Blocks.GLASS.defaultBlockState() : existing;

		Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, client.level);
		display.setBlockState(shown);
		display.setId(nextId--);
		display.setPos(pos.getX(), pos.getY(), pos.getZ());
		display.setSharedFlag(6, true);
		client.level.addEntity(display);
		spawned.add(display);
	}

	/** Removes every marker entity this mod created. */
	public static void clear() {
		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			for (Entity entity : spawned) {
				client.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		spawned.clear();
		nextId = ID_BASE;
	}
}
