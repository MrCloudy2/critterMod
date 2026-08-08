package dev.rok.crittermod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;

/**
 * Drops the Warden darkness effect while at the Safari.
 *
 * <p>Everything that renders the darkness — the light texture, the fog — reads the
 * effect off the local player, so taking the instance off the client's copy is enough
 * and no mixin is needed. Nothing is sent to the server; this is the client choosing
 * not to draw something it was told about.
 *
 * <p>Re-applied effects are dropped again on the next tick, so a server that keeps
 * reapplying it costs at most a frame of dimming.
 *
 * <p>Vanilla's own accessibility setting, Darkness Pulsing, scales the dimming down and
 * can be turned to zero, but it does not touch the fog. This does both, which is why it
 * exists here at all.
 */
public final class DarknessFilter {

	private DarknessFilter() {
	}

	public static void tick() {
		if (!ConfigManager.get().display.removeDarkness) return;
		// Kept to the Safari like everything else here: this is a Safari tracker, not a
		// general-purpose visual mod, and elsewhere the effect may be worth seeing.
		if (!SafariLocation.inSafari()) return;

		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !player.hasEffect(MobEffects.DARKNESS)) return;
		player.removeEffect(MobEffects.DARKNESS);
	}
}
