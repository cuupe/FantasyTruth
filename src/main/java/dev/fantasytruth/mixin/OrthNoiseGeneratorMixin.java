package dev.fantasytruth.mixin;

import dev.fantasytruth.abyss.OrthCity;
import dev.fantasytruth.abyss.OrthBiomeSource;
import dev.fantasytruth.abyss.OrthIsland;
import dev.fantasytruth.abyss.OrthTerrain;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class OrthNoiseGeneratorMixin {
    @Inject(method = {"m_213974_", "fillFromNoise"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void fantasytruth$fillOrth(Executor executor, Blender blender, RandomState state,
                                       StructureManager structures, ChunkAccess chunk,
                                       CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (OrthIsland.enabled((NoiseBasedChunkGenerator) (Object) this) && OrthTerrain.supports(chunk)) {
            boolean terraced = OrthIsland.terraced((NoiseBasedChunkGenerator) (Object) this);
            boolean vertical = OrthIsland.vertical((NoiseBasedChunkGenerator) (Object) this);
            cir.setReturnValue(CompletableFuture.supplyAsync(() -> OrthTerrain.fill(chunk, terraced, vertical), Util.backgroundExecutor()));
        }
    }

    @Inject(method = {"m_224261_", "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/blending/Blender;)V"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void fantasytruth$buildOrth(ChunkAccess chunk, WorldGenerationContext context, RandomState state,
                                        StructureManager structures, BiomeManager biomes, Registry<Biome> registry,
                                        Blender blender, CallbackInfo ci) {
        if (OrthIsland.enabled((NoiseBasedChunkGenerator) (Object) this) && OrthTerrain.supports(chunk)) {
            var generator = (NoiseBasedChunkGenerator) (Object) this;
            boolean forested = generator.getBiomeSource() instanceof OrthBiomeSource orth && orth.forested();
            OrthCity.generateSurface(chunk, OrthIsland.terraced(generator), OrthIsland.vertical(generator), false, forested);
            ci.cancel();
        }
    }

    @Inject(method = {"m_213679_", "applyCarvers"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void fantasytruth$preserveOrthCliffs(WorldGenRegion region, long seed, RandomState state, BiomeManager biomes,
                                                  StructureManager structures, ChunkAccess chunk,
                                                  GenerationStep.Carving step, CallbackInfo ci) {
        if (OrthIsland.enabled((NoiseBasedChunkGenerator) (Object) this) && OrthCity.authored(chunk.getPos())) {
            ci.cancel();
        }
    }
}
