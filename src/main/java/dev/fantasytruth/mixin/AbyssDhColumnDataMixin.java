package dev.fantasytruth.mixin;

import dev.fantasytruth.client.AbyssDistantHorizons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer",
        remap = false)
public abstract class AbyssDhColumnDataMixin {
    @ModifyVariable(method = "setRenderColumnView", at = @At("STORE"), name = "caveCullingEnabled",
            require = 0, remap = false)
    private static boolean fantasytruth$keepDarkColumns(boolean enabled, @Coerce Object level) {
        return enabled && !AbyssDistantHorizons.isEdgeWrapper(level);
    }

    @ModifyVariable(method = "transformCompleteFullDataToColumnData", at = @At("STORE"), name = "vertSize",
            require = 0, remap = false)
    private static int fantasytruth$keepMultipleShelves(int size, @Coerce Object level, @Coerce Object source) {
        return AbyssDistantHorizons.verticalBudget(size, level, source);
    }
}
