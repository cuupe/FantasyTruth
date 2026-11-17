package dev.fantasytruth;

import com.mojang.serialization.Codec;
import dev.fantasytruth.abyss.AbyssChunkGenerator;
import dev.fantasytruth.abyss.AbyssEdgeGenerator;
import dev.fantasytruth.abyss.AbyssItems;
import dev.fantasytruth.abyss.ForestExpedition;
import dev.fantasytruth.abyss.AbyssEntities;
import dev.fantasytruth.abyss.AbyssMouth;
import dev.fantasytruth.abyss.AbyssTransit;
import dev.fantasytruth.abyss.NaturalAbyssStructure;
import dev.fantasytruth.abyss.OrthBiomeSource;
import dev.fantasytruth.abyss.OrthBlocks;
import dev.fantasytruth.abyss.OrthDensity;
import dev.fantasytruth.abyss.OrthResidents;
import dev.fantasytruth.atlas.AtlasBiomeSource;
import dev.fantasytruth.atlas.AtlasChunkGenerator;
import dev.fantasytruth.atlas.AtlasPages;
import dev.fantasytruth.worldgen.TerrainDensity;
import dev.fantasytruth.horizon.HorizonClientConfig;
import dev.fantasytruth.horizon.HorizonNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

@Mod(FantasyTruth.ID)
public final class FantasyTruth {
    public static final String ID = "stratacubes";

    public FantasyTruth() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HorizonClientConfig.SPEC);
        HorizonNetwork.register();
        DeferredRegister<Codec<? extends DensityFunction>> types =
                DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, ID);
        types.register("relief", TerrainDensity.CODEC::codec);
        types.register("abyss_mouth", AbyssMouth.CODEC::codec);
        types.register("orth", OrthDensity.CODEC::codec);
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        types.register(bus);
        NaturalAbyssStructure.register(bus);
        OrthBlocks.register(bus);
        AbyssItems.register(bus);
        AbyssEntities.register(bus);
        DeferredRegister<Codec<? extends BiomeSource>> biomes = DeferredRegister.create(Registries.BIOME_SOURCE, ID);
        biomes.register("atlas", () -> AtlasBiomeSource.CODEC);
        biomes.register("orth", () -> OrthBiomeSource.CODEC);
        biomes.register(bus);
        DeferredRegister<Codec<? extends ChunkGenerator>> generators = DeferredRegister.create(Registries.CHUNK_GENERATOR, ID);
        generators.register("atlas", () -> AtlasChunkGenerator.CODEC);
        generators.register("abyss", () -> AbyssChunkGenerator.CODEC);
        generators.register("abyss_edge", () -> AbyssEdgeGenerator.CODEC);
        generators.register(bus);
        MinecraftForge.EVENT_BUS.register(AtlasPages.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new AbyssTransit());
        MinecraftForge.EVENT_BUS.register(ForestExpedition.class);
        MinecraftForge.EVENT_BUS.register(OrthResidents.class);
    }
}
