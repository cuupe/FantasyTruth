package dev.fantasytruth.mixin;

import dev.fantasytruth.client.AbyssAtmosphere;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.ClientLevelData.class)
abstract class AbyssSkyHorizonMixin {
    @Inject(method = {"m_171687_", "getHorizonHeight"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void fantasytruth$hideDarkDisc(LevelHeightAccessor height, CallbackInfoReturnable<Double> result) {
        if (AbyssAtmosphere.replacesHorizon()) result.setReturnValue((double) height.getMinBuildHeight());
    }
}
