package dev.fantasytruth.mixin;

import dev.fantasytruth.atlas.AtlasPages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class LevelChunkMixin {
    // The production loader uses SRG and the Gradle development run uses Mojang names.
    @Inject(method = {"m_6978_", "setBlockState"}, at = @At("RETURN"), remap = false)
    private void fantasytruth$changed(BlockPos pos, BlockState state, boolean moving,
                                      CallbackInfoReturnable<BlockState> callback) {
        if (callback.getReturnValue() != null && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level) {
            AtlasPages.blockChanged(level, pos, callback.getReturnValue(), state);
        }
    }
}
