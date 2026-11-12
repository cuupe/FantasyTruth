package dev.fantasytruth.atlas;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

record AtlasContent(List<Deposit> deposits, List<Landmark> landmarks, List<FixedSite> fixedSites) {
    static final AtlasContent EMPTY = new AtlasContent(List.of(), List.of(), List.of());
    static final ResourceLocation FILE = new ResourceLocation("stratacubes", "atlas/content.json");
    private static final Codec<Block> BLOCK = BuiltInRegistries.BLOCK.byNameCodec();
    private static final List<Block> HOST = List.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.ANDESITE, Blocks.CALCITE);

    record Deposit(String id, Block block, Optional<Block> belowZeroBlock, long minY, long maxY,
                   int attempts, double chance, int radius, int minimumCover, List<Block> replace) {
        private static final Codec<Deposit> CODEC = RecordCodecBuilder.<Deposit>create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Deposit::id),
                BLOCK.fieldOf("block").forGetter(Deposit::block),
                BLOCK.optionalFieldOf("below_zero_block").forGetter(Deposit::belowZeroBlock),
                Codec.LONG.fieldOf("min_y").forGetter(Deposit::minY),
                Codec.LONG.fieldOf("max_y").forGetter(Deposit::maxY),
                Codec.intRange(0, 32).optionalFieldOf("attempts", 1).forGetter(Deposit::attempts),
                Codec.doubleRange(0, 1).optionalFieldOf("chance", 1.0).forGetter(Deposit::chance),
                Codec.intRange(1, 16).optionalFieldOf("radius", 3).forGetter(Deposit::radius),
                Codec.intRange(0, 128).optionalFieldOf("minimum_cover", 3).forGetter(Deposit::minimumCover),
                BLOCK.listOf().optionalFieldOf("replace", HOST).forGetter(Deposit::replace)
        ).apply(instance, Deposit::new)).flatXmap(Deposit::validate, Deposit::validate);

        private static DataResult<Deposit> validate(Deposit value) {
            if (value.minY < -Altitude.LIMIT || value.maxY > Altitude.LIMIT || value.minY > value.maxY) {
                return DataResult.error(() -> "Invalid global Y range for deposit " + value.id);
            }
            if (value.block.defaultBlockState().hasBlockEntity()
                    || value.belowZeroBlock.orElse(value.block).defaultBlockState().hasBlockEntity()) {
                return DataResult.error(() -> "Deposit " + value.id + " cannot generate block entities");
            }
            return DataResult.success(value);
        }
    }

    enum Kind {
        HOMESTEAD, WATCHTOWER, RUIN, MINE;

        private static final Codec<Kind> CODEC = Codec.STRING.comapFlatMap(name -> {
            for (Kind value : values()) if (value.id().equals(name)) return DataResult.success(value);
            return DataResult.error(() -> "Unknown Atlas landmark type " + name);
        }, Kind::id);

        String id() { return name().toLowerCase(Locale.ROOT); }
    }

    record Landmark(String id, Kind kind, int spacing, double chance, double maxSlope, Block wall,
                    Block floor, Optional<ResourceLocation> lootTable) {
        private static final Codec<Landmark> CODEC = RecordCodecBuilder.<Landmark>create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Landmark::id),
                Kind.CODEC.fieldOf("kind").forGetter(Landmark::kind),
                Codec.intRange(40, 4096).optionalFieldOf("spacing", 0).forGetter(Landmark::spacing),
                Codec.doubleRange(0, 1).optionalFieldOf("chance", 0.65).forGetter(Landmark::chance),
                Codec.doubleRange(0, 5).optionalFieldOf("max_slope", 0.8).forGetter(Landmark::maxSlope),
                BLOCK.optionalFieldOf("wall", Blocks.COBBLESTONE).forGetter(Landmark::wall),
                BLOCK.optionalFieldOf("floor", Blocks.SPRUCE_PLANKS).forGetter(Landmark::floor),
                ResourceLocation.CODEC.optionalFieldOf("loot_table").forGetter(Landmark::lootTable)
        ).apply(instance, Landmark::new)).flatXmap(Landmark::validate, Landmark::validate);

        private static DataResult<Landmark> validate(Landmark value) {
            if (value.wall.defaultBlockState().hasBlockEntity() || value.floor.defaultBlockState().hasBlockEntity()) {
                return DataResult.error(() -> "Landmark materials cannot generate block entities: " + value.id);
            }
            return DataResult.success(value);
        }
    }

    record FixedSite(String landmark, int x, int z, Optional<Long> y) {
        private static final Codec<FixedSite> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("landmark").forGetter(FixedSite::landmark),
                Codec.intRange(-29_999_950, 29_999_950).fieldOf("x").forGetter(FixedSite::x),
                Codec.intRange(-29_999_950, 29_999_950).fieldOf("z").forGetter(FixedSite::z),
                Codec.LONG.optionalFieldOf("y").forGetter(FixedSite::y)
        ).apply(instance, FixedSite::new));
    }

    private static final Codec<AtlasContent> CODEC = RecordCodecBuilder.<AtlasContent>create(instance -> instance.group(
            Deposit.CODEC.listOf().optionalFieldOf("deposits", List.of()).forGetter(AtlasContent::deposits),
            Landmark.CODEC.listOf().optionalFieldOf("landmarks", List.of()).forGetter(AtlasContent::landmarks),
            FixedSite.CODEC.listOf().optionalFieldOf("fixed_sites", List.of()).forGetter(AtlasContent::fixedSites)
    ).apply(instance, AtlasContent::new)).flatXmap(AtlasContent::validate, AtlasContent::validate);

    private static DataResult<AtlasContent> validate(AtlasContent value) {
        var ids = new java.util.HashSet<String>();
        for (Landmark landmark : value.landmarks) {
            if (landmark.id.isBlank() || !ids.add(landmark.id)) {
                return DataResult.error(() -> "Blank or duplicate landmark id " + landmark.id);
            }
        }
        for (FixedSite site : value.fixedSites) {
            if (!ids.contains(site.landmark)) return DataResult.error(() -> "Unknown fixed-site landmark " + site.landmark);
            if (site.y.isPresent() && (site.y.get() < -Altitude.LIMIT || site.y.get() > Altitude.LIMIT)) {
                return DataResult.error(() -> "Fixed-site Y is out of range for " + site.landmark);
            }
        }
        ids.clear();
        for (Deposit deposit : value.deposits) {
            if (deposit.id.isBlank() || !ids.add(deposit.id)) return DataResult.error(() -> "Blank or duplicate deposit id " + deposit.id);
        }
        return DataResult.success(value);
    }

    static AtlasContent read(ResourceManager resources) throws IOException {
        var resource = resources.getResource(FILE);
        if (resource.isPresent()) {
            try (var reader = resource.get().openAsReader()) { return decode(reader); }
        }
        return builtIn();
    }

    static AtlasContent builtIn() throws IOException {
        try (var stream = AtlasContent.class.getResourceAsStream("/data/stratacubes/atlas/content.json")) {
            if (stream == null) throw new IOException("Missing built-in Atlas content");
            return decode(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static AtlasContent decode(java.io.Reader reader) throws IOException {
        try {
            return parse(JsonParser.parseReader(reader));
        } catch (RuntimeException e) {
            throw new IOException("Invalid Atlas content: " + e.getMessage(), e);
        }
    }

    static AtlasContent parse(com.google.gson.JsonElement json) {
        return CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, message -> {});
    }
}
