package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.session.SafariSession;
import dev.rok.crittermod.session.SessionManager;
import net.minecraft.client.gui.Font;

import java.util.function.Supplier;

/**
 * The movable on-screen boxes, and where each one lives.
 *
 * <p>Positions are stored as fractions of the screen rather than pixels, so a box
 * stays where you put it across resolution and GUI-scale changes. Both the live HUD
 * and {@link HudEditorScreen} go through here, so a box can only ever be drawn where
 * the editor lets you drag it.
 */
public enum HudBox {

	PROGRESS("Progress HUD", CritterHud::buildPanel) {
		@Override
		public float x() {
			return ConfigManager.get().display.progressX;
		}

		@Override
		public float y() {
			return ConfigManager.get().display.progressY;
		}

		@Override
		public float scale() {
			return ConfigManager.get().display.progressScale;
		}

		@Override
		public void setPosition(float x, float y) {
			ConfigManager.get().display.progressX = x;
			ConfigManager.get().display.progressY = y;
		}

		@Override
		public void setScale(float scale) {
			ConfigManager.get().display.progressScale = scale;
		}

		@Override
		public boolean enabled() {
			return ConfigManager.get().display.hudEnabled;
		}
	},

	MISSING("Missing panel", HudBox::missingPanel) {
		@Override
		public float x() {
			return ConfigManager.get().display.missingX;
		}

		@Override
		public float y() {
			return ConfigManager.get().display.missingY;
		}

		@Override
		public float scale() {
			return ConfigManager.get().display.missingScale;
		}

		@Override
		public void setPosition(float x, float y) {
			ConfigManager.get().display.missingX = x;
			ConfigManager.get().display.missingY = y;
		}

		@Override
		public void setScale(float scale) {
			ConfigManager.get().display.missingScale = scale;
		}

		@Override
		public boolean enabled() {
			return ConfigManager.get().display.hudEnabled && ConfigManager.get().display.showMissing;
		}
	};

	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 3.0f;
	/** Slightly under 1 so the boxes stay out of the way at default GUI scale. */
	public static final float DEFAULT_SCALE = 0.8f;

	private final String label;
	private final Supplier<HudPanel> builder;

	HudBox(String label, Supplier<HudPanel> builder) {
		this.label = label;
		this.builder = builder;
	}

	public String label() {
		return label;
	}

	public abstract float x();

	public abstract float y();

	public abstract float scale();

	public abstract void setPosition(float x, float y);

	public abstract void setScale(float scale);

	public abstract boolean enabled();

	/** The panel as it would be drawn right now, or {@code null} if it has no content. */
	public HudPanel panel() {
		return builder.get();
	}

	/**
	 * A stand-in used by the editor when the real panel has nothing to show, so every
	 * box stays draggable outside a run.
	 */
	public HudPanel placeholderPanel() {
		HudPanel panel = new HudPanel();
		panel.title(label, 0xFFFFAA00);
		panel.line("(nothing to show yet)", 0xFF888888);
		return panel;
	}

	public int pixelX(int screenWidth, HudPanel panel, Font font) {
		int max = Math.max(0, screenWidth - Math.round(panel.width(font) * scale()));
		return Math.min(max, Math.round(x() * screenWidth));
	}

	public int pixelY(int screenHeight, HudPanel panel, Font font) {
		int max = Math.max(0, screenHeight - Math.round(panel.height() * scale()));
		return Math.min(max, Math.round(y() * screenHeight));
	}

	private static HudPanel missingPanel() {
		SafariBiome biome = AreaDetector.currentBiome();
		if (biome == null) return null;
		return MissingHud.buildPanel(biome, SessionManager.currentOrLast());
	}

	/** Convenience for the editor, which wants the live session without re-querying. */
	static SafariSession session() {
		return SessionManager.currentOrLast();
	}
}
