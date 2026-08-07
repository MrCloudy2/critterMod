package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Drag-to-place editor for the on-screen boxes, reached from the Edit button in the
 * settings.
 *
 * <p>Every box is shown with its real contents where it has any, and a labelled
 * placeholder where it does not, so positions can be set outside a run. Drag to move,
 * scroll over a box to resize it.
 */
public final class HudEditorScreen extends Screen {

	private static final int HINT = 0xFFFFFFFF;
	private static final int DIM = 0xFFAAAAAA;
	private static final int OUTLINE = 0xFFFFAA00;
	private static final int OUTLINE_IDLE = 0x60FFFFFF;
	private static final int BACKDROP = 0x80000000;
	private static final float SCALE_STEP = 0.1f;

	/** Where each box was drawn last frame, so clicks and scrolls can be hit-tested. */
	private final Map<HudBox, Rect> bounds = new EnumMap<>(HudBox.class);

	private HudBox dragging;
	private int grabOffsetX;
	private int grabOffsetY;
	private HudBox hovered;

	public HudEditorScreen() {
		super(Component.literal("Edit HUD positions"));
	}

	/** Opens the editor on the next tick, from wherever the settings screen was. */
	public static void open() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new HudEditorScreen()));
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("Reset positions"), b -> resetAll())
			.bounds(width / 2 - 105, height - 28, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(width / 2 + 5, height - 28, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, BACKDROP);

		Font font = this.font;
		bounds.clear();
		hovered = null;

		for (HudBox box : HudBox.values()) {
			if (!box.enabled()) continue;

			HudPanel panel = box.panel();
			if (panel == null || panel.isEmpty()) panel = box.placeholderPanel();

			float scale = box.scale();
			int x = box.pixelX(width, panel, font);
			int y = box.pixelY(height, panel, font);
			int w = Math.round(panel.width(font) * scale);
			int h = Math.round(panel.height() * scale);

			// Dragging keeps its own box under the cursor rather than snapping to it.
			if (dragging == box) {
				x = clamp(mouseX - grabOffsetX, 0, width - w);
				y = clamp(mouseY - grabOffsetY, 0, height - h);
				box.setPosition(x / (float) width, y / (float) height);
			}

			bounds.put(box, new Rect(x, y, w, h));
			panel.render(graphics, font, x, y, scale);

			boolean over = dragging == box
				|| (dragging == null && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h);
			if (over) hovered = box;
			outline(graphics, x, y, w, h, over ? OUTLINE : OUTLINE_IDLE);

			if (over) {
				String tag = "%s  ·  %.0f%%".formatted(box.label(), scale * 100);
				graphics.text(font, Component.literal(tag), x, Math.max(0, y - 10), OUTLINE);
			}
		}

		String hint = "Drag a box to move it  ·  scroll over a box to resize";
		graphics.text(font, Component.literal(hint), (width - font.width(hint)) / 2, 12, HINT);
		String hint2 = "Boxes with nothing to show appear as placeholders";
		graphics.text(font, Component.literal(hint2), (width - font.width(hint2)) / 2, 24, DIM);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
		graphics.fill(x - 1, y - 1, x + w + 1, y, colour);
		graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, colour);
		graphics.fill(x - 1, y, x, y + h, colour);
		graphics.fill(x + w, y, x + w + 1, y + h, colour);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (super.mouseClicked(event, doubled)) return true;

		int mouseX = (int) event.x();
		int mouseY = (int) event.y();
		for (Map.Entry<HudBox, Rect> hit : bounds.entrySet()) {
			Rect rect = hit.getValue();
			if (mouseX < rect.x() || mouseX >= rect.x() + rect.w()) continue;
			if (mouseY < rect.y() || mouseY >= rect.y() + rect.h()) continue;
			dragging = hit.getKey();
			grabOffsetX = mouseX - rect.x();
			grabOffsetY = mouseY - rect.y();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			ConfigManager.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		HudBox target = hovered;
		if (target == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

		float scale = target.scale() + (float) Math.signum(scrollY) * SCALE_STEP;
		scale = Math.round(scale * 100) / 100f;
		target.setScale(Math.clamp(scale, HudBox.MIN_SCALE, HudBox.MAX_SCALE));
		ConfigManager.save();
		return true;
	}

	private void resetAll() {
		for (HudBox box : HudBox.values()) {
			box.setScale(1.0f);
		}
		HudBox.PROGRESS.setPosition(0.004f, 0.006f);
		HudBox.MISSING.setPosition(0.78f, 0.006f);
		ConfigManager.save();
	}

	@Override
	public void onClose() {
		ConfigManager.save();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private record Rect(int x, int y, int w, int h) {
	}
}
