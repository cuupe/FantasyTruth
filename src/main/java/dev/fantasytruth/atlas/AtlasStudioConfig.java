package dev.fantasytruth.atlas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.fantasytruth.terrain.AtlasProfile;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraftforge.common.ForgeHooks;

/** The common document used by the form, JSON editor and data-pack writer. */
public record AtlasStudioConfig(JsonObject document, AtlasProfile profile) {
    private static final String PRESET = "/data/stratacubes/worldgen/world_preset/atlas.json";
    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Set<String> SECTIONS = Set.of("format", "terrain", "biomes", "content", "loot_tables");
    private static final int MAX_CHARS = 1_048_576;

    public record Parameter(String key, double min, double max, Function<AtlasProfile, Number> value) {
        public String range() { return readable(min) + " … " + readable(max); }
    }

    public static final List<Parameter> PARAMETERS = List.of(
            new Parameter("sea_level", -40000, 40000, AtlasProfile::seaLevel),
            new Parameter("horizontal_scale", 0.25, 16, AtlasProfile::horizontalScale),
            new Parameter("relief", 0, 20, AtlasProfile::relief),
            new Parameter("shape.summit_limit", 0, 4000, profile -> profile.shape().summitLimit()),
            new Parameter("shape.land_bias", -0.35, 0.65, profile -> profile.shape().landBias()),
            new Parameter("shape.mountain_frequency", 0, 1, profile -> profile.shape().mountainFrequency()),
            new Parameter("foothill_detail", 0, 8, AtlasProfile::foothillDetail),
            new Parameter("rock_detail", 0, 8, AtlasProfile::rockDetail),
            new Parameter("ravine_depth", 0, 240, AtlasProfile::ravineDepth),
            new Parameter("ravine_width", 0.025, 0.9, AtlasProfile::ravineWidth),
            new Parameter("runoff_strength", 0, 2, AtlasProfile::runoffStrength),
            new Parameter("runoff_spacing", 16, 128, AtlasProfile::runoffSpacing),
            new Parameter("runoff_steps", 4, 32, AtlasProfile::runoffSteps),
            new Parameter("caves", 0, 2, AtlasProfile::caves),
            new Parameter("temperature_offset", -2, 2, AtlasProfile::temperatureOffset),
            new Parameter("moisture_offset", -2, 2, AtlasProfile::moistureOffset),
            new Parameter("tree_density", 0, 1, AtlasProfile::treeDensity),
            new Parameter("settlement_spacing", 64, 4096, AtlasProfile::settlementSpacing),
            new Parameter("settlement_density", 0, 1, AtlasProfile::settlementDensity));

    public static JsonObject defaults() {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        JsonObject generator = presetGenerator(resource(PRESET));
        AtlasProfile profile = parseProfile(generator.getAsJsonObject("profile"));
        root.add("terrain", terrain(profile));
        root.add("biomes", generator.getAsJsonObject("biome_source").getAsJsonObject("regions"));
        root.add("content", resource("/data/stratacubes/atlas/content.json"));
        JsonObject loot = new JsonObject();
        for (String id : List.of("homestead", "watchtower", "ruin", "mine")) {
            loot.add("stratacubes:chests/" + id, resource("/data/stratacubes/loot_tables/chests/" + id + ".json"));
        }
        root.add("loot_tables", loot);
        return root;
    }

