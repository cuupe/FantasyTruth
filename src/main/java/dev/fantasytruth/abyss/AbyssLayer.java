package dev.fantasytruth.abyss;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public enum AbyssLayer {
    EDGE("edge", 736, 160, 164),
    FOREST("forest", ForestCoordinates.MOUTH_Y, -48, 218),
    FAULT("fault", 832, -368, 126),
    GOBLETS("goblets", 768, -128, 264),
    SEA("sea", 752, 208, 292),
    CAPITAL("capital", 768, -192, 236),
    MAELSTROM("maelstrom", 768, -416, 202);

    public static final int MIN_Y = -512;
    public static final int MAX_Y = 1024;
    public static final int CENTER_X = 8;
    public static final int CENTER_Z = 8;
    public static final int MOUTH_GATE = 112;
    public static final int PASSAGE_RADIUS = 18;
    private static final AbyssLayer[] LAYERS = values();

    public final String id;
    public final int entrance;
    public final int exit;
    public final int width;
    public final ResourceKey<Level> key;

    AbyssLayer(String id, int entrance, int exit, int width) {
        this.id = id;
        this.entrance = entrance;
        this.exit = exit;
        this.width = width;
        this.key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("stratacubes", "abyss/" + id));
    }

    public static AbyssLayer numbered(int number) { return LAYERS[number - 1]; }

    public static AbyssLayer of(ResourceKey<Level> dimension) {
        for (AbyssLayer layer : LAYERS) if (dimension == layer.key) return layer;
        return null;
    }

    public AbyssLayer next() { return this == MAELSTROM ? null : LAYERS[ordinal() + 1]; }
    public AbyssLayer previous() { return this == EDGE ? null : LAYERS[ordinal() - 1]; }

    public static boolean passage(double x, double z, int radius) {
        double dx = x - CENTER_X;
        double dz = z - CENTER_Z;
        return dx * dx + dz * dz <= (double) radius * radius;
    }
}
