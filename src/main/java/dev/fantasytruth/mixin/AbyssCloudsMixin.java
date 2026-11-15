package dev.fantasytruth.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.fantasytruth.client.AbyssAtmosphere;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class AbyssCloudsMixin {
    @Inject(method = {"m_253054_", "renderClouds"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void fantasytruth$hideCaveCloudPlane(PoseStack pose, Matrix4f projection, float tick,
                                                 double x, double y, double z, CallbackInfo callback) {
        if (AbyssAtmosphere.hidesVanillaClouds(x, y, z)) callback.cancel();
    }
}
