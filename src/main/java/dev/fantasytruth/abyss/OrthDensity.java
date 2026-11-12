package dev.fantasytruth.abyss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public record OrthDensity(DensityFunction terrain, boolean terraced, boolean vertical) implements DensityFunction {
    public static final KeyDispatchDataCodec<OrthDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("terrain").forGetter(OrthDensity::terrain),
                    Codec.BOOL.optionalFieldOf("terraced", false).forGetter(OrthDensity::terraced),
                    Codec.BOOL.optionalFieldOf("vertical", false).forGetter(OrthDensity::vertical)
            ).apply(instance, OrthDensity::new)));

    @Override
    public double compute(FunctionContext point) {
        return OrthIsland.density(terrain, point, terraced, vertical);
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new OrthDensity(terrain.mapAll(visitor), terraced, vertical)); }
    @Override public double minValue() { return Math.min(terrain.minValue(), -128); }
    @Override public double maxValue() { return Math.max(terrain.maxValue(), 128); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
