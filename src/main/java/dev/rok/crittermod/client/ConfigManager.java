package dev.rok.crittermod.client;

import io.github.notenoughupdates.moulconfig.gui.GuiContext;
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent;
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig;
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Holds the MoulConfig-managed settings instance.
 *
 * <p>MoulConfig reads and writes {@code config/crittermod.json} but never saves by
 * itself — {@code saveToFile} is only ever called by whoever owns the config. So the
 * settings screen is watched here and the file written when it closes, and again when
 * the game shuts down. Code that changes a field directly calls {@link #save()}.
 *
 * <p>Persistence is Gson with {@code excludeFieldsWithoutExposeAnnotation}, so a field
 * of {@link CritterConfig} is only remembered if it carries {@code @Expose} — and it is
 * remembered by field name, so renaming one silently resets it.
 */
public final class ConfigManager {

	private static ManagedConfig<CritterConfig> managed;

	/** The settings screen we opened, so closing someone else's does not trigger a save. */
	private static Screen ourScreen;
	private static boolean wasOpen;

	private ConfigManager() {
	}

	/**
	 * Saves when the settings screen closes.
	 *
	 * <p>Polled rather than hooked to the screen: it needs no Fabric screen API and
	 * cannot miss a close that happens by any route.
	 */
	public static void tick() {
		boolean open = ourScreen != null && Minecraft.getInstance().screen == ourScreen;
		if (wasOpen && !open) {
			save();
			ourScreen = null;
		}
		wasOpen = open;
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
		ourScreen = new MoulConfigScreenComponent(
			Component.literal("Critter Safari Tracker"), context, parent);
		return ourScreen;
	}
}
