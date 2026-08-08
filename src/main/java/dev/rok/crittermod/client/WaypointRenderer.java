package dev.rok.crittermod.client;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rok.crittermod.CritterMod;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws a wireframe box, with its name floating above it, at each marked position.
 *
 * <p>No mixin: Fabric API does provide a world render event on 26.1.2, as
 * {@code LevelRenderEvents} under {@code rendering/v1/level}. An earlier search for the
 * old {@code WorldRenderEvents} name found nothing and led to the wrong conclusion that
 * only a {@code LevelRenderer} mixin would do. Skyblocker uses this same event.
 *
 * <p>Every vanilla line type is depth-tested, so a box drawn with one vanishes behind
 * the wall you are trying to find. This registers its own pipeline — the vanilla lines
 * one with the depth test turned off — which is what Skyblocker does for its
 * through-wall renderers too.
 */
public final class WaypointRenderer {

	private static final float MAX_DISTANCE = 200.0f;
	private static final float LINE_WIDTH = 3.0f;
	/** Where the label sits above the top of the box. */
	private static final float LABEL_HEIGHT = 0.4f;
	/** Vanilla's name-tag scale, so labels match the size of mob names. */
	private static final float LABEL_SCALE = 0.025f;

	/**
	 * The lines pipeline with the depth test disabled, so the box shows through terrain.
	 *
	 * <p>Registered so the game precompiles it along with its own; it would be compiled
	 * on first use either way.
	 */
	private static final RenderPipeline LINES_THROUGH_WALLS = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(CritterMod.MOD_ID, "pipeline/lines_through_walls"))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build());

	private static final RenderType LINES = RenderType.create(
		CritterMod.MOD_ID + ":lines_through_walls",
		RenderSetup.builder(LINES_THROUGH_WALLS)
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.createRenderSetup());

	private WaypointRenderer() {
	}

	public static void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(WaypointRenderer::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) return;
		if (!SafariLocation.inSafari()) return;

		List<Markers.Marker> markers = Markers.collect();
		if (markers.isEmpty()) return;

		// The pose is at the camera, so world positions are drawn relative to it.
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		PoseStack poses = context.poseStack();
		MultiBufferSource.BufferSource buffers = context.bufferSource();

		for (Markers.Marker marker : markers) {
			if (tooFar(marker, camera)) continue;

			// A highlight is drawn with the vanilla line type, which is depth-tested, so
			// it only shows where the thing itself would be visible. A waypoint uses the
			// one with the depth test off and shows through the terrain.
			boolean waypoint = marker.style() == Markers.Style.WAYPOINT;
			VertexConsumer lines = buffers.getBuffer(waypoint ? LINES : RenderTypes.LINES);

			AABB box = marker.box();
			poses.pushPose();
			poses.translate(box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z);
			box(poses, lines, (float) box.getXsize(), (float) box.getYsize(),
				(float) box.getZsize(), marker.colour());
			poses.popPose();
		}
		// Flushed here rather than left to the end of the frame, so every box is drawn
		// before the first label and a waypoint reads as one thing.
		buffers.endBatch(LINES);
		buffers.endBatch(RenderTypes.LINES);

		for (Markers.Marker marker : markers) {
			// Only a waypoint is named: a highlight sits on something you can already
			// see, so a label over it is just something else to read.
			if (marker.style() != Markers.Style.WAYPOINT || tooFar(marker, camera)) continue;
			label(poses, buffers, marker, camera,
				marker.box().getCenter().distanceTo(camera));
		}
	}

	private static boolean tooFar(Markers.Marker marker, Vec3 camera) {
		return marker.box().getCenter().distanceToSqr(camera) > MAX_DISTANCE * MAX_DISTANCE;
	}

	/**
	 * Twelve edges of the marked box, drawn from its own corner and slightly outset so
	 * it does not z-fight whatever it is drawn around.
	 */
	private static void box(PoseStack poses, VertexConsumer lines,
							float xSize, float ySize, float zSize, int colour) {
		float o = 0.005f;
		float x0 = -o;
		float y0 = -o;
		float z0 = -o;
		float x1 = xSize + o;
		float y1 = ySize + o;
		float z1 = zSize + o;
		float red = ((colour >> 16) & 0xFF) / 255f;
		float green = ((colour >> 8) & 0xFF) / 255f;
		float blue = (colour & 0xFF) / 255f;

		float[][] edges = {
			{x0, y0, z0, x1, y0, z0}, {x1, y0, z0, x1, y0, z1},
			{x1, y0, z1, x0, y0, z1}, {x0, y0, z1, x0, y0, z0},
			{x0, y1, z0, x1, y1, z0}, {x1, y1, z0, x1, y1, z1},
			{x1, y1, z1, x0, y1, z1}, {x0, y1, z1, x0, y1, z0},
			{x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0},
			{x1, y0, z1, x1, y1, z1}, {x0, y0, z1, x0, y1, z1},
		};
		for (float[] e : edges) {
			line(poses, lines, e[0], e[1], e[2], e[3], e[4], e[5], red, green, blue);
		}
	}

	private static void line(PoseStack poses, VertexConsumer lines,
							 float x1, float y1, float z1, float x2, float y2, float z2,
							 float r, float g, float b) {
		var pose = poses.last();
		// The line format wants a normal along the segment and a width per vertex.
		// Leaving the width off is a hard crash — "Missing elements in vertex" — rather
		// than a default, which is what the first version of this did.
		float nx = x2 - x1;
		float ny = y2 - y1;
		float nz = z2 - z1;
		float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length == 0) return;
		nx /= length;
		ny /= length;
		nz /= length;

		lines.addVertex(pose, x1, y1, z1).setColor(r, g, b, 1.0f)
			.setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
		lines.addVertex(pose, x2, y2, z2).setColor(r, g, b, 1.0f)
			.setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
	}

	/**
	 * The name above the box, turned to face the camera.
	 *
	 * <p>Built the way vanilla builds a mob's name tag: translate to the spot, apply the
	 * camera's rotation so it always faces you, then scale down to text size. Drawn
	 * see-through and full-bright so it reads at any light level and through walls, like
	 * the box under it.
	 */
	private static void label(PoseStack poses, MultiBufferSource buffers,
							  Markers.Marker marker, Vec3 camera, double distance) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		AABB box = marker.box();
		String text = "%s §7%dm".formatted(marker.label(), Math.round(distance));

		poses.pushPose();
		poses.translate(
			box.getCenter().x - camera.x,
			box.maxY + LABEL_HEIGHT - camera.y,
			box.getCenter().z - camera.z);
		poses.mulPose(client.gameRenderer.getMainCamera().rotation());
		poses.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

		Matrix4f pose = new Matrix4f(poses.last().pose());
		float x = -font.width(text) / 2.0f;
		font.drawInBatch(text, x, 0, marker.colour() | 0xFF000000, false, pose, buffers,
			Font.DisplayMode.SEE_THROUGH, 0x40000000, LightCoordsUtil.FULL_BRIGHT);
		poses.popPose();
	}
}
