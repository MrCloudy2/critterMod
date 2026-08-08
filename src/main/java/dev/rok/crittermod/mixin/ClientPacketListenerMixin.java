package dev.rok.crittermod.mixin;

import dev.rok.crittermod.client.ParticleLog;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Taps the particles the server sends.
 *
 * <p>The only mixin in the mod, and it exists because there is no other way: particles
 * arrive as packets, vanilla keeps no record of them, and Fabric API has no event for
 * one. Skyblocker hooks the same packet for the same reason.
 *
 * <p>It only reads — the packet is passed on untouched — and it hands straight off to
 * {@link ParticleLog}, which does nothing at all unless the recorder has been switched
 * on by {@code /critters particles watch}.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

	@Inject(method = "handleParticleEvent", at = @At("HEAD"))
	private void crittermod$recordParticle(ClientboundLevelParticlesPacket packet, CallbackInfo info) {
		ParticleLog.onParticle(packet);
	}
}
