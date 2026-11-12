package dev.fantasytruth.abyss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Joins the expedition surface to the first layer in world-space coordinates. */
public record AbyssMouth(DensityFunction terrain, boolean graded) implements DensityFunction {
    public static final KeyDispatchDataCodec<AbyssMouth> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("terrain").forGetter(AbyssMouth::terrain),
                    Codec.BOOL.optionalFieldOf("graded", false).forGetter(AbyssMouth::graded)
            ).apply(instance, AbyssMouth::new)));

    @Override
    public double compute(FunctionContext point) {
        double density = terrain.compute(point);
        int x = point.blockX() - AbyssLayer.CENTER_X;
        int z = point.blockZ() - AbyssLayer.CENTER_Z;
        int y = point.blockY();
        if (y >= 416 || y <= -488) return density;
        if (graded) return AbyssCoast.shape(density, x, y, z);
        if ((double) x * x + (double) z * z >= 224 * 224) return density;
        double radius = Math.hypot(x, z);
        double angle = Math.atan2(z, x);
        double ledge = 8 * Math.sin(5 * angle) + 4 * Math.sin(13 * angle + point.blockY() * 0.034);
        double throat = 65 + 0.19 * Math.max(0, point.blockY() - AbyssLayer.MOUTH_GATE) + ledge;
        double lip = 151 + 11 * Math.sin(3 * angle) + 6 * Math.sin(7 * angle);
        double island = Math.min((lip - point.blockY()) / 22, (199 - radius) / 25);
        double incision = Math.max((radius - throat) / 11, (-460 - point.blockY()) / 12.0);
        return Math.min(Math.max(density, island), incision);
    }

    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) { return visitor.apply(new AbyssMouth(terrain.mapAll(visitor), graded)); }
    @Override public double minValue() { return Math.min(terrain.minValue(), -32); }
    @Override public double maxValue() { return Math.max(terrain.maxValue(), graded ? 16 : 8); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
