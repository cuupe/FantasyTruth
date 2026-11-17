package dev.fantasytruth.smoke;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import dev.fantasytruth.atlas.AtlasChunkGenerator;
import dev.fantasytruth.atlas.AtlasStudioConfig;
import dev.fantasytruth.terrain.AtlasField;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.WorldTypeEntry;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.RegistryLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** UI and persistence fixture for an isolated launcher directory. */
@Mod("atlasstudiosmoke")
public final class AtlasStudioSmokeMod {
    private static final boolean REOPEN = Boolean.getBoolean("atlas.studio.reopen");
    private int stage;
    private int frames;
    private CreateWorldScreen creation;
    private boolean checked;
    private boolean reapplied;

    public AtlasStudioSmokeMod() { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent
    public void client(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage < 0) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (stage == 0 && mc.screen instanceof TitleScreen title) {
                stage++;
                require(ModList.get().getModContainerById("stratacubes").orElseThrow()
                        .getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class).isPresent(), "Mods menu config is registered");
                if (REOPEN) {
                    ObfuscationReflectionHelper.findMethod(WorldOpenFlows.class, "m_233145_", Screen.class, String.class,
                            boolean.class, boolean.class).invoke(mc.createWorldOpenFlows(), title, "AtlasStudioSmoke", false, false);
                    stage = 5;
                } else {
                    CreateWorldScreen.openFresh(mc, title);
                }
            } else if (stage == 1 && mc.screen instanceof CreateWorldScreen screen) {
                stage++;
                creation = screen;
                var ui = creation.getUiState();
                ui.setName("AtlasStudioSmoke");
                ui.setSeed("72019");
                ui.setGameMode(SelectedGameMode.CREATIVE);
                ui.setAllowCheats(true);
                var preset = ResourceKey.create(Registries.WORLD_PRESET, new ResourceLocation("stratacubes", "atlas"));
                var registry = ui.getSettings().worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET);
                ui.setWorldType(new WorldTypeEntry(registry.getHolderOrThrow(preset)));
                mc.setScreen(ui.getPresetEditor().createEditScreen(creation, ui.getSettings()));
            } else if (stage == 2 && ++frames >= 65) {
                require(mc.screen.getClass().getSimpleName().equals("AtlasStudioScreen"), "Atlas opens via native customize button");
                Screenshot.grab(mc.gameDirectory, "atlas-studio-form.png", mc.getMainRenderTarget(), ignored -> {});
                EditBox sea = mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .filter(box -> box.getMessage().getString().equals("Sea level") || box.getMessage().getString().equals("海平面"))
                        .findFirst().orElseThrow();
                sea.setValue("135");
                press(mc.screen, "deposits");
                EditBox factor = mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .filter(box -> box.getMessage().getContents() instanceof TranslatableContents text
                                && text.getKey().equals("screen.stratacubes.density_factor")).findFirst().orElseThrow();
                factor.setValue("0.5");
                press(mc.screen, "scale_ores");
                press(mc.screen, "json");
                MultiLineEditBox raw = json(mc.screen);
                JsonObject draft = JsonParser.parseString(raw.getValue()).getAsJsonObject();
                require(draft.getAsJsonObject("terrain").get("sea_level").getAsInt() == 135, "form commits into JSON");
                require(draft.getAsJsonObject("content").getAsJsonArray("deposits").get(0).getAsJsonObject()
                        .get("chance").getAsDouble() == 0.235, "bulk multiplier changed ore probabilities");
                draft.getAsJsonObject("terrain").addProperty("runoff_strength", 1.4);
                draft.getAsJsonObject("content").add("fixed_sites", JsonParser.parseString("[{\"landmark\":\"watchtower\",\"x\":32,\"y\":170,\"z\":0}]"));
                draft.getAsJsonObject("loot_tables").add("stratacubes:chests/watchtower", JsonParser.parseString(
                        "{\"type\":\"minecraft:chest\",\"pools\":[{\"name\":\"studio\",\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:emerald\",\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":6}]}]}]}"));
                raw.setValue(draft.toString());
                AtlasStudioConfig.validate(draft);
                press(mc.screen, "write");
                press(mc.screen, "export");
                Path dir = mc.gameDirectory.toPath().resolve("config/stratacubes");
                Path file = dir.resolve("studio.json");
                var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                require(saved.getAsJsonObject("terrain").get("runoff_strength").getAsDouble() == 1.4, "raw JSON saved");
                try (var zip = new ZipFile(dir.resolve("atlas-studio.zip").toFile())) {
                    require(zip.getEntry("atlas-studio.json") != null && zip.getEntry("data/stratacubes/atlas/content.json") != null,
                            "whole data pack exported");
                }
                saved.getAsJsonObject("terrain").addProperty("caves", 0.8);
                Files.writeString(file, saved.toString());
                press(mc.screen, "read");
                require(JsonParser.parseString(json(mc.screen).getValue()).getAsJsonObject().getAsJsonObject("terrain")
                        .get("caves").getAsDouble() == 0.8, "manual file edit imported back into UI");
                press(mc.screen, "map");
                stage++;
                frames = 0;
                System.out.println("ATLAS_STUDIO_EDITOR_PASS");
            } else if (stage == 3 && ++frames >= 35) {
                Screenshot.grab(mc.gameDirectory, "atlas-studio-map.png", mc.getMainRenderTarget(), ignored -> {});
                press(mc.screen, "apply");
                stage++;
            } else if (stage == 4 && mc.screen == creation) {
                var context = creation.getUiState().getSettings();
                require(context.dataConfiguration().dataPacks().getEnabled().stream().anyMatch(id -> id.startsWith("file/atlas-studio-")),
                        "Apply all activated one real data pack");
                var generator = (AtlasChunkGenerator) context.selectedDimensions().overworld();
                require(generator.profile().seaLevel() == 135 && generator.profile().runoffStrength() == 1.4
                        && generator.profile().caves() == 0.8, "selected Overworld generator received the entire profile");
                if (!reapplied) {
                    reapplied = true;
                    var ui = creation.getUiState();
                    mc.setScreen(ui.getPresetEditor().createEditScreen(creation, context));
                    press(mc.screen, "json");
                    MultiLineEditBox editor = json(mc.screen);
                    JsonObject draft = JsonParser.parseString(editor.getValue()).getAsJsonObject();
                    require(draft.getAsJsonObject("content").getAsJsonArray("fixed_sites").size() == 1, "reopen retains the draft contents");
                    draft.getAsJsonObject("terrain").addProperty("runoff_steps", 18);
                    editor.setValue(draft.toString());
                    press(mc.screen, "apply");
                    return;
                }
                require(context.dataConfiguration().dataPacks().getEnabled().stream().filter(id -> id.startsWith("file/atlas-studio-")).count() == 1
                        && generator.profile().runoffSteps() == 18, "second apply replaced the old pack");
                var dimensions = context.selectedDimensions().bake(context.datapackDimensions());
                var registries = context.worldgenRegistries().replaceFrom(RegistryLayer.DIMENSIONS, dimensions.dimensionsRegistryAccess());
                ObfuscationReflectionHelper.findMethod(CreateWorldScreen.class, "m_245184_", PrimaryLevelData.SpecialWorldProperty.class,
                        LayeredRegistryAccess.class, Lifecycle.class).invoke(creation, dimensions.specialWorldProperty(), registries,
                        registries.compositeAccess().allRegistriesLifecycle());
                stage++;
                System.out.println("ATLAS_STUDIO_APPLY_PASS");
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    @SubscribeEvent
    public void server(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || checked || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        checked = true;
        try {
            var level = event.getServer().overworld();
            var generator = (AtlasChunkGenerator) level.getChunkSource().getGenerator();
            require(generator.profile().runoffSteps() == 18 && generator.profile().seaLevel() == 135, "profile saved in level data");
            var expected = new AtlasField(AtlasChunkGenerator.terrainSeed(level.getSeed()), generator.profile()).column(-400, 640);
            int actual = ((ChunkGenerator) generator).getBaseHeight(-400, 640, Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
            require(expected.height() + 1 == actual, "preview seed matches server terrain");
            var chest = (ChestBlockEntity) level.getBlockEntity(new BlockPos(33, 185, 1));
            require(chest != null, "Studio fixed-site config generated a real watchtower");
            int emeralds = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.EMERALD)) emeralds += chest.getItem(i).getCount();
            require(emeralds == 6, "Studio loot JSON reached the server");
            try (var packs = Files.list(event.getServer().getWorldPath(LevelResource.DATAPACK_DIR))) {
                require(packs.filter(path -> path.getFileName().toString().startsWith("atlas-studio-")).count() == 1,
                        "exactly one pack was copied into the actual save");
            }
            event.getServer().saveEverything(true, true, true);
            System.out.println(REOPEN ? "ATLAS_STUDIO_REOPEN_PASS" : "ATLAS_STUDIO_WORLD_PASS");
            Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
        } catch (Throwable failure) { fail(failure); }
    }

    private static MultiLineEditBox json(Screen screen) {
        return screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast)
                .findFirst().orElseThrow();
    }

    private static void press(Screen screen, String key) {
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getContents() instanceof TranslatableContents text
                        && text.getKey().equals("screen.stratacubes." + key))
                .findFirst().orElseThrow().onPress();
    }

    private static void require(boolean condition, String check) {
        if (!condition) throw new AssertionError(check);
    }

    private void fail(Throwable failure) {
        System.err.println("ATLAS_STUDIO_FAIL stage=" + stage);
        failure.printStackTrace();
        stage = -1;
        Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
    }
}
