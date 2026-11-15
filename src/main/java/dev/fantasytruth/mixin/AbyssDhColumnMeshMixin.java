package dev.fantasytruth.mixin;

import dev.fantasytruth.client.AbyssDistantHorizons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnBox", remap = false)
public abstract class AbyssDhColumnMeshMixin {
    @ModifyVariable(method = "addBoxQuadsToBuilder", at = @At(value = "STORE"), index = 29,
            slice = @Slice(from = @At(value = "INVOKE", remap = false,
                    target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/ILevelWrapper;getMinHeight()I")),
            require = 0, remap = false)
    private static int fantasytruth$keepDarkFaces(int height, @Coerce Object builder,
                                                  @Coerce Object checkout, @Coerce Object level) {
        return AbyssDistantHorizons.caveCullHeight(height, level);
    }
}
