package dev.fantasytruth.smoke;

import com.mojang.serialization.Lifecycle;
import dev.fantasytruth.atlas.AtlasChunkGenerator;
import dev.fantasytruth.terrain.AtlasField;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Uses the actual integrated server for spawn, chunk generation, and a paired benchmark. */
@Mod("atlasterrainsmoke")
public final class AtlasTerrainSmokeMod {
    private static final boolean BENCHMARK = Boolean.getBoolean("atlas.terrain.bench");
    private static final long SEED = Long.getLong("atlas.terrain.seed", 72019L);
    private volatile int stage;
    private int serverTicks;

    public AtlasTerrainSmokeMod() { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent
    public void client(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage < 0) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (stage == 0 && mc.screen instanceof TitleScreen title) {
                stage = 1;
                CreateWorldScreen.openFresh(mc, title);
            } else if (stage == 1 && mc.screen instanceof CreateWorldScreen screen) {
                stage = 2;
                var ui = screen.getUiState();
                ui.setName("AtlasTerrainSmoke");
                ui.setSeed(Long.toString(SEED));
                ui.setGameMode(SelectedGameMode.SURVIVAL);
                ui.setWorldType(ui.getNormalPresetList().stream().filter(entry -> entry.preset() != null
                        && entry.preset().unwrapKey().orElseThrow().location().toString().equals("stratacubes:atlas"))
                        .findFirst().orElseThrow());
                var context = ui.getSettings();
                var dimensions = context.selectedDimensions().bake(context.datapackDimensions());
                var registries = context.worldgenRegistries().replaceFrom(RegistryLayer.DIMENSIONS, dimensions.dimensionsRegistryAccess());
                ObfuscationReflectionHelper.findMethod(CreateWorldScreen.class, "m_245184_", PrimaryLevelData.SpecialWorldProperty.class,
                        LayeredRegistryAccess.class, Lifecycle.class).invoke(screen, dimensions.specialWorldProperty(), registries,
                        registries.compositeAccess().allRegistriesLifecycle());
            }
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent
    public void anchorBenchmark(LevelEvent.CreateSpawnPosition event) {
        if (!BENCHMARK || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        event.getSettings().setSpawn(new BlockPos(8, 187, 8), 0);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void server(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage != 2 || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        if (++serverTicks < 25) return;
        stage = 3;
        try {
            ServerLevel level = event.getServer().overworld();
            if (BENCHMARK) {
                level.getChunk(78, -105);
                var chunks = new ArrayList<ChunkAccess>();
                long started = System.nanoTime();
                for (int z = -142; z <= -140; z++) {
                    for (int x = 119; x <= 121; x++) chunks.add(level.getChunk(x, z));
                }
                double millis = (System.nanoTime() - started) / 1e6;
                long digest = 17;
                for (var chunk : chunks) {
                    for (var section : chunk.getSections()) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                for (int x = 0; x < 16; x++) digest = digest * 31 + Block.getId(section.getBlockState(x, y, z));
                            }
                        }
                    }
                }
                System.out.printf(java.util.Locale.ROOT, "ATLAS_TERRAIN_BENCH_PASS seed=%d chunks=9 ms=%.1f digest=%016x%n",
                        SEED, millis, digest);
            } else {
                var atlas = (AtlasChunkGenerator) level.getChunkSource().getGenerator();
                var terrain = new AtlasField(AtlasChunkGenerator.terrainSeed(level.getSeed()), atlas.profile());
                var player = event.getServer().getPlayerList().getPlayers().get(0);
                var surface = terrain.column(player.getBlockX(), player.getBlockZ());
                if (surface.height() < atlas.profile().seaLevel() + 3 || player.getY() <= atlas.profile().seaLevel()
                        || !level.getFluidState(player.blockPosition()).isEmpty()) {
                    throw new AssertionError("Unsafe water spawn at " + player.blockPosition());
                }
                System.out.printf("ATLAS_TERRAIN_SPAWN_PASS seed=%d spawn=%s surface=%d%n", SEED,
                        player.blockPosition(), surface.height());
            }
            event.getServer().saveEverything(true, true, true);
            Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
        } catch (Throwable failure) { fail(failure); }
    }

    private void fail(Throwable failure) {
        stage = -1;
        System.err.println("ATLAS_TERRAIN_FAIL");
        failure.printStackTrace();
        Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
    }
}
