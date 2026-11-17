package dev.fantasytruth.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.NativeImage;
import dev.fantasytruth.atlas.AtlasStudioConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AtlasStudioScreen extends Screen {
    private static final int ACCENT = 0x73e5c7;
    private static final int INK = 0xe3eeed;
    private static final int MUTED = 0xa7bfc4;
    private static final int ROW_TOP = 106;
    private static final int ROW_HEIGHT = 22;
    private static final int FOOTER = 48;
    private static final int HORIZON_BUTTON_WIDTH = 90;
    private static final ResourceLocation MAP_TEXTURE = new ResourceLocation("stratacubes", "studio_preview");

    private enum Tab { TERRAIN, BIOMES, DEPOSITS, LANDMARKS, LOOT, JSON, MAP }
    private enum Scope {
        ALL(""), TERRAIN("terrain"), BIOMES("biomes"), DEPOSITS("content.deposits"),
        LANDMARKS("content.landmarks"), FIXED("content.fixed_sites"), LOOT("loot_tables");

        final String path;
        Scope(String path) { this.path = path; }
    }
    private record Row(String path, String label, String info, JsonElement value, Consumer<JsonElement> set) {}

    private final Screen parent;
    private final CreateWorldScreen creation;
    private final Map<String, String> edits = new LinkedHashMap<>();
    private JsonObject document;
    private Tab tab = Tab.TERRAIN;
    private Scope scope = Scope.ALL;
    private String rawText = "";
    private String filter = "";
    private String densityFactor = "1";
    private boolean fixedSites;
    private int page;
    private List<Row> rows = List.of();
    private int pageSize;
    private int left;
    private int panelWidth;
    private EditBox filterBox;
    private Component status = Component.empty();
    private boolean error;
    private String seedText;
    private int centerX;
    private int centerZ;
    private int span = 6144;
    private int request;
    private boolean loading;
    private boolean biomeMap;
    private AtlasStudioPreview.Data preview;
    private boolean textureLoaded;

    AtlasStudioScreen(Screen parent, CreateWorldScreen creation, JsonObject document, long seed) {
        super(t("title"));
        this.parent = parent;
        this.creation = creation;
        this.document = document.deepCopy();
        seedText = Long.toString(seed);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 20, 760);
        left = (width - panelWidth) / 2;
        if (creation == null) {
            button(left + panelWidth - HORIZON_BUTTON_WIDTH, 12, HORIZON_BUTTON_WIDTH,
                    Component.translatable(HorizonVideoSettings.LABEL), () -> {
                        if (commitEdits()) minecraft.setScreen(HorizonVideoSettings.open(this));
                    });
        }
        int index = 0;
        for (Tab entry : Tab.values()) {
            int x = left + panelWidth * index / Tab.values().length;
            int end = left + panelWidth * (++index) / Tab.values().length;
            Button button = button(x, 45, end - x - 3, t(key(entry)), () -> select(entry));
            button.active = entry != tab;
        }
        if (tab == Tab.JSON) initJson();
        else if (tab == Tab.MAP) initMap();
        else initTable();
        int n = 0;
        for (String action : List.of("read", "write", "export", creation == null ? "validate" : "apply", "back")) {
            int x = left + panelWidth * n / 5;
            int end = left + panelWidth * (++n) / 5;
            button(x, height - 25, end - x - 3, t(action), () -> action(action));
        }
    }

    private void initTable() {
        rows = tableRows();
        pageSize = Math.max(1, (height - FOOTER - ROW_TOP) / ROW_HEIGHT);
        page = Math.min(page, Math.max(0, (rows.size() - 1) / pageSize));
        int right = left + panelWidth;
        int filterWidth = Math.max(38, Math.min(122, panelWidth - (tab == Tab.DEPOSITS ? 258 : 236)));
        filterBox = textBox(left, 72, filterWidth, t("filter"), filter, 120);
        button(left + filterWidth + 3, 71, 46, t("filter"), () -> {
            if (!commitEdits()) return;
            filter = filterBox.getValue();
            page = 0;
            rebuildWidgets();
        });
        button(right - 61, 71, 61, t("section_json"), () -> openRaw(tableScope()));
        button(right - 86, 71, 22, Component.literal(">"), () -> changePage(1)).active = (page + 1) * pageSize < rows.size();
        button(right - 111, 71, 22, Component.literal("<"), () -> changePage(-1)).active = page > 0;
        if (tab == Tab.LANDMARKS) {
            button(left + filterWidth + 52, 71, 61, t(fixedSites ? "landmarks" : "fixed"), () -> {
                if (!commitEdits()) return;
                fixedSites = !fixedSites;
                filter = "";
                page = 0;
                rebuildWidgets();
            });
        } else if (tab == Tab.DEPOSITS && panelWidth >= 295) {
            int x = left + filterWidth + 51;
            EditBox factor = textBox(x, 72, 34, t("density_factor"), densityFactor, 8);
            factor.setResponder(value -> densityFactor = value);
            factor.setTooltip(Tooltip.create(t("density_factor")));
            button(x + 38, 71, 53, t("scale_ores"), this::scaleDeposits);
        }
        int column = Math.max(80, panelWidth / 2);
        int end = Math.min(rows.size(), (page + 1) * pageSize);
        for (int i = page * pageSize; i < end; i++) {
            Row row = rows.get(i);
            int y = ROW_TOP + (i - page * pageSize) * ROW_HEIGHT;
            EditBox edit = textBox(left + column, y, panelWidth - column - 2, Component.literal(row.label),
                    edits.getOrDefault(row.path, text(row.value)), 4096);
            edit.setResponder(value -> edits.put(row.path, value));
            edit.setTooltip(Tooltip.create(Component.literal(row.path + (row.info.isEmpty() ? "" : "  ·  " + row.info))));
        }
    }

    private void scaleDeposits() {
        if (!commitEdits()) return;
        try {
            double factor = Double.parseDouble(densityFactor.strip());
            if (!Double.isFinite(factor) || factor < 0 || factor > 10) throw new IllegalArgumentException();
            JsonArray deposits = get(Scope.DEPOSITS).getAsJsonArray();
            for (JsonElement item : deposits) {
                JsonObject rule = item.getAsJsonObject();
                double chance = rule.has("chance") ? rule.get("chance").getAsDouble() : 1;
                rule.addProperty("chance", Math.min(1, chance * factor));
            }
            report(t("scaled_ores", deposits.size()), false);
            rebuildWidgets();
        } catch (RuntimeException e) {
            report(t("density_factor"), true);
        }
    }

    private List<Row> tableRows() {
        List<Row> result = new ArrayList<>();
        Scope current = tableScope();
        if (current == Scope.TERRAIN) {
            JsonObject settings = document.getAsJsonObject("terrain");
            for (var parameter : AtlasStudioConfig.PARAMETERS) {
                JsonElement value = AtlasStudioConfig.terrainValue(settings, parameter.key());
                if (value != null) result.add(new Row("terrain." + parameter.key(), t("terrain." + parameter.key()).getString(),
                        parameter.range(), value, element -> AtlasStudioConfig.setTerrainValue(settings, parameter.key(), element)));
            }
        } else {
            JsonElement part = get(current);
            walk(result, current.path, "", part, ignored -> {});
        }
        String query = filter.strip().toLowerCase(Locale.ROOT);
        return query.isEmpty() ? result : result.stream().filter(row -> (row.path + " " + row.label)
                .toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private static void walk(List<Row> rows, String path, String label, JsonElement value, Consumer<JsonElement> setter) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (var entry : object.entrySet()) {
                String key = entry.getKey();
                walk(rows, path + "." + key, join(label, key), entry.getValue(), element -> object.add(key, element));
            }
        } else if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            boolean simple = true;
            for (JsonElement child : array) if (!child.isJsonPrimitive()) simple = false;
            if (simple && !label.isEmpty()) {
                rows.add(new Row(path, label, "JSON []", array, setter));
                return;
            }
            for (int i = 0; i < array.size(); i++) {
                int index = i;
                JsonElement child = array.get(i);
                String name = Integer.toString(i + 1);
                if (child.isJsonObject()) {
                    JsonObject object = child.getAsJsonObject();
                    for (String key : List.of("id", "name", "landmark")) {
                        if (object.has(key) && object.get(key).isJsonPrimitive()) { name = object.get(key).getAsString(); break; }
                    }
                }
                walk(rows, path + "[" + i + "]", join(label, name), child, element -> array.set(index, element));
            }
        } else if (!label.isEmpty()) {
            rows.add(new Row(path, label, "", value, setter));
        }
    }

    private static String join(String parent, String label) { return parent.isEmpty() ? label : parent + " / " + label; }

    private void changePage(int delta) {
        if (!commitEdits()) return;
        page = Math.max(0, page + delta);
        rebuildWidgets();
    }

    private void select(Tab next) {
        if (!commitEdits()) return;
        tab = next;
        page = 0;
        filter = "";
        if (next == Tab.JSON) {
            scope = Scope.ALL;
            rawText = AtlasStudioConfig.pretty(document);
        }
        rebuildWidgets();
        if (next == Tab.MAP) refreshPreview();
    }

    private void openRaw(Scope part) {
        if (!commitEdits()) return;
        scope = part;
        tab = Tab.JSON;
        rawText = AtlasStudioConfig.pretty(get(part));
        rebuildWidgets();
    }

    private void initJson() {
        button(left + panelWidth - 67, 71, 67, t("format_json"), () -> {
            if (!commitEdits()) return;
            rawText = AtlasStudioConfig.pretty(get(scope));
            rebuildWidgets();
        });
        MultiLineEditBox editor = new MultiLineEditBox(font, left, 96, panelWidth, Math.max(36, height - FOOTER - 96),
                t("json_hint"), t("json"));
        editor.setCharacterLimit(1_048_576);
        editor.setValue(rawText);
        editor.setValueListener(value -> rawText = value);
        addRenderableWidget(editor);
    }

    private Scope tableScope() {
        return switch (tab) {
            case TERRAIN -> Scope.TERRAIN;
            case BIOMES -> Scope.BIOMES;
            case DEPOSITS -> Scope.DEPOSITS;
            case LANDMARKS -> fixedSites ? Scope.FIXED : Scope.LANDMARKS;
            case LOOT -> Scope.LOOT;
            default -> Scope.ALL;
        };
    }

    private JsonElement get(Scope section) {
        if (section == Scope.ALL) return document;
        String[] path = section.path.split("\\.");
        JsonElement value = document.get(path[0]);
        if (path.length == 2) value = value.getAsJsonObject().get(path[1]);
        if (value == null) return new JsonArray();
        return value;
    }

    private boolean commitEdits() {
        try {
            if (tab == Tab.JSON) {
                JsonElement value = JsonParser.parseString(rawText);
                if (scope == Scope.ALL) {
                    document = AtlasStudioConfig.validate(value.getAsJsonObject()).document();
                } else {
                    JsonObject next = document.deepCopy();
                    String[] keys = scope.path.split("\\.");
                    if (keys.length == 1) next.add(keys[0], value);
                    else next.getAsJsonObject(keys[0]).add(keys[1], value);
                    document = AtlasStudioConfig.validate(next).document();
                }
            } else if (!edits.isEmpty()) {
                var assignments = new ArrayList<Runnable>();
                for (Row row : rows) {
                    if (!edits.containsKey(row.path)) continue;
                    String value = edits.get(row.path).strip();
                    JsonElement parsed = row.value.isJsonPrimitive() && row.value.getAsJsonPrimitive().isString()
                            ? new JsonPrimitive(value) : JsonParser.parseString(value);
                    if (row.value.isJsonPrimitive() && row.value.getAsJsonPrimitive().isNumber()
                            && (!parsed.isJsonPrimitive() || !parsed.getAsJsonPrimitive().isNumber())) {
                        throw new IllegalArgumentException(row.path + " must be numeric");
                    }
                    assignments.add(() -> row.set.accept(parsed));
                }
                assignments.forEach(Runnable::run);
                edits.clear();
            }
            return true;
        } catch (RuntimeException e) {
            report(Component.literal(e.getMessage() == null ? "Invalid JSON" : e.getMessage()), true);
            return false;
        }
    }

    private void action(String action) {
        if (action.equals("back")) { onClose(); return; }
        try {
            if (action.equals("read")) {
                document = AtlasStudioConfig.readJson(directory().resolve("studio.json"));
                edits.clear();
                rawText = AtlasStudioConfig.pretty(get(scope));
                page = 0;
                report(t("loaded"), false);
                rebuildWidgets();
                if (tab == Tab.MAP) refreshPreview();
                return;
            }
            if (!commitEdits()) return;
            AtlasStudioConfig config = AtlasStudioConfig.validate(document);
            switch (action) {
                case "write" -> {
                    config.saveJson(directory().resolve("studio.json"));
                    report(t("saved_json"), false);
                }
                case "export" -> {
                    config.exportPack(directory().resolve("atlas-studio.zip"));
                    report(t("saved_zip"), false);
                }
                case "apply" -> AtlasStudioHooks.apply(creation, this, config);
                default -> report(t("valid"), false);
            }
        } catch (IOException | RuntimeException e) {
            report(Component.literal(e.getMessage() == null ? action : e.getMessage()), true);
        }
    }

    private Path directory() { return minecraft.gameDirectory.toPath().resolve("config").resolve("stratacubes"); }

    void report(Component message, boolean error) { status = message; this.error = error; }

    private void initMap() {
        int x = left;
        EditBox seed = textBox(x, 72, Math.min(110, panelWidth / 3), t("seed"), seedText, 32);
        seed.setResponder(value -> seedText = value);
        x += seed.getWidth() + 4;
        button(x, 71, 47, t("refresh"), this::refreshPreview);
        x += 51;
        button(x, 71, 62, t(biomeMap ? "height" : "regions"), () -> {
            biomeMap = !biomeMap;
            upload();
            rebuildWidgets();
        });
        int right = left + panelWidth;
        button(right - 49, 96, 22, Component.literal("−"), () -> zoom(2));
        button(right - 23, 96, 22, Component.literal("+"), () -> zoom(0.5));
        button(right - 49, 120, 22, Component.literal("<"), () -> pan(-1, 0));
        button(right - 23, 120, 22, Component.literal(">"), () -> pan(1, 0));
        button(right - 49, 144, 22, Component.literal("↑"), () -> pan(0, -1));
        button(right - 23, 144, 22, Component.literal("↓"), () -> pan(0, 1));
    }

    private void zoom(double factor) {
        span = Math.max(384, Math.min(49_152, (int) (span * factor)));
        refreshPreview();
    }

    private void pan(int dx, int dz) {
        centerX = Math.max(-29_950_000, Math.min(29_950_000, centerX + dx * span / 2));
        centerZ = Math.max(-29_950_000, Math.min(29_950_000, centerZ + dz * span / 2));
        refreshPreview();
    }

    private void refreshPreview() {
        try {
            var config = AtlasStudioConfig.validate(document);
            long seed = Long.parseLong(seedText.strip());
            int x = centerX, z = centerZ, size = span, token = ++request;
            loading = true;
            report(t("preview_busy"), false);
            CompletableFuture.supplyAsync(() -> AtlasStudioPreview.sample(config.profile(), seed, x, z, size), Util.backgroundExecutor())
                    .whenComplete((result, failure) -> minecraft.execute(() -> {
                        if (request != token || minecraft.screen != this) return;
                        loading = false;
                        if (failure != null) {
                            report(Component.literal(failure.getMessage()), true);
                            return;
                        }
                        preview = result;
                        upload();
                        report(t("preview_ready"), false);
                    }));
        } catch (RuntimeException e) {
            report(Component.literal(e.getMessage() == null ? "Invalid seed or configuration" : e.getMessage()), true);
        }
    }

    private void upload() {
        if (preview == null) return;
        int[] pixels = biomeMap ? preview.biomes() : preview.height();
        NativeImage image = new NativeImage(AtlasStudioPreview.SIZE, AtlasStudioPreview.SIZE, false);
        for (int z = 0; z < AtlasStudioPreview.SIZE; z++) {
            for (int x = 0; x < AtlasStudioPreview.SIZE; x++) image.setPixelRGBA(x, z, pixels[x + z * AtlasStudioPreview.SIZE]);
        }
        minecraft.getTextureManager().register(MAP_TEXTURE, new DynamicTexture(image));
        textureLoaded = true;
    }

    private Button button(int x, int y, int w, Component name, Runnable action) {
        return addRenderableWidget(Button.builder(name, ignored -> action.run()).bounds(x, y, w, 20).build());
    }

    private EditBox textBox(int x, int y, int w, Component label, String value, int limit) {
        EditBox box = new EditBox(font, x, y, w, 18, label);
        box.setMaxLength(limit);
        box.setValue(value);
        return addRenderableWidget(box);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fillGradient(0, 0, width, height, 0xff102329, 0xff091218);
        g.fill(left, 39, left + panelWidth, 40, 0xff37766e);
        g.drawString(font, title, left, 13, ACCENT);
        int subtitleWidth = creation == null ? panelWidth - HORIZON_BUTTON_WIDTH - 6 : panelWidth;
        g.drawString(font, font.plainSubstrByWidth(t("subtitle").getString(), subtitleWidth), left, 26, MUTED);
        if (tab == Tab.JSON) g.drawString(font, t("json_scope", scope == Scope.ALL ? "atlas-studio.json" : scope.path), left, 77, INK);
        else if (tab == Tab.MAP) renderMap(g);
        else renderTable(g, mouseX, mouseY);
        g.fill(left, height - FOOTER + 3, left + panelWidth, height - FOOTER + 4, 0xff33555b);
        g.drawString(font, font.plainSubstrByWidth(status.getString(), panelWidth - 3), left, height - 37, error ? 0xffa3a3 : ACCENT);
        super.render(g, mouseX, mouseY, delta);
        if (!status.getString().isEmpty() && mouseX >= left && mouseX <= left + panelWidth
                && mouseY >= height - 39 && mouseY <= height - 28) g.renderTooltip(font, status, mouseX, mouseY);
    }

    private void renderTable(GuiGraphics g, int mouseX, int mouseY) {
        int column = panelWidth / 2;
        g.drawString(font, t("field"), left + 3, 95, MUTED);
        g.drawString(font, t("value"), left + column, 95, MUTED);
        int end = Math.min(rows.size(), (page + 1) * pageSize);
        for (int i = page * pageSize; i < end; i++) {
            Row row = rows.get(i);
            int y = ROW_TOP + (i - page * pageSize) * ROW_HEIGHT;
            g.fill(left, y, left + panelWidth, y + 19, i % 2 == 0 ? 0xff152b34 : 0xff13252d);
            String label = row.label + (row.info.isEmpty() ? "" : "  ·  " + row.info);
            g.drawString(font, font.plainSubstrByWidth(label, column - 8), left + 4, y + 5, INK);
            if (mouseX >= left && mouseX < left + column && mouseY >= y && mouseY < y + 19) {
                g.renderTooltip(font, Component.literal(row.path + (row.info.isEmpty() ? "" : "  ·  " + row.info)), mouseX, mouseY);
            }
        }
        if (rows.isEmpty()) g.drawString(font, t("empty"), left + 4, ROW_TOP + 7, MUTED);
        String pages = (page + 1) + " / " + Math.max(1, (rows.size() + pageSize - 1) / pageSize);
        g.drawString(font, pages, left + panelWidth - font.width(pages) - 3, 95, MUTED);
    }

    private void renderMap(GuiGraphics g) {
        int y = 96;
        int side = Math.max(20, Math.min(panelWidth - 62, height - FOOTER - y - 13));
        g.fill(left - 1, y - 1, left + side + 1, y + side + 1, 0xff3c726f);
        g.fill(left, y, left + side, y + side, 0xff172e38);
        if (textureLoaded) g.blit(MAP_TEXTURE, left, y, side, side, 0, 0, AtlasStudioPreview.SIZE, AtlasStudioPreview.SIZE,
                AtlasStudioPreview.SIZE, AtlasStudioPreview.SIZE);
        String where = "X " + centerX + "   Z " + centerZ + "   ·   " + span + " m";
        g.drawString(font, where, left, y + side + 4, MUTED);
        if (preview != null && !loading && side + 174 < panelWidth) {
            int legend = left + side + 16;
            g.drawString(font, t(biomeMap ? "region_legend" : "relief_legend"), legend, y + 6, ACCENT);
            g.drawString(font, "Y " + preview.low() + " … " + preview.high(), legend, y + 22, INK);
            if (biomeMap) {
                swatch(g, legend, y + 41, 0x206994, "water");
                swatch(g, legend, y + 58, 0x3c8a72, "woodland");
                swatch(g, legend, y + 75, 0xd9b16c, "dryland");
                swatch(g, legend, y + 92, 0xc7dfed, "alpine");
            } else {
                swatch(g, legend, y + 41, 0x206994, "water");
                swatch(g, legend, y + 58, 0x579274, "lowland");
                swatch(g, legend, y + 75, 0xa8a577, "highland");
                swatch(g, legend, y + 92, 0xe3edf6, "alpine");
            }
        }
    }

    private void swatch(GuiGraphics g, int x, int y, int color, String key) {
        g.fill(x, y, x + 10, y + 10, 0xff000000 | color);
        g.drawString(font, t("legend." + key), x + 16, y + 1, MUTED);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void removed() {
        request++;
        if (textureLoaded) minecraft.getTextureManager().release(MAP_TEXTURE);
        textureLoaded = false;
        preview = null;
    }

    private static String text(JsonElement value) {
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    private static String key(Tab tab) { return tab.name().toLowerCase(Locale.ROOT); }
    private static Component t(String key, Object... args) { return Component.translatable("screen.stratacubes." + key, args); }
}
