package dev.fantasytruth.horizon;

import net.minecraft.core.SectionPos;
import net.minecraftforge.common.ForgeConfigSpec;

public final class HorizonClientConfig {
    public static final int MAX_CHUNKS = SectionPos.blockToSectionCoord(HorizonTile.RANGE);

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue DISTANCE;

    static {
        var builder = new ForgeConfigSpec.Builder();
        DISTANCE = builder.comment("Client horizon radius in chunks. Zero disables distant terrain.")
                .defineInRange("horizonDistance", MAX_CHUNKS, 0, MAX_CHUNKS);
        SPEC = builder.build();
    }

    public static int distanceChunks() {
        return SPEC.isLoaded() ? DISTANCE.get() : MAX_CHUNKS;
    }

    public static int distanceBlocks() {
        return SectionPos.sectionToBlockCoord(distanceChunks());
    }

    public static void setDistanceChunks(int distance) {
        if (!SPEC.isLoaded() || distance == DISTANCE.get()) return;
        DISTANCE.set(distance);
        SPEC.save();
    }

    private HorizonClientConfig() {}
}
