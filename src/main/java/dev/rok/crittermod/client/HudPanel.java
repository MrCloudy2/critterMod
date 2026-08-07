package dev.rok.crittermod.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A small stacked panel with a measured layout.
 *
 * <p>Minecraft's font is proportional, so padding rows with {@code %-8s} does not
 * line anything up. Columns here are positioned from measured pixel widths instead:
 * labels left, values right-aligned against a common edge, with an optional progress
 * bar between them.
 */
public final class HudPanel {

	private static final int LINE_HEIGHT = 11;
	private static final int PADDING = 5;
	private static final int GUTTER = 6;
	private static final int BAR_WIDTH = 34;
	private static final int BAR_HEIGHT = 4;

	private static final int BACKGROUND = 0xB0000000;
	private static final int BORDER = 0x40FFFFFF;
	private static final int BAR_TRACK = 0x50FFFFFF;

	private final List<Row> rows = new ArrayList<>();

	public HudPanel title(String text, int colour) {
		rows.add(new Row(Kind.TITLE, text, null, colour, 0, 0, 0));
		return this;
	}

	public HudPanel line(String text, int colour) {
		rows.add(new Row(Kind.TEXT, text, null, colour, 0, 0, 0));
		return this;
	}

	public HudPanel pair(String label, String value, int labelColour, int valueColour) {
		rows.add(new Row(Kind.PAIR, label, value, labelColour, valueColour, 0, 0));
		return this;
	}

	/** A label, a filled progress bar, and a right-aligned {@code current/max} value. */
	public HudPanel bar(String label, int current, int max, int labelColour, int barColour) {
		rows.add(new Row(Kind.BAR, label, current + "/" + max, labelColour, barColour, current, max));
		return this;
	}

	public HudPanel blank() {
		rows.add(new Row(Kind.BLANK, "", null, 0, 0, 0, 0));
		return this;
	}

	public boolean isEmpty() {
		return rows.isEmpty();
	}

	/** Panel width in pixels at scale 1, measured from the widest row. */
	public int width(Font font) {
		return contentWidth(font) + PADDING * 2;
	}

	/** Panel height in pixels at scale 1. */
	public int height() {
		return rows.size() * LINE_HEIGHT + PADDING * 2;
	}

	private int contentWidth(Font font) {
		int labelWidth = 0;
		int valueWidth = 0;
		boolean anyBar = false;
		for (Row row : rows) {
			labelWidth = Math.max(labelWidth, font.width(row.label()));
			if (row.value() != null) valueWidth = Math.max(valueWidth, font.width(row.value()));
			if (row.kind() == Kind.BAR) anyBar = true;
		}
		int contentWidth = labelWidth + (valueWidth > 0 ? GUTTER + valueWidth : 0)
			+ (anyBar ? GUTTER + BAR_WIDTH : 0);
		// A title spans the whole panel, so it can widen it on its own.
		for (Row row : rows) {
			if (row.kind() == Kind.TITLE || row.kind() == Kind.TEXT) {
				contentWidth = Math.max(contentWidth, font.width(row.label()));
			}
		}
		return contentWidth;
	}

	/**
	 * Draws the panel with its top-left corner at {@code x, y}, scaled about that
	 * corner. Scaling is applied to the matrix rather than to every coordinate, so
	 * the layout maths stays in unscaled pixels.
	 */
	public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale) {
		if (rows.isEmpty()) return;
		if (scale == 1.0f) {
			draw(graphics, font, x, y);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		draw(graphics, font, Math.round(x / scale), Math.round(y / scale));
		graphics.pose().popMatrix();
	}

	private void draw(GuiGraphicsExtractor graphics, Font font, int left, int y) {
		int labelWidth = 0;
		int valueWidth = 0;
		boolean anyBar = false;
		for (Row row : rows) {
			labelWidth = Math.max(labelWidth, font.width(row.label()));
			if (row.value() != null) valueWidth = Math.max(valueWidth, font.width(row.value()));
			if (row.kind() == Kind.BAR) anyBar = true;
		}

		int panelWidth = contentWidth(font) + PADDING * 2;
		int panelHeight = rows.size() * LINE_HEIGHT + PADDING * 2;

		graphics.fill(left, y, left + panelWidth, y + panelHeight, BACKGROUND);
		graphics.fill(left, y, left + panelWidth, y + 1, BORDER);

		int textLeft = left + PADDING;
		int valueRight = left + panelWidth - PADDING;
		int barLeft = valueRight - valueWidth - GUTTER - BAR_WIDTH;
		int rowY = y + PADDING;

		for (Row row : rows) {
			switch (row.kind()) {
				case BLANK -> {
				}
				case TITLE, TEXT -> graphics.text(font, Component.literal(row.label()),
					textLeft, rowY, row.labelColour());
				case PAIR -> {
					graphics.text(font, Component.literal(row.label()), textLeft, rowY, row.labelColour());
					graphics.text(font, Component.literal(row.value()),
						valueRight - font.width(row.value()), rowY, row.valueColour());
				}
				case BAR -> {
					graphics.text(font, Component.literal(row.label()), textLeft, rowY, row.labelColour());
					int barY = rowY + 2;
					graphics.fill(barLeft, barY, barLeft + BAR_WIDTH, barY + BAR_HEIGHT, BAR_TRACK);
					if (row.max() > 0 && row.current() > 0) {
						int filled = Math.max(1, BAR_WIDTH * row.current() / row.max());
						graphics.fill(barLeft, barY, barLeft + filled, barY + BAR_HEIGHT, row.valueColour());
					}
					graphics.text(font, Component.literal(row.value()),
						valueRight - font.width(row.value()), rowY, row.valueColour());
				}
			}
			rowY += LINE_HEIGHT;
		}
	}

	private enum Kind {TITLE, TEXT, PAIR, BAR, BLANK}

	private record Row(Kind kind, String label, String value,
					   int labelColour, int valueColour, int current, int max) {
	}
}
