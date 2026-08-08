package dev.rok.crittermod.client;

import io.github.notenoughupdates.moulconfig.ChromaColour;

/**
 * Turns the colours picked in the settings into something to draw with.
 *
 * <p>MoulConfig's colour picker stores {@code speed:alpha:r:g:b} as a string, which is
 * what lets it offer alpha and the cycling chroma effect. Reading it back per frame
 * keeps a chroma colour actually cycling.
 */
public final class Colours {

	private Colours() {
	}

	/**
	 * The packed ARGB for a stored colour, or {@code fallback} if it cannot be read.
	 *
	 * <p>A hand-edited config file can hold anything, and a colour that fails to parse
	 * should cost the mark its colour, not the frame.
	 */
	public static int argb(String stored, int fallback) {
		if (stored == null || stored.isBlank()) return fallback;
		try {
			int argb = ChromaColour.Companion.specialToChromaRGB(stored);
			// A colour with no alpha would draw nothing at all, which reads as the
			// feature being broken rather than as a deliberate choice.
			return (argb >>> 24) == 0 ? 0xFF000000 | argb : argb;
		} catch (RuntimeException malformed) {
			return fallback;
		}
	}
}