    public static JsonObject fromGenerator(AtlasChunkGenerator atlas, RegistryAccess registries) {
        JsonObject root = defaults();
        JsonObject generator = AtlasChunkGenerator.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), atlas)
                .getOrThrow(false, message -> {}).getAsJsonObject();
        root.add("terrain", terrain(atlas.profile()));
        root.add("biomes", generator.getAsJsonObject("biome_source").getAsJsonObject("regions"));
        return root;
    }

    public static JsonObject parseText(String text) {
        if (text.length() > MAX_CHARS) throw new IllegalArgumentException("JSON exceeds 1 Mi characters");
        return JsonParser.parseString(text).getAsJsonObject();
    }

    public static AtlasStudioConfig validate(JsonObject input) {
        JsonObject result = input.deepCopy();
        for (String key : result.keySet()) {
            if (!SECTIONS.contains(key)) throw new IllegalArgumentException("Unknown top-level key: " + key);
        }
        if (!result.has("format") || !result.get("format").isJsonPrimitive()
                || !result.getAsJsonPrimitive("format").isNumber() || !"1".equals(result.get("format").getAsString())) {
            throw new IllegalArgumentException("format must be 1");
        }
        JsonObject ground = object(result, "terrain");
        for (String key : ground.keySet()) {
            if (key.equals("shape")) {
                for (String field : object(ground, "shape").keySet()) {
                    if (PARAMETERS.stream().noneMatch(p -> p.key().equals("shape." + field))) {
                        throw new IllegalArgumentException("Unknown terrain setting: shape." + field);
                    }
                }
            } else if (PARAMETERS.stream().noneMatch(p -> p.key().equals(key))) {
                throw new IllegalArgumentException("Unknown terrain setting: " + key);
            }
        }
        for (Parameter setting : PARAMETERS) {
            JsonElement value = terrainValue(ground, setting.key);
            if (value == null) continue;
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(setting.key + " must be numeric");
            }
            double number = value.getAsDouble();
            if (!Double.isFinite(number) || number < setting.min || number > setting.max
                    || setting.value.apply(AtlasProfile.DEFAULT) instanceof Integer && number != Math.rint(number)) {
                throw new IllegalArgumentException(setting.key + " must be in " + setting.range());
            }
        }
        AtlasProfile profile = parseProfile(ground);
        result.add("terrain", terrain(profile));
        JsonObject biomes = object(result, "biomes");
        Set<String> expected = presetGenerator(resource(PRESET)).getAsJsonObject("biome_source").getAsJsonObject("regions").keySet();
        if (!biomes.keySet().equals(expected)) throw new IllegalArgumentException("biomes must define every Atlas region exactly once");
        biomes.asMap().forEach((key, value) -> {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid biome for " + key);
            resourceId(value.getAsString());
        });
        AtlasContent.parse(object(result, "content"));
        Gson lootParser = Deserializers.createLootTableSerializer().create();
        for (var entry : object(result, "loot_tables").entrySet()) {
            ResourceLocation id = resourceId(entry.getKey());
            if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("Loot table must be an object: " + entry.getKey());
            try {
                ForgeHooks.loadLootTable(lootParser, id, entry.getValue(), true);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(entry.getKey() + ": " + e.getMessage(), e);
            }
        }
        return new AtlasStudioConfig(result, profile);
    }

    public AtlasChunkGenerator generator(RegistryAccess registries) {
        return AtlasChunkGenerator.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), generatorJson())
                .getOrThrow(false, message -> {});
    }

    public void saveJson(Path path) throws IOException {
        writeAtomic(path, temp -> Files.writeString(temp, pretty(document), StandardCharsets.UTF_8));
    }

    public static JsonObject readJson(Path path) throws IOException {
        if (Files.size(path) > MAX_CHARS * 3L) throw new IOException("JSON exceeds the Studio size limit");
        return validate(parseText(Files.readString(path, StandardCharsets.UTF_8))).document();
    }

    public void exportPack(Path path) throws IOException {
        writeAtomic(path, temp -> {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temp), StandardCharsets.UTF_8)) {
                add(zip, "pack.mcmeta", JsonParser.parseString("{\"pack\":{\"pack_format\":15,\"description\":\"Strata Atlas Studio\"}}"));
                JsonObject preset = resource(PRESET);
                JsonObject generator = presetGenerator(preset);
                generator.add("profile", document.get("terrain"));
                generator.getAsJsonObject("biome_source").add("regions", document.get("biomes"));
                add(zip, PRESET.substring(1), preset);
                add(zip, "data/stratacubes/atlas/content.json", document.get("content"));
                for (var entry : document.getAsJsonObject("loot_tables").entrySet()) {
                    ResourceLocation id = resourceId(entry.getKey());
                    add(zip, "data/" + id.getNamespace() + "/loot_tables/" + id.getPath() + ".json", entry.getValue());
                }
                add(zip, "atlas-studio.json", document);
            }
        });
    }

    public static String pretty(JsonElement value) { return PRETTY.toJson(value); }

    private JsonObject generatorJson() {
        JsonObject generator = new JsonObject();
        JsonObject source = new JsonObject();
        source.add("regions", document.get("biomes"));
        generator.add("biome_source", source);
        generator.add("profile", document.get("terrain"));
        return generator;
    }

    private static AtlasProfile parseProfile(JsonObject object) {
        return AtlasCodecs.PROFILE.parse(JsonOps.INSTANCE, object).getOrThrow(false, message -> {});
    }

    private static JsonObject terrain(AtlasProfile profile) {
        JsonObject object = new JsonObject();
        for (Parameter parameter : PARAMETERS) {
            setTerrainValue(object, parameter.key, new com.google.gson.JsonPrimitive(parameter.value.apply(profile)));
        }
        return object;
    }

    public static JsonElement terrainValue(JsonObject settings, String key) {
        int dot = key.indexOf('.');
        if (dot < 0) return settings.get(key);
        JsonObject parent = settings.getAsJsonObject(key.substring(0, dot));
        return parent == null ? null : parent.get(key.substring(dot + 1));
    }

    public static void setTerrainValue(JsonObject settings, String key, JsonElement value) {
        int dot = key.indexOf('.');
        if (dot < 0) {
            settings.add(key, value);
        } else {
            String group = key.substring(0, dot);
            if (!settings.has(group)) settings.add(group, new JsonObject());
            settings.getAsJsonObject(group).add(key.substring(dot + 1), value);
        }
    }

    private static JsonObject presetGenerator(JsonObject preset) {
        return preset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
    }

    private static JsonObject object(JsonObject root, String name) {
        if (!root.has(name) || !root.get(name).isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        return root.getAsJsonObject(name);
    }

    private static ResourceLocation resourceId(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null || name.indexOf(':') < 1 || id.getPath().startsWith("/")
                || List.of(id.getPath().split("/", -1)).stream().anyMatch(p -> p.isEmpty() || p.equals(".") || p.equals(".."))) {
            throw new IllegalArgumentException("Invalid namespaced ID: " + name);
        }
        return id;
    }

    private static JsonObject resource(String path) {
        try (var stream = AtlasStudioConfig.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void add(ZipOutputStream zip, String path, JsonElement json) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(pretty(json).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    @FunctionalInterface
    private interface FileWriter { void write(Path path) throws IOException; }

    private static void writeAtomic(Path output, FileWriter writer) throws IOException {
        Path directory = output.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "atlas-", ".tmp");
        try {
            writer.write(temp);
            try {
                Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String readable(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
