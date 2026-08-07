package dev.rok.crittermod.client;

import io.github.notenoughupdates.moulconfig.gui.GuiContext;
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent;
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig;
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Holds the MoulConfig-managed settings instance.
 *
 * <p>MoulConfig owns reading and writing {@code config/crittermod.json}, so nothing
 * here calls save — editing a value in the GUI persists it.
 */
public final class ConfigManager {

	private static ManagedConfig<CritterConfig> managed;

	private ConfigManager() {
	}

	public static ManagedConfig<CritterConfig> managed() {
		if (managed == null) {
			managed = ManagedConfig.create(
				FabricLoader.getInstance().getConfigDir().resolve("crittermod.json").toFile(),
				CritterConfig.class);
		}
		return managed;
	}

	/** The live settings object. Fields may be read directly. */
	public static CritterConfig get() {
		return managed().getInstance();
	}

	/** Persists the current values; only needed after changing a field in code. */
	public static void save() {
		managed().saveToFile();
	}

	/** The settings screen, for {@code /cm} and for Mod Menu's Config button. */
	public static Screen createScreen(Screen parent) {
		// The editor is a GuiElement; GuiContext wants a GuiComponent, and
		// GuiElementComponent is MoulConfig's own adapter between the two.
		GuiContext context = new GuiContext(new GuiElementComponent(managed().getEditor()));
		return new MoulConfigScreenComponent(Component.literal("Critter Safari Tracker"), context, parent);
	}
}
