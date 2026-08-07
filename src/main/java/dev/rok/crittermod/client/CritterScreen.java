package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.session.MissingReport;
import dev.rok.crittermod.session.SafariSession;
import dev.rok.crittermod.session.SessionManager;
import dev.rok.crittermod.session.TrackingMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * Full-screen view of the current run, opened by {@code /critters} or {@code /ct}.
 *
 * <p>One column per biome listing every species, colour-coded by who has it: green if
 * you caught it, aqua if only a partymate did, grey if nobody has. That makes the two
 * questions the run actually turns on — what is left, and who is covering it —
 * answerable at a glance instead of by reading chat.
 */
public final class CritterScreen extends Screen {

	private static final int PREFERRED_COLUMN_WIDTH = 108;
	private static final int LINE_HEIGHT = 11;
	private static final int PANEL_PADDING = 10;

	private static final int CAUGHT_BY_YOU = 0xFF55FF55;
	private static final int CAUGHT_BY_PARTY = 0xFF55FFFF;
	private static final int UNCAUGHT = 0xFF777777;
	private static final int HEADING = 0xFFFFAA00;
	private static final int LABEL = 0xFFBBBBBB;
	private static final int DIM = 0xFF888888;
	private static final int WHITE = 0xFFFFFFFF;

	private static final int PANEL_BACKGROUND = 0xD0101010;
	private static final int PANEL_BORDER = 0x40FFFFFF;
	private static final int BAR_TRACK = 0x50FFFFFF;

	private final SafariSession session;
	private final boolean live;

	/** Shrinks from the preferred width when the window cannot fit four columns. */
	private int columnWidth;
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelHeight;

	public CritterScreen() {
		super(Component.literal("Critter Safari"));
		this.session = SessionManager.currentOrLast();
		this.live = SessionManager.current() != null;
	}

