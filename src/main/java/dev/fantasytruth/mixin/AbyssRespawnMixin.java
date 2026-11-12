package dev.fantasytruth.mixin;

import dev.fantasytruth.client.AbyssFrameHold;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class AbyssRespawnMixin {
    @Inject(method = "m_7992_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;m_91156_(Lnet/minecraft/client/multiplayer/ClientLevel;)V"),
            remap = false, require = 0)
    private void fantasytruth$captureSrg(ClientboundRespawnPacket packet, CallbackInfo ci) {
        AbyssFrameHold.capture(packet);
    }

    @Inject(method = "handleRespawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V"),
            remap = false, require = 0)
    private void fantasytruth$captureOfficial(ClientboundRespawnPacket packet, CallbackInfo ci) {
        AbyssFrameHold.capture(packet);
    }
}
