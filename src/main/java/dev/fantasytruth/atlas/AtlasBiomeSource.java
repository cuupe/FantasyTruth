package dev.fantasytruth.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.fantasytruth.terrain.AtlasField;
import dev.fantasytruth.terrain.AtlasProfile;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

public final class AtlasBiomeSource extends BiomeSource {
    private static final Codec<Map<String, Holder<Biome>>> MAPPING = Codec.unboundedMap(Codec.STRING, Biome.CODEC);
    public static final Codec<AtlasBiomeSource> CODEC = MAPPING.fieldOf("regions").codec().flatXmap(
            regions -> {
                for (AtlasField.Biome biome : AtlasField.Biome.values()) {
                    if (!regions.containsKey(key(biome))) {
                        return DataResult.error(() -> "Missing Atlas biome mapping: " + key(biome));
                    }
                }
                return DataResult.success(new AtlasBiomeSource(regions));
            }, source -> DataResult.success(source.regions));

    private final Map<String, Holder<Biome>> regions;
    private volatile AtlasField field = new AtlasField(0, AtlasProfile.DEFAULT);
    private long originY;

    private AtlasBiomeSource(Map<String, Holder<Biome>> regions) {
        this.regions = Map.copyOf(regions);
    }

    AtlasBiomeSource forPage(long originY) {
        AtlasBiomeSource source = new AtlasBiomeSource(regions);
        source.originY = originY;
        return source;
    }

    void bind(AtlasField field) { this.field = field; }

    Holder<Biome> at(AtlasField.Biome biome) { return regions.get(key(biome)); }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() { return regions.values().stream(); }

    @Override
    protected Codec<? extends BiomeSource> codec() { return CODEC; }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        AtlasField terrain = field;
        AtlasField.Column column = terrain.column(QuartPos.toBlock(x), QuartPos.toBlock(z));
        return at(terrain.biomeAt(column, originY + QuartPos.toBlock(y)));
    }

    private static String key(AtlasField.Biome biome) {
        return biome.name().toLowerCase(Locale.ROOT);
    }
}
