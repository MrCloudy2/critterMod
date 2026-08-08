package dev.rok.crittermod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws a wireframe box in the world at each marked position.
 *
 * <p>No mixin: Fabric API does provide a world render event on 26.1.2, as
 * {@code LevelRenderEvents} under {@code rendering/v1/level}. An earlier search for the
 * old {@code WorldRenderEvents} name found nothing and led to the wrong conclusion that
 * only a {@code LevelRenderer} mixin would do. Skyblocker uses this same event.
 *
 * <p>Drawn after translucent terrain with the line render type, which ignores depth, so
 * the boxes show through walls — the point of marking them at all.
 */
public final class WaypointRenderer {

	private static final float MAX_DISTANCE = 200.0f;

	private WaypointRenderer() {
	}

	public static void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(WaypointRenderer::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) return;
		if (!AreaDetector.inSafari()) return;

		List<Markers.Marker> markers = Markers.collect();
		if (markers.isEmpty()) return;

		// The pose is at the camera, so world positions are drawn relative to it.
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		PoseStack poses = context.poseStack();
		VertexConsumer lines = context.bufferSource().getBuffer(RenderTypes.LINES);

		for (Markers.Marker marker : markers) {
			BlockPos pos = marker.pos();
			if (pos.distToCenterSqr(camera) > MAX_DISTANCE * MAX_DISTANCE) continue;

			poses.pushPose();
			poses.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
			box(poses, lines, marker.colour());
			poses.popPose();
		}
	}

	/** Twelve edges of a unit cube, slightly outset so it does not z-fight the block. */
	private static void box(PoseStack poses, VertexConsumer lines, int colour) {
		float a = -0.005f;
		float b = 1.005f;
		float red = ((colour >> 16) & 0xFF) / 255f;
		float green = ((colour >> 8) & 0xFF) / 255f;
		float blue = (colour & 0xFF) / 255f;

		float[][] edges = {
			{a, a, a, b, a, a}, {b, a, a, b, a, b}, {b, a, b, a, a, b}, {a, a, b, a, a, a},
			{a, b, a, b, b, a}, {b, b, a, b, b, b}, {b, b, b, a, b, b}, {a, b, b, a, b, a},
			{a, a, a, a, b, a}, {b, a, a, b, b, a}, {b, a, b, b, b, b}, {a, a, b, a, b, b},
		};
		for (float[] e : edges) {
			line(poses, lines, e[0], e[1], e[2], e[3], e[4], e[5], red, green, blue);
		}
	}

	private static void line(PoseStack poses, VertexConsumer lines,
							 float x1, float y1, float z1, float x2, float y2, float z2,
							 float r, float g, float b) {
		var pose = poses.last();
		// The line render type wants a normal along the segment for its thickness.
		float nx = x2 - x1;
		float ny = y2 - y1;
		float nz = z2 - z1;
		float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length == 0) return;
		nx /= length;
		ny /= length;
		nz /= length;

		lines.addVertex(pose, x1, y1, z1).setColor(r, g, b, 1.0f).setNormal(pose, nx, ny, nz);
		lines.addVertex(pose, x2, y2, z2).setColor(r, g, b, 1.0f).setNormal(pose, nx, ny, nz);
	}
}
