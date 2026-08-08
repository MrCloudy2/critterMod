package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the drops lying on the floor.
 *
 * <p>Hypixel builds one out of three string item displays sitting in a single block, and
 * announces it with a happy-villager particle a block above. That count of three is
 * Skyblocker's test and it is used unchanged here, since it is what actually identifies
 * one — plenty of single item displays are scenery.
 *
 * <p>The one deliberate difference from Skyblocker is the trigger. They listen for the
 * particle packet and then confirm by counting the displays; this sweeps for the
 * displays directly, on the same one-second cycle they re-confirm on. The particle is
 * the announcement, the displays are the thing, and reading the thing needs no mixin.
 *
 * <p>A drop stays listed for a few seconds after it stops being confirmed, so a display
 * that flickers out of the client's view for a moment does not make the mark blink, and
 * one that has been picked up disappears shortly after.
 */
public final class FloorDrops {

	/** Skyblocker's cycle, and fast enough that a picked-up drop clears promptly. */
	private static final int SCAN_INTERVAL_TICKS = 20;
	/** Exactly this many string displays make a drop. Fewer is scenery. */
	private static final int STRING_DISPLAYS = 3;
	/** How long a drop survives without being seen again, as Skyblocker does. */
	private static final long HOLD_MILLIS = 5_000;

	private static final Map<BlockPos, Long> confirmed = new HashMap<>();
	private static int ticks;

	private FloorDrops() {
	}

	public static void tick() {
		if (++ticks < SCAN_INTERVAL_TICKS) return;
		ticks = 0;

		if (ConfigManager.get().display.floorDropStyle() == CritterConfig.MarkStyle.OFF
			|| !SafariLocation.inSafari()) {
			confirmed.clear();
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		Map<BlockPos, Integer> strings = new HashMap<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof Display.ItemDisplay display)) continue;
			if (!isString(display)) continue;
			strings.merge(entity.blockPosition(), 1, Integer::sum);
		}

		long now = System.currentTimeMillis();
		for (Map.Entry<BlockPos, Integer> entry : strings.entrySet()) {
			if (entry.getValue() == STRING_DISPLAYS) confirmed.put(entry.getKey(), now);
		}
		confirmed.values().removeIf(seen -> now - seen > HOLD_MILLIS);
	}

	/** The floor drops currently known. */
	public static List<BlockPos> positions() {
		return new ArrayList<>(confirmed.keySet());
	}

	/** Forgets a drop the player has just interacted with, without waiting for it to expire. */
	public static void onInteract(BlockPos pos) {
		confirmed.remove(pos);
	}

	public static void reset() {
		confirmed.clear();
	}

	private static boolean isString(Display.ItemDisplay display) {
		Display.ItemDisplay.ItemRenderState state = display.itemRenderState();
		if (state == null) return false;
		ItemStack stack = state.itemStack();
		return !stack.isEmpty() && stack.getItem().equals(Items.STRING);
	}
}
