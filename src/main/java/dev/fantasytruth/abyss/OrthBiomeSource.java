package dev.fantasytruth.abyss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.core.QuartPos;

public final class OrthBiomeSource extends BiomeSource {
    public static final Codec<OrthBiomeSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("surroundings").forGetter(source -> source.surroundings),
            Biome.CODEC.fieldOf("coast").forGetter(source -> source.coast),
            Biome.CODEC.fieldOf("ocean").forGetter(source -> source.ocean),
            Codec.BOOL.optionalFieldOf("terraced", false).forGetter(source -> source.terraced),
            Codec.BOOL.optionalFieldOf("forested", false).forGetter(OrthBiomeSource::forested)
    ).apply(instance, OrthBiomeSource::new));

    private final BiomeSource surroundings;
    private final Holder<Biome> coast;
    private final Holder<Biome> ocean;

    private final boolean terraced;
    private final boolean forested;

    private OrthBiomeSource(BiomeSource surroundings, Holder<Biome> coast, Holder<Biome> ocean, boolean terraced, boolean forested) {
        this.surroundings = surroundings;
        this.coast = coast;
        this.ocean = ocean;
        this.terraced = terraced;
        this.forested = forested;
    }

    public boolean forested() { return forested; }

    @Override protected Codec<? extends BiomeSource> codec() { return CODEC; }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(Stream.of(coast, ocean), surroundings.possibleBiomes().stream());
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int localX = OrthIsland.localX(QuartPos.toBlock(x));
        int localZ = OrthIsland.localZ(QuartPos.toBlock(z));
        if (!OrthIsland.authored(localX, localZ)) return surroundings.getNoiseBiome(x, y, z, sampler);
        return OrthIsland.elevation(localX, localZ, terraced) < OrthIsland.SEA ? ocean : coast;
    }
}
