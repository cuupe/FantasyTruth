package dev.fantasytruth.mixin;

import dev.fantasytruth.atlas.AtlasChunkGenerator;
import dev.fantasytruth.abyss.AbyssLayer;
import dev.fantasytruth.abyss.AbyssTransit;
import dev.fantasytruth.abyss.OrthCity;
import dev.fantasytruth.abyss.OrthIsland;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftServer.class)
abstract class AtlasServerStartupMixin {
    @Unique private static final int FANTASYTRUTH_RADIUS = 4;

    @ModifyVariable(method = {"m_177896_", "setInitialSpawn"}, at = @At("STORE"), ordinal = 0, remap = false)
    private static ChunkPos fantasytruth$landSpawn(ChunkPos original, ServerLevel level, ServerLevelData data,
                                                  boolean bonusChest, boolean debugWorld) {
        if (level.getChunkSource().getGenerator() instanceof AtlasChunkGenerator atlas) {
            return atlas.initialSpawnChunk(level.getChunkSource().randomState(), original);
        }
        if (OrthIsland.enabled(level.getChunkSource().getGenerator())) {
            int z = OrthCity.spawnZ(OrthIsland.terraced(level.getChunkSource().getGenerator()));
            return new ChunkPos(AbyssLayer.CENTER_X >> 4, (AbyssLayer.CENTER_Z + z) >> 4);
        }
        if (AbyssTransit.expedition(level)) {
            return new ChunkPos((AbyssLayer.CENTER_X + 124) >> 4, AbyssLayer.CENTER_Z >> 4);
        }
        return original;
    }

    @ModifyConstant(method = {"m_130006_", "loadLevel"}, constant = @Constant(intValue = 11), remap = false)
    private int fantasytruth$progressRadius(int vanilla) {
        return fantasytruth$isAtlas() ? FANTASYTRUTH_RADIUS : vanilla;
    }

    @ModifyConstant(method = {"m_129940_", "prepareLevels"}, constant = @Constant(intValue = 11), remap = false)
    private int fantasytruth$ticketRadius(int vanilla) {
        return fantasytruth$isAtlas() ? FANTASYTRUTH_RADIUS : vanilla;
    }

    @ModifyConstant(method = {"m_129940_", "prepareLevels"}, constant = @Constant(intValue = 441), remap = false)
    private int fantasytruth$readyChunks(int vanilla) {
        if (!fantasytruth$isAtlas()) return vanilla;
        int diameter = 2 * FANTASYTRUTH_RADIUS - 1;
        int completed = ((MinecraftServer) (Object) this).overworld().getChunkSource().getTickingGenerated();
        // The vanilla loop uses equality. Accept at least the required number.
        return Math.max(diameter * diameter, completed);
    }

    @Unique
    private boolean fantasytruth$isAtlas() {
        var server = (MinecraftServer) (Object) this;
        LevelStem stem = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD);
        return stem != null && (stem.generator() instanceof AtlasChunkGenerator || stem.type().unwrapKey()
                .map(key -> key.location().equals(AbyssTransit.EXPEDITION)).orElse(false));
    }
}
