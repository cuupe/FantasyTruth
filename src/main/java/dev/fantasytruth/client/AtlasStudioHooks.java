package dev.fantasytruth.client;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import dev.fantasytruth.atlas.AtlasChunkGenerator;
import dev.fantasytruth.atlas.AtlasStudioConfig;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

public final class AtlasStudioHooks {
    private record Applied(Path file, JsonObject config) {}
    private record Staged(Path file, String id, AtlasStudioConfig config) {}
    private static final Map<WorldCreationUiState, Applied> APPLIED = new WeakHashMap<>();
    private static final Map<WorldCreationUiState, Staged> STAGED = new WeakHashMap<>();
    private static final ResourceLocation PRESET = new ResourceLocation("stratacubes", "atlas");
    public static final PresetEditor EDITOR = AtlasStudioHooks::openCreation;

    public static boolean isAtlas(WorldCreationUiState state) {
        var preset = state.getWorldType().preset();
        return preset != null && preset.unwrapKey().map(key -> key.location().equals(PRESET)).orElse(false);
    }

    private static Screen openCreation(CreateWorldScreen parent, WorldCreationContext context) {
        Applied applied = APPLIED.get(parent.getUiState());
        JsonObject config = applied != null ? applied.config.deepCopy() :
                context.selectedDimensions().overworld() instanceof AtlasChunkGenerator atlas
                        ? AtlasStudioConfig.fromGenerator(atlas, context.worldgenLoadContext()) : AtlasStudioConfig.defaults();
        return new AtlasStudioScreen(parent, parent, config, context.options().seed());
    }

    static Screen openStandalone(Minecraft minecraft, Screen parent) {
        return new AtlasStudioScreen(parent, null, AtlasStudioConfig.defaults(), 72019L);
    }

    static void apply(CreateWorldScreen parent, AtlasStudioScreen editor, AtlasStudioConfig config) throws IOException {
        WorldCreationUiState state = parent.getUiState();
        WorldCreationContext context = state.getSettings();
        config.generator(context.worldgenLoadContext());
        Pair<Path, PackRepository> selection = packSelection(parent, context.dataConfiguration());
        if (selection == null) throw new IOException("Minecraft could not open the world's temporary data-pack directory");
        Path file = Files.createTempFile(selection.getFirst(), "atlas-studio-", ".zip");
        PackRepository repo = selection.getSecond();
        List<String> before = new ArrayList<>(repo.getSelectedIds());
        String id = packId(file);
        Applied previous = APPLIED.get(state);
        List<String> next = new ArrayList<>(before);
        if (previous != null) next.remove(packId(previous.file));
        next.add(id);
        try {
            config.exportPack(file);
            repo.reload();
            if (!repo.isAvailable(id)) throw new IOException("Minecraft could not discover the generated Atlas pack");
            repo.setSelected(next);
            STAGED.put(state, new Staged(file, id, config));
            Consumer<WorldDataConfiguration> failed = ignored -> {
                rollback(state, repo, before, file);
                editor.report(net.minecraft.network.chat.Component.translatable("screen.stratacubes.apply_failed"), true);
                Minecraft.getInstance().setScreen(editor);
            };
            ObfuscationReflectionHelper.findMethod(CreateWorldScreen.class, "m_269443_", PackRepository.class,
                    boolean.class, Consumer.class).invoke(parent, repo, true, failed);
        } catch (ReflectiveOperationException | RuntimeException | IOException e) {
            rollback(state, repo, before, file);
            Throwable cause = e instanceof InvocationTargetException invocation ? invocation.getCause() : e;
            throw new IOException("Cannot apply Atlas configuration: " + cause.getMessage(), cause);
        }
    }

    public static void onSettings(WorldCreationUiState state, WorldCreationContext context) {
        Staged pending = STAGED.get(state);
        if (pending == null || !context.dataConfiguration().dataPacks().getEnabled().contains(pending.id)) return;
        STAGED.remove(state);
        state.updateDimensions((registries, dimensions) -> dimensions.replaceOverworldGenerator(registries,
                pending.config.generator(registries)));
        Applied previous = APPLIED.put(state, new Applied(pending.file, pending.config.document().deepCopy()));
        if (previous != null) remove(previous.file);
    }

    @SuppressWarnings("unchecked")
    private static Pair<Path, PackRepository> packSelection(CreateWorldScreen screen, WorldDataConfiguration config) throws IOException {
        try {
            return (Pair<Path, PackRepository>) ObfuscationReflectionHelper.findMethod(CreateWorldScreen.class,
                    "m_267637_", WorldDataConfiguration.class).invoke(screen, config);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Cannot access Minecraft's data-pack selection", e);
        }
    }

    private static String packId(Path path) { return "file/" + path.getFileName(); }

    private static void rollback(WorldCreationUiState state, PackRepository repo, List<String> before, Path staged) {
        STAGED.remove(state);
        remove(staged);
        repo.reload();
        repo.setSelected(before);
    }

    private static void remove(Path file) {
        try { Files.deleteIfExists(file); }
        catch (IOException e) { com.mojang.logging.LogUtils.getLogger().warn("Unable to remove superseded Atlas pack {}", file, e); }
    }

    private AtlasStudioHooks() {}
}
