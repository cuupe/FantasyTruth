package dev.fantasytruth.mixin;

import dev.fantasytruth.abyss.OrthCity;
import dev.fantasytruth.abyss.OrthIsland;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class OrthChunkGeneratorMixin {
    @Inject(method = {"m_255037_", "createStructures"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void fantasytruth$preserveOrthStructures(RegistryAccess registry, ChunkGeneratorStructureState state,
                                                     StructureManager structures, ChunkAccess chunk,
                                                     StructureTemplateManager templates, CallbackInfo ci) {
        if (OrthIsland.enabled((ChunkGenerator) (Object) this) && OrthCity.authored(chunk.getPos())) ci.cancel();
    }

    @Inject(method = {"m_213609_", "applyBiomeDecoration"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void fantasytruth$preserveOrthDecoration(WorldGenLevel region, ChunkAccess chunk,
                                                     StructureManager structures, CallbackInfo ci) {
        if (OrthIsland.enabled((ChunkGenerator) (Object) this) && OrthCity.authored(chunk.getPos())) {
            var generator = (ChunkGenerator) (Object) this;
            OrthCity.generateDetails(chunk, OrthIsland.terraced(generator), OrthIsland.vertical(generator));
            ci.cancel();
        }
    }
}
