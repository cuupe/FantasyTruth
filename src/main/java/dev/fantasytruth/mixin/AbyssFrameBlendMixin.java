package dev.fantasytruth.mixin;

import dev.fantasytruth.client.AbyssFrameHold;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class AbyssFrameBlendMixin {
    @Inject(method = {"m_109093_(FJZ)V", "render(FJZ)V"}, at = @At("RETURN"), remap = false)
    private void fantasytruth$blendAbyssFrame(float tickDelta, long frameStart, boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) AbyssFrameHold.fade();
    }
}
