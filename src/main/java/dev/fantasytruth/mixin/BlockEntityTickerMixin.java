package dev.fantasytruth.mixin;

import dev.fantasytruth.atlas.AtlasPages;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity", remap = false)
abstract class BlockEntityTickerMixin {
    @Shadow(aliases = "f_156428_", remap = false) @Final private BlockEntity blockEntity;

    @Inject(method = {"m_142224_", "tick"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void fantasytruth$skipShadowTick(CallbackInfo callback) {
        if (blockEntity.getLevel() instanceof ServerLevel level && !AtlasPages.ownsBlock(level, blockEntity.getBlockPos())) {
            callback.cancel();
        }
    }
}