	@Override
	protected void init() {
		int columns = SafariBiome.values().length;
		int available = width - PANEL_PADDING * 2 - 8;
		columnWidth = Math.max(60, Math.min(PREFERRED_COLUMN_WIDTH, available / columns));
		panelWidth = columnWidth * columns + PANEL_PADDING * 2;
		// Header block, the longest biome column (Haunted has 10), then the player table.
		int playerRows = session == null ? 0 : session.uniquePerPlayer().size();
		panelHeight = 46 + (2 + 10) * LINE_HEIGHT + (playerRows + 2) * LINE_HEIGHT + 40;
		panelLeft = Math.max(4, (width - panelWidth) / 2);
		panelTop = Math.max(10, (height - panelHeight) / 2);

		int buttonY = panelTop + panelHeight - 30;
		int buttonWidth = 80;
		int spacing = 6;
		int totalWidth = buttonWidth * 3 + spacing * 2;
		int buttonX = panelLeft + (panelWidth - totalWidth) / 2;

		addRenderableWidget(Button.builder(Component.literal("Copy missing"), b -> copyMissing())
			.bounds(buttonX, buttonY, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Share"), b -> shareMissing())
			.bounds(buttonX + buttonWidth + spacing, buttonY, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
			.bounds(buttonX + (buttonWidth + spacing) * 2, buttonY, buttonWidth, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL_BACKGROUND);
		graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 1, PANEL_BORDER);

		Font font = this.font;
		int y = panelTop + PANEL_PADDING;

		if (session == null) {
			graphics.text(font, Component.literal("No Critter Safari run tracked yet."),
				panelLeft + PANEL_PADDING, y, LABEL);
			return;
		}

		y = drawHeader(graphics, font, y);
		y = drawBiomeColumns(graphics, font, y + 4);
		drawPlayers(graphics, font, y + 6);
	}

	private int drawHeader(GuiGraphicsExtractor graphics, Font font, int y) {
		int total = Critters.total();
		int left = panelLeft + PANEL_PADDING;

		String heading = live
			? "Critter Safari  " + formatDuration(session.durationMillis())
			: "Critter Safari  (last run)";
		graphics.text(font, Component.literal(heading), left, y, HEADING);

		String legend = "you  ·  party  ·  uncaught";
		graphics.text(font, Component.literal(legend),
			panelLeft + panelWidth - PANEL_PADDING - font.width(legend), y, DIM);
		y += LINE_HEIGHT + 3;

		y = drawSummaryBar(graphics, font, left, y, "Party", session.partyUnique(), total,
			session.dexComplete() ? CAUGHT_BY_YOU : WHITE);
		y = drawSummaryBar(graphics, font, left, y, "You", session.ownUnique(), total, CAUGHT_BY_PARTY);
		return y;
	}

	private int drawSummaryBar(GuiGraphicsExtractor graphics, Font font, int x, int y,
							   String label, int current, int max, int colour) {
		graphics.text(font, Component.literal(label), x, y, LABEL);

		int barLeft = x + 40;
		int barWidth = 150;
		int barY = y + 2;
		graphics.fill(barLeft, barY, barLeft + barWidth, barY + 5, BAR_TRACK);
		if (current > 0) {
			graphics.fill(barLeft, barY, barLeft + Math.max(1, barWidth * current / max), barY + 5, colour);
		}
		graphics.text(font, Component.literal(current + "/" + max), barLeft + barWidth + 8, y, colour);
		return y + LINE_HEIGHT + 2;
	}

	private int drawBiomeColumns(GuiGraphicsExtractor graphics, Font font, int y) {
		int bottom = y;
		SafariBiome[] biomes = SafariBiome.values();

		for (int i = 0; i < biomes.length; i++) {
			SafariBiome biome = biomes[i];
			int x = panelLeft + PANEL_PADDING + i * columnWidth;
			int rowY = y;

			boolean complete = session.biomeComplete(biome);
			int max = Critters.totalIn(biome);
			graphics.text(font, Component.literal(biome.displayName()), x, rowY,
				0xFF000000 | biome.colour());
			String count = session.partyUnique(biome) + "/" + max;
			graphics.text(font, Component.literal(count), x + columnWidth - 14 - font.width(count), rowY,
				complete ? CAUGHT_BY_YOU : WHITE);
			rowY += LINE_HEIGHT + 2;

			for (Critter critter : Critters.inBiome(biome)) {
				// Grey until the run is actually finished with it, so a quota species
				// caught once still reads as outstanding.
				int colour = session.isUnavailable(critter) ? UNCAUGHT
					: !session.isComplete(critter) ? UNCAUGHT
					: session.caughtByYou(critter) ? CAUGHT_BY_YOU : CAUGHT_BY_PARTY;
				graphics.text(font, Component.literal(critter.name()), x, rowY, colour);

				// Quota species show progress towards their total; the rest show repeat
				// catches, or an attempt count meaning it is around and escaping.
				int caught = session.partyCatches(critter);
				int total = session.required(critter);
				boolean known = total > 1 && !TrackingMode.uniqueOnly();
				String note = session.isUnavailable(critter) ? "n/a"
					: known ? caught + "/" + total
					: caught > 1 ? "x" + caught
					: caught == 0 && session.attempts(critter) > 0 ? session.attempts(critter) + "t" : "";
				if (!note.isEmpty()) {
					graphics.text(font, Component.literal(note),
						x + columnWidth - 14 - font.width(note), rowY, DIM);
				}
				rowY += LINE_HEIGHT;
			}
			bottom = Math.max(bottom, rowY);
		}
		return bottom;
	}

	private void drawPlayers(GuiGraphicsExtractor graphics, Font font, int y) {
		Map<String, Map<SafariBiome, Integer>> perPlayer = session.uniquePerPlayer();
		int left = panelLeft + PANEL_PADDING;

		graphics.fill(left, y, panelLeft + panelWidth - PANEL_PADDING, y + 1, PANEL_BORDER);
		y += 5;

		if (perPlayer.isEmpty()) {
			graphics.text(font, Component.literal("Nobody has caught anything yet."), left, y, DIM);
			return;
		}

		// Fixed columns, since the proportional font makes padded text impossible to align.
		int nameWidth = Math.min(96, panelWidth / 3);
		int cellWidth = Math.max(28, (panelWidth - PANEL_PADDING * 2 - nameWidth) / SafariBiome.values().length);
		SafariBiome[] biomes = SafariBiome.values();

		graphics.text(font, Component.literal("Unique per player"), left, y, LABEL);
		for (int i = 0; i < biomes.length; i++) {
			String header = biomes[i].displayName();
			graphics.text(font, Component.literal(header), left + nameWidth + i * cellWidth, y,
				0xFF000000 | biomes[i].colour());
		}
		y += LINE_HEIGHT + 2;

		for (Map.Entry<String, Map<SafariBiome, Integer>> entry : perPlayer.entrySet()) {
			boolean self = entry.getKey().equals(session.selfName());
			graphics.text(font, Component.literal(entry.getKey()), left, y, self ? CAUGHT_BY_YOU : WHITE);
			for (int i = 0; i < biomes.length; i++) {
				int value = entry.getValue().getOrDefault(biomes[i], 0);
				graphics.text(font, Component.literal(String.valueOf(value)),
					left + nameWidth + i * cellWidth, y, value == 0 ? UNCAUGHT : LABEL);
			}
			y += LINE_HEIGHT;
		}
	}

	private void copyMissing() {
		if (session == null) return;
		Minecraft.getInstance().keyboardHandler.setClipboard(MissingReport.text(session));
		feedback("Copied the missing list to the clipboard.");
	}

	private void shareMissing() {
		if (session == null) return;
		List<String> lines = MissingReport.lines(session);
		if (lines.isEmpty()) lines = List.of(MissingReport.text(session));

		String channel = ConfigManager.get().party.shareCommand();
		boolean asCommand = channel != null && !channel.isBlank();
		for (String line : lines) {
			ChatQueue.enqueue(asCommand ? channel.trim() + " " + line : line, asCommand);
		}
		onClose();
	}

	private void feedback(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui == null) return;
		client.gui.getChat().addClientSystemMessage(
			Component.literal("[Critters] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(text).withStyle(ChatFormatting.YELLOW)));
	}

	private static String formatDuration(long millis) {
		long seconds = millis / 1000;
		return "%d:%02d".formatted(seconds / 60, seconds % 60);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
