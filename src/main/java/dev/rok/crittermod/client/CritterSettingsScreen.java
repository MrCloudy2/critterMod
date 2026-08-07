package dev.rok.crittermod.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Settings screen, opened by {@code /crittermod} or {@code /cm}.
 *
 * <p>Every control writes straight through to {@link CritterConfig} and saves, so
 * there is no apply step and nothing to lose by closing with Escape. Each row carries
 * a one-line explanation, since several options only matter in a party.
 */
public final class CritterSettingsScreen extends Screen {

	private static final int ROW_HEIGHT = 26;
	private static final int PANEL_PADDING = 12;
	private static final int CONTROL_WIDTH = 70;
	private static final int PANEL_WIDTH = 330;

	private static final int HEADING = 0xFFFFAA00;
	private static final int LABEL = 0xFFFFFFFF;
	private static final int DIM = 0xFF888888;

	private static final int PANEL_BACKGROUND = 0xD0101010;
	private static final int PANEL_BORDER = 0x40FFFFFF;

	/** Channel {@code /critters share} and the party notifications post through. */
	private enum ShareChannel {
		PARTY("pc", "Party"),
		ALL("ac", "All chat"),
		NORMAL("", "Normal chat");

		final String command;
		final String label;

		ShareChannel(String command, String label) {
			this.command = command;
			this.label = label;
		}

		static ShareChannel of(String command) {
			String value = command == null ? "" : command.trim();
			for (ShareChannel channel : values()) {
				if (channel.command.equals(value)) return channel;
			}
			return PARTY;
		}
	}

	private final List<Row> rows = new ArrayList<>();
	private int panelLeft;
	private int panelTop;
	private int panelHeight;

	public CritterSettingsScreen() {
		super(Component.literal("Critter Safari Tracker settings"));
	}

	@Override
	protected void init() {
		rows.clear();
		CritterConfig config = CritterConfig.get();

		panelHeight = PANEL_PADDING * 2 + 24 + 8 * ROW_HEIGHT + 28;
		panelLeft = Math.max(4, (width - PANEL_WIDTH) / 2);
		panelTop = Math.max(8, (height - panelHeight) / 2);

		int y = panelTop + PANEL_PADDING + 24;
		int controlX = panelLeft + PANEL_WIDTH - PANEL_PADDING - CONTROL_WIDTH;

		y = toggle(y, controlX, "Progress HUD", "Top-left panel with the run timer and biome bars",
			() -> config.hudEnabled, v -> config.hudEnabled = v);
		y = toggle(y, controlX, "Only in the Safari", "Hide the HUD once you leave",
			() -> config.onlyInSafari, v -> config.onlyInSafari = v);
		y = toggle(y, controlX, "Per-player lines", "Who is covering which biome, under the bars",
			() -> config.showPerPlayer, v -> config.showPerPlayer = v);
		y = toggle(y, controlX, "Missing panel", "Top-right list of what is left in your biome",
			() -> config.showMissing, v -> config.showMissing = v);
		y = toggle(y, controlX, "Encounter alerts", "Banners for Gemzie, Wumpa and Doomspiral",
			() -> config.bossAlerts, v -> config.bossAlerts = v);
		y = toggle(y, controlX, "Announce to party", "Post those stages to chat for your team",
			() -> config.bossPartyNotify, v -> config.bossPartyNotify = v);
		y = toggle(y, controlX, "Biome complete", "Announce \"<Biome> Done!\" when nothing is left",
			() -> config.biomeDoneNotify, v -> config.biomeDoneNotify = v);

		rows.add(new Row(y, "Post to", "Where Share and announcements are sent"));
		addRenderableWidget(CycleButton.builder(
				(ShareChannel channel) -> Component.literal(channel.label),
				ShareChannel.of(config.shareCommand))
			.withValues(ShareChannel.values())
			.displayOnlyValue()
			.create(controlX, y - 6, CONTROL_WIDTH, 20, Component.literal("Post to"),
				(button, value) -> {
					config.shareCommand = value.command;
					config.save();
				}));
		y += ROW_HEIGHT;

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(panelLeft + (PANEL_WIDTH - 100) / 2, panelTop + panelHeight - 26, 100, 20).build());
	}

	private int toggle(int y, int controlX, String label, String help,
					   BooleanSupplier get, Consumer<Boolean> set) {
		rows.add(new Row(y, label, help));
		CritterConfig config = CritterConfig.get();
		addRenderableWidget(CycleButton.onOffBuilder(get.getAsBoolean())
			.displayOnlyValue()
			.create(controlX, y - 6, CONTROL_WIDTH, 20, Component.literal(label),
				(button, value) -> {
					set.accept(value);
					config.save();
				}));
		return y + ROW_HEIGHT;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + panelHeight, PANEL_BACKGROUND);
		graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + 1, PANEL_BORDER);

		Font font = this.font;
		graphics.text(font, Component.literal("Critter Safari Tracker"),
			panelLeft + PANEL_PADDING, panelTop + PANEL_PADDING, HEADING);
		graphics.text(font, Component.literal("changes save immediately"),
			panelLeft + PANEL_PADDING, panelTop + PANEL_PADDING + 11, DIM);

		for (Row row : rows) {
			graphics.text(font, Component.literal(row.label()), panelLeft + PANEL_PADDING, row.y() - 4, LABEL);
			graphics.text(font, Component.literal(row.help()), panelLeft + PANEL_PADDING, row.y() + 6, DIM);
		}

		// Widgets last, so the panel fill cannot cover them.
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		CritterConfig.get().save();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private record Row(int y, String label, String help) {
	}
}
