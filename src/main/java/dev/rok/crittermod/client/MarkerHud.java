package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws waypoint markers by projecting world positions onto the screen.
 *
 * <p>Minecraft's own waypoint system registers fine but nothing appears, because those
 * are drawn on the locator bar and Hypixel does not show it. Rather than reach for a
 * {@code LevelRenderer} mixin, the marker is worked out here: a position relative to
 * the player's eye, rotated by their look angles and divided by depth, is all a
 * perspective projection is. That runs in the existing HUD element with no injection
 * and nothing to crash on world load.
 *
 * <p>The camera's own position and rotation are not public in 26.1.2, so the player's
 * eye and look angles stand in. They agree in first person, which is what matters.
 */
public final class MarkerHud implements HudElement {

	private static final int LABEL = 0xFFFFFFFF;
	private static final int DIM = 0xFFBBBBBB;
	private static final int MAX_DISTANCE = 200;

	/** One thing worth walking to. */
	public record Marker(BlockPos pos, String label, int colour) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!ConfigManager.get().display.waypoints) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) return;
		if (!AreaDetector.inSafari()) return;

		List<Marker> markers = collect();
		if (markers.isEmpty()) return;

		Vec3 eye = client.player.getEyePosition(deltaTracker.getGameTimeDeltaPartialTick(true));
		double yaw = Math.toRadians(client.player.getYRot());
		double pitch = Math.toRadians(client.player.getXRot());

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		// Vertical field of view, so the focal length is derived from the height.
		double fov = Math.toRadians(client.options.fov().get());
		double focal = (height / 2.0) / Math.tan(fov / 2.0);

		Font font = client.font;
		for (Marker marker : markers) {
			double dx = marker.pos().getX() + 0.5 - eye.x;
			double dy = marker.pos().getY() + 0.5 - eye.y;
			double dz = marker.pos().getZ() + 0.5 - eye.z;

			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (distance > MAX_DISTANCE) continue;

			// Undo the player's yaw, then their pitch, leaving camera space with z
			// pointing where they are looking.
			double sinYaw = Math.sin(-yaw);
			double cosYaw = Math.cos(-yaw);
			double x1 = dx * cosYaw - dz * sinYaw;
			double z1 = dx * sinYaw + dz * cosYaw;

			double sinPitch = Math.sin(-pitch);
			double cosPitch = Math.cos(-pitch);
			double y2 = dy * cosPitch - z1 * sinPitch;
			double z2 = dy * sinPitch + z1 * cosPitch;

			// Behind the viewer: there is no sensible place to put it on screen.
			if (z2 <= 0.1) continue;

			int screenX = (int) Math.round(width / 2.0 + (x1 / z2) * focal);
			int screenY = (int) Math.round(height / 2.0 - (y2 / z2) * focal);
			if (screenX < 0 || screenX > width || screenY < 0 || screenY > height) continue;

			graphics.fill(screenX - 3, screenY - 3, screenX + 3, screenY + 3, marker.colour());
			graphics.fill(screenX - 2, screenY - 2, screenX + 2, screenY + 2, 0xFF000000);

			String text = marker.label();
			graphics.text(font, Component.literal(text),
				screenX - font.width(text) / 2, screenY + 5, LABEL);
			String range = Math.round(distance) + "m";
			graphics.text(font, Component.literal(range),
				screenX - font.width(range) / 2, screenY + 15, DIM);
		}
	}

	/** Everything currently worth marking. */
	static List<Marker> collect() {
		List<Marker> markers = new ArrayList<>();
		CritterConfig config = ConfigManager.get();

		for (WallTracker.Wall wall : WallTracker.walls()) {
			if (wall.state() != WallTracker.State.INTACT) continue;
			markers.add(new Marker(wall.pos(), "Wall", 0xFF000000 | SafariBiome.CAVERN.colour()));
		}

		for (NestTracker.Nest nest : NestTracker.nests()) {
			if (!nest.unpunched()) continue;
			markers.add(new Marker(nest.pos(), "Nest", 0xFF000000 | SafariBiome.FOREST.colour()));
		}

		for (TraderWatch.Trade trade : TraderWatch.found()) {
			TraderWatch.Spot spot = trade.spot();
			if (spot == null) continue;
			markers.add(new Marker(new BlockPos(spot.x(), spot.y(), spot.z()),
				trade.critter().name(), 0xFFFFAA00));
		}

		if (config.advanced.waypointMounds) {
			for (BlockPos pos : MoundSpotter.mounds()) {
				markers.add(new Marker(pos, "Mound", 0xFFCC7744));
			}
		}
		return markers;
	}
}
