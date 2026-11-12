package dev.fantasytruth.mixin;

import dev.fantasytruth.atlas.AtlasPages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
abstract class HopperInventoryMixin {
    @Inject(method = {"m_59347_", "getContainerAt(Lnet/minecraft/world/level/Level;DDD)Lnet/minecraft/world/Container;"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void fantasytruth$findOwner(Level level, double x, double y, double z, CallbackInfoReturnable<Container> result) {
        if (!(level instanceof ServerLevel server)) return;
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!AtlasPages.ownsBlock(server, pos)) {
            var target = AtlasPages.neighboringInventory(server, pos);
            result.setReturnValue(target == null ? null : HopperBlockEntity.getContainerAt(target.level(), target.pos()));
        }
    }
}
