package dev.fantasytruth.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.fantasytruth.terrain.TerrainField;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** RandomState resolves the registered noise before any world sampling takes place. */
public final class TerrainDensity implements DensityFunction {
    public static final KeyDispatchDataCodec<TerrainDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(density -> density.seedNoise),
                    Codec.BOOL.optionalFieldOf("continentalness", false).forGetter(density -> density.continentalness)
            ).apply(instance, TerrainDensity::new)));

    private final NoiseHolder seedNoise;
    private final boolean continentalness;
    private final TerrainField terrain;

    private TerrainDensity(NoiseHolder seedNoise, boolean continentalness) {
        this.seedNoise = seedNoise;
        this.continentalness = continentalness;
        long first = Double.doubleToLongBits(seedNoise.getValue(1173, 0, -448));
        long second = Double.doubleToLongBits(seedNoise.getValue(-687, 129, 901));
        this.terrain = new TerrainField(first ^ Long.rotateLeft(second, 29));
    }

    @Override
    public double compute(FunctionContext context) {
        if (continentalness) {
            return terrain.landmass(context.blockX(), context.blockZ());
        }
        return terrain.height(context.blockX(), context.blockZ());
    }

    @Override
    public void fillArray(double[] values, ContextProvider context) {
        context.fillAllDirectly(values, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        NoiseHolder resolved = visitor.visitNoise(seedNoise);
        return visitor.apply(resolved == seedNoise ? this : new TerrainDensity(resolved, continentalness));
    }

    @Override
    public double minValue() {
        return continentalness ? -1 : TerrainField.MIN_SURFACE;
    }

    @Override
    public double maxValue() {
        return continentalness ? 1 : TerrainField.MAX_SURFACE;
    }

    @Override
    public KeyDispatchDataCodec<TerrainDensity> codec() {
        return CODEC;
    }
}
