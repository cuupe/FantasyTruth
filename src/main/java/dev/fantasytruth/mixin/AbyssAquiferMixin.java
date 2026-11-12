package dev.fantasytruth.mixin;

import dev.fantasytruth.abyss.AbyssLayer;
import dev.fantasytruth.abyss.AbyssMouth;
import dev.fantasytruth.abyss.OrthDensity;
import dev.fantasytruth.abyss.OrthIsland;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class AbyssAquiferMixin {
    @Inject(method = {"m_247703_", "createFluidPicker"}, at = @At("RETURN"), cancellable = true, remap = false)
    private static void fantasytruth$keepDescentOpen(NoiseGeneratorSettings settings,
                                                    CallbackInfoReturnable<Aquifer.FluidPicker> cir) {
        var density = settings.noiseRouter().finalDensity();
        if (density instanceof DensityFunctions.HolderHolder holder) density = holder.function().value();
        var vanilla = cir.getReturnValue();
        var dry = new Aquifer.FluidStatus(Integer.MIN_VALUE, Blocks.AIR.defaultBlockState());
        if (density instanceof OrthDensity orth) {
            cir.setReturnValue((x, y, z) -> OrthIsland.dry(x, y, z, orth.terraced(), orth.vertical()) ? dry : vanilla.computeFluid(x, y, z));
        } else if (density instanceof AbyssMouth mouth && mouth.graded()) {
            cir.setReturnValue((x, y, z) -> AbyssLayer.passage(x, z, 51) ? dry : vanilla.computeFluid(x, y, z));
        }
    }
}
