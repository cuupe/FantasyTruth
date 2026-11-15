package dev.fantasytruth.mixin;

import dev.fantasytruth.abyss.OrthCity;
import dev.fantasytruth.abyss.OrthBiomeSource;
import dev.fantasytruth.abyss.OrthIsland;
import dev.fantasytruth.abyss.OrthTerrain;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.Util;
import dev.fantasytruth.abyss.OrthSeam;
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
            var generator = (NoiseBasedChunkGenerator) (Object) this;
            boolean terraced = OrthIsland.terraced(generator);
            boolean vertical = OrthIsland.vertical(generator);
            boolean abyssRock = generator.getBiomeSource() instanceof OrthBiomeSource orth && orth.abyssRock();
            boolean wallTerraces = generator.getBiomeSource() instanceof OrthBiomeSource orth && orth.wallTerraces();
            boolean wildEdge = generator.getBiomeSource() instanceof OrthBiomeSource orth && orth.wildEdge();
            cir.setReturnValue(CompletableFuture.supplyAsync(() -> OrthTerrain.fill(chunk, terraced, vertical, abyssRock, wallTerraces, wildEdge), Util.backgroundExecutor()));
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
            boolean abyssRock = generator.getBiomeSource() instanceof OrthBiomeSource orth && orth.abyssRock();
            boolean wallTerraces = generator.getBiomeSource() instanceof OrthBiomeSource orth && orth.wallTerraces();
            boolean wildEdge = generator.getBiomeSource() instanceof OrthBiomeSource orth && orth.wildEdge();
            OrthCity.generateSurface(chunk, OrthIsland.terraced(generator), OrthIsland.vertical(generator), forested, abyssRock,
                    wallTerraces, wildEdge);
            ci.cancel();
        }
    }

    @Inject(method = {"m_214194_", "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V"},
            at = @At("RETURN"), remap = false)
    private void fantasytruth$shareEdgeStructures(WorldGenRegion region, StructureManager structures, RandomState state,
                                                   ChunkAccess chunk, CallbackInfo ci) {
        var generator = (NoiseBasedChunkGenerator) (Object) this;
        if (OrthIsland.vertical(generator) && OrthTerrain.supports(chunk)
                && generator.getBiomeSource() instanceof OrthBiomeSource orth && orth.abyssRock() && orth.wallTerraces()) {
            OrthSeam.decorate(region, chunk);
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
