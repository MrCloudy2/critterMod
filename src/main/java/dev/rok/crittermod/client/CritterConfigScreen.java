package dev.rok.crittermod.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The settings screen, built with Cloth Config so it looks and behaves like any other
 * Fabric mod's config — categories, search, tooltips, per-entry reset arrows and a
 * Save/Cancel footer.
 *
 * <p>Reached from {@code /crittermod}, {@code /cm}, or the Config button next to the
 * mod in Mod Menu.
 */
public final class CritterConfigScreen {

	private CritterConfigScreen() {
	}

	/** Channel that {@code /critters share} and the encounter announcements post through. */
	public enum ShareChannel implements StringRepresentable {
		PARTY("pc", "Party chat"),
		ALL("ac", "All chat"),
		NORMAL("", "Normal chat");

		private final String command;
		private final String label;

		ShareChannel(String command, String label) {
			this.command = command;
			this.label = label;
		}

		public String command() {
			return command;
		}

		public static ShareChannel of(String command) {
			String value = command == null ? "" : command.trim();
			for (ShareChannel channel : values()) {
				if (channel.command.equals(value)) return channel;
			}
			return PARTY;
		}

		@Override
		public String getSerializedName() {
			return label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public static Screen create(Screen parent) {
		CritterConfig config = CritterConfig.get();

		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.literal("Critter Safari Tracker"))
			.setSavingRunnable(config::save);

		ConfigEntryBuilder entry = builder.entryBuilder();

		ConfigCategory display = builder.getOrCreateCategory(Component.literal("Display"));
		display.addEntry(entry.startBooleanToggle(Component.literal("Progress HUD"), config.hudEnabled)
			.setDefaultValue(true)
			.setTooltip(Component.literal("Top-left panel: run timer, party and personal dex, a bar per biome."))
			.setSaveConsumer(value -> config.hudEnabled = value)
			.build());
		display.addEntry(entry.startBooleanToggle(Component.literal("Only in the Safari"), config.onlyInSafari)
			.setDefaultValue(true)
			.setTooltip(Component.literal("Hide the HUD once you leave the Critter Safari."))
			.setSaveConsumer(value -> config.onlyInSafari = value)
			.build());
		display.addEntry(entry.startBooleanToggle(Component.literal("Per-player lines"), config.showPerPlayer)
			.setDefaultValue(true)
			.setTooltip(Component.literal("Show who is covering which biome, under the biome bars."))
			.setSaveConsumer(value -> config.showPerPlayer = value)
			.build());
		display.addEntry(entry.startBooleanToggle(Component.literal("Missing panel"), config.showMissing)
			.setDefaultValue(true)
			.setTooltip(Component.literal("Top-right list of what is still uncaught in the biome you are standing in."))
			.setSaveConsumer(value -> config.showMissing = value)
			.build());
		display.addEntry(entry.startIntSlider(Component.literal("HUD X"), config.hudX, 0, 400)
			.setDefaultValue(4)
			.setTooltip(Component.literal("Distance from the left edge, in pixels."))
			.setSaveConsumer(value -> config.hudX = value)
			.build());
		display.addEntry(entry.startIntSlider(Component.literal("HUD Y"), config.hudY, 0, 400)
			.setDefaultValue(4)
			.setTooltip(Component.literal("Distance from the top edge, in pixels."))
			.setSaveConsumer(value -> config.hudY = value)
			.build());

		ConfigCategory alerts = builder.getOrCreateCategory(Component.literal("Alerts"));
		alerts.addEntry(entry.startBooleanToggle(Component.literal("Encounter alerts"), config.bossAlerts)
			.setDefaultValue(true)
			.setTooltip(
				Component.literal("On-screen banner and sound as each legendary encounter progresses."),
				Component.literal("Gemzie: chamber opens, then 3 catches."),
				Component.literal("Wumpa: footsteps, awoken, cave reopens."),
				Component.literal("Doomspiral: candle ritual, summoned, retreats."))
			.setSaveConsumer(value -> config.bossAlerts = value)
			.build());
		alerts.addEntry(entry.startBooleanToggle(Component.literal("Biome complete"), config.biomeDoneNotify)
			.setDefaultValue(false)
			.setTooltip(Component.literal("Announce \"<Biome> Done!\" once every species there has been caught by someone."))
			.setSaveConsumer(value -> config.biomeDoneNotify = value)
			.build());

		ConfigCategory party = builder.getOrCreateCategory(Component.literal("Party"));
		party.addEntry(entry.startBooleanToggle(Component.literal("Announce to party"), config.bossPartyNotify)
			.setDefaultValue(true)
			.setTooltip(
				Component.literal("Post encounter stages to chat so your team sees them too."),
				Component.literal("Lines are spaced 1.2s apart; Hypixel drops faster bursts."))
			.setSaveConsumer(value -> config.bossPartyNotify = value)
			.build());
		party.addEntry(entry.startSelector(Component.literal("Post to"),
				ShareChannel.values(), ShareChannel.of(config.shareCommand))
			.setDefaultValue(ShareChannel.PARTY)
			.setTooltip(Component.literal("Where /critters share and the announcements are sent."))
			.setSaveConsumer(value -> config.shareCommand = value.command())
			.build());

		return builder.build();
	}
}
