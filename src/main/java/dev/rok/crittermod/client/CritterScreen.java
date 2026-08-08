package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.session.MissingReport;
import dev.rok.crittermod.session.RunHistory;
import dev.rok.crittermod.session.RunRecord;
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
	/** As many past runs as fit without the panel needing to scroll. */
	private static final int HISTORY_ROWS = 12;

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM  HH:mm");

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

	/** Which view is showing. Static so it survives closing and reopening the screen. */
	private static Tab tab = Tab.RUN;

	/** The three things there are to look at. */
	private enum Tab {
		/** This run, or the last one if there is none. */
		RUN("Run"),
		/** Every saved run, newest first. */
		HISTORY("History"),
		/** Totals per species across every saved run. */
		STATS("Stats");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

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
		panelHeight = switch (tab) {
			// Header block, the longest biome column (Haunted has 10), then the player table.
			case RUN -> 46 + (2 + 10) * LINE_HEIGHT
				+ ((session == null ? 0 : session.uniquePerPlayer().size()) + 2) * LINE_HEIGHT + 40;
			// A summary block, then one line per run shown.
			case HISTORY -> 46 + (HISTORY_ROWS + 2) * LINE_HEIGHT + 46;
			// A summary block, then the species columns.
			case STATS -> 46 + (4 + 10) * LINE_HEIGHT + 46;
		};
		panelLeft = Math.max(4, (width - panelWidth) / 2);
		panelTop = Math.max(10, (height - panelHeight) / 2);

		addTabButtons();

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

	/** A row of buttons above the panel; the one showing is disabled to mark it. */
	private void addTabButtons() {
		Tab[] tabs = Tab.values();
		int tabWidth = 60;
		int spacing = 4;
		int totalWidth = tabWidth * tabs.length + spacing * (tabs.length - 1);
		int x = panelLeft + (panelWidth - totalWidth) / 2;
		int y = Math.max(2, panelTop - 24);

		for (Tab value : tabs) {
			Button button = Button.builder(Component.literal(value.label), b -> switchTo(value))
				.bounds(x, y, tabWidth, 20).build();
			button.active = value != tab;
			addRenderableWidget(button);
			x += tabWidth + spacing;
		}
	}

	private void switchTo(Tab value) {
		tab = value;
		// The panel is a different height per tab, so everything is laid out again.
		rebuildWidgets();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL_BACKGROUND);
		graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 1, PANEL_BORDER);

		Font font = this.font;
		int y = panelTop + PANEL_PADDING;

		switch (tab) {
			case RUN -> {
				if (session == null) {
					graphics.text(font, Component.literal("No Critter Safari run tracked yet."),
						panelLeft + PANEL_PADDING, y, LABEL);
					return;
				}
				y = drawHeader(graphics, font, y);
				y = drawBiomeColumns(graphics, font, y + 4);
				drawPlayers(graphics, font, y + 6);
			}
			case HISTORY -> drawHistory(graphics, font, y);
			case STATS -> drawStats(graphics, font, y);
		}
	}

	private int drawHeader(GuiGraphicsExtractor graphics, Font font, int y) {
		int total = Critters.total();
		int left = panelLeft + PANEL_PADDING;

		String heading = live
			? "Critter Safari  " + formatDuration(session.elapsedMillis(System.currentTimeMillis()))
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

	/**
	 * Every saved run, newest first.
	 *
	 * <p>Runs are written when the next one starts, so the one you are in is not here
	 * yet — that is what the Run tab is for.
	 */
	private void drawHistory(GuiGraphicsExtractor graphics, Font font, int y) {
		int left = panelLeft + PANEL_PADDING;
		int right = panelLeft + panelWidth - PANEL_PADDING;
		List<RunRecord> runs = RunHistory.runs();

		graphics.text(font, Component.literal("Saved runs"), left, y, HEADING);
		String count = runs.size() + " kept";
		graphics.text(font, Component.literal(count), right - font.width(count), y, DIM);
		y += LINE_HEIGHT + 3;

		if (runs.isEmpty()) {
			graphics.text(font, Component.literal("No runs saved yet."), left, y, LABEL);
			y += LINE_HEIGHT;
			graphics.text(font, Component.literal("A run is saved when the next one starts."),
				left, y, DIM);
			y += LINE_HEIGHT;
			graphics.text(font, Component.literal("/critters import reads past runs out of your logs."),
				left, y, DIM);
			return;
		}

		graphics.text(font, Component.literal("%d run%s  ·  %s played  ·  %d caught  ·  best %d/%d".formatted(
			runs.size(), runs.size() == 1 ? "" : "s", formatHours(RunHistory.totalTimeMillis()),
			RunHistory.totalCatches(), RunHistory.bestDex(), Critters.total())), left, y, LABEL);
		y += LINE_HEIGHT + 5;

		// Fixed columns: the proportional font makes padded text impossible to align.
		int timeWidth = Math.min(120, panelWidth / 3);
		int cell = Math.max(46, (panelWidth - PANEL_PADDING * 2 - timeWidth) / 4);
		graphics.text(font, Component.literal("When"), left, y, LABEL);
		graphics.text(font, Component.literal("Length"), left + timeWidth, y, LABEL);
		graphics.text(font, Component.literal("Party"), left + timeWidth + cell, y, LABEL);
		graphics.text(font, Component.literal("You"), left + timeWidth + cell * 2, y, LABEL);
		graphics.text(font, Component.literal("Caught"), left + timeWidth + cell * 3, y, LABEL);
		y += LINE_HEIGHT + 2;

		int total = Critters.total();
		for (int i = runs.size() - 1, shown = 0; i >= 0 && shown < HISTORY_ROWS; i--, shown++) {
			RunRecord run = runs.get(i);
			boolean perfect = run.partyUnique() == total;
			graphics.text(font, Component.literal(formatWhen(run.started)), left, y, WHITE);
			graphics.text(font, Component.literal(formatDuration(run.durationMillis())),
				left + timeWidth, y, DIM);
			graphics.text(font, Component.literal(run.partyUnique() + "/" + total),
				left + timeWidth + cell, y, perfect ? CAUGHT_BY_YOU : WHITE);
			graphics.text(font, Component.literal(run.ownUnique() + "/" + total),
				left + timeWidth + cell * 2, y, CAUGHT_BY_PARTY);
			graphics.text(font, Component.literal(String.valueOf(run.partyTotal())),
				left + timeWidth + cell * 3, y, DIM);
			y += LINE_HEIGHT;
		}
	}

	/**
	 * What every species has been worth across the saved runs.
	 *
	 * <p>Two numbers per species, because they answer different questions: the total
	 * says how much of it you have caught, and how many runs it turned up in says how
	 * reliably it appears at all. A species with a big total from few runs comes in
	 * numbers when it comes.
	 */
	private void drawStats(GuiGraphicsExtractor graphics, Font font, int y) {
		int left = panelLeft + PANEL_PADDING;
		int right = panelLeft + panelWidth - PANEL_PADDING;
		int runs = RunHistory.size();

		graphics.text(font, Component.literal("Across saved runs"), left, y, HEADING);
		String legend = "total  ·  runs seen in";
		graphics.text(font, Component.literal(legend), right - font.width(legend), y, DIM);
		y += LINE_HEIGHT + 3;

		if (runs == 0) {
			graphics.text(font, Component.literal("Nothing saved yet."), left, y, LABEL);
			return;
		}

		int never = RunHistory.neverCaught().size();
		graphics.text(font, Component.literal("%d runs  ·  %s played  ·  %d catches (%d yours)".formatted(
			runs, formatHours(RunHistory.totalTimeMillis()),
			RunHistory.totalCatches(), RunHistory.ownCatches())), left, y, LABEL);
		y += LINE_HEIGHT;
		graphics.text(font, Component.literal("%d shards  ·  %.1f catches a run  ·  %d full dex  ·  %d never caught"
			.formatted(RunHistory.totalShards(), (double) RunHistory.totalCatches() / runs,
				RunHistory.perfectRuns(), never)), left, y, LABEL);
		y += LINE_HEIGHT + 5;

		SafariBiome[] biomes = SafariBiome.values();
		for (int i = 0; i < biomes.length; i++) {
			SafariBiome biome = biomes[i];
			int x = panelLeft + PANEL_PADDING + i * columnWidth;
			int rowY = y;

			graphics.text(font, Component.literal(biome.displayName()), x, rowY,
				0xFF000000 | biome.colour());
			rowY += LINE_HEIGHT + 2;

			for (Critter critter : Critters.inBiome(biome)) {
				RunHistory.SpeciesStat stat = RunHistory.statFor(critter);
				graphics.text(font, Component.literal(critter.name()), x, rowY,
					stat.total() == 0 ? UNCAUGHT : WHITE);
				String note = stat.total() == 0 ? "—"
					: "%d/%d".formatted(stat.total(), stat.runsSeen());
				graphics.text(font, Component.literal(note),
					x + columnWidth - 14 - font.width(note), rowY,
					stat.total() == 0 ? UNCAUGHT : DIM);
				rowY += LINE_HEIGHT;
			}
		}
	}

	/** Date and time of day, which is how you recognise a run you remember. */
	private static String formatWhen(long millis) {
		return WHEN.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
	}

	/** Hours and minutes, for spans far longer than one run. */
	private static String formatHours(long millis) {
		long minutes = millis / 60_000;
		return minutes < 60 ? minutes + "m" : "%dh %02dm".formatted(minutes / 60, minutes % 60);
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
