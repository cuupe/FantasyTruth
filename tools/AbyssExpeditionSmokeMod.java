package dev.fantasytruth.smoke;

import com.mojang.serialization.Lifecycle;
import dev.fantasytruth.abyss.AbyssChunkGenerator;
import dev.fantasytruth.abyss.AbyssLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.WorldTypeEntry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.RegistryLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

@Mod("abysssmoke")
public final class AbyssExpeditionSmokeMod {
    private static final long SEED = Long.getLong("stratacubes.abyss.test.seed", 1L);
    private static final BlockPos HIGH_MARKER = new BlockPos(553, 1023, 8);
    private volatile int stage;
    private volatile AbyssLayer layer;
    private int ticks;
    private ClientLevel original;
    private long crossing;

    public AbyssExpeditionSmokeMod() { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent
    public void client(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage < 0) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks > 4600) throw new AssertionError("Timeout stage=" + stage + " layer=" + layer
                    + " player=" + (mc.player == null ? null : mc.player.position()) + " screen=" + mc.screen);
            if (stage == 0 && mc.screen instanceof TitleScreen title) {
                stage = 1;
                CreateWorldScreen.openFresh(mc, title);
            } else if (stage == 1 && mc.screen instanceof CreateWorldScreen screen) {
                stage = 2;
                var ui = screen.getUiState();
                ui.setName("AbyssExpeditionSmoke");
                ui.setSeed(Long.toString(SEED));
                ui.setGameMode(SelectedGameMode.CREATIVE);
                var preset = ResourceKey.create(Registries.WORLD_PRESET, new ResourceLocation("stratacubes", "expedition"));
                var registry = ui.getSettings().worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET);
                ui.setWorldType(new WorldTypeEntry(registry.getHolderOrThrow(preset)));
                var context = ui.getSettings();
                var dimensions = context.selectedDimensions().bake(context.datapackDimensions());
                var registries = context.worldgenRegistries().replaceFrom(RegistryLayer.DIMENSIONS, dimensions.dimensionsRegistryAccess());
                ObfuscationReflectionHelper.findMethod(CreateWorldScreen.class, "m_245184_", PrimaryLevelData.SpecialWorldProperty.class,
                        LayeredRegistryAccess.class, Lifecycle.class).invoke(screen, dimensions.specialWorldProperty(), registries,
                        registries.compositeAccess().allRegistriesLifecycle());
            } else if (mc.player != null && mc.screen == null) {
                checkClient(mc);
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private void checkClient(Minecraft mc) {
        if (stage == 3 && mc.player.getBlockX() == 552 && mc.player.getY() >= 891
                && mc.level.getBlockState(HIGH_MARKER).is(Blocks.GOLD_BLOCK)) {
            original = mc.level;
            mc.options.keyJump.setDown(true);
            stage = 4;
        } else if (stage == 4 && mc.player.getY() >= 911) {
            require(mc.level == original && mc.level.dimension() == Level.OVERWORLD, "Overworld reloaded at old handoff");
            mc.options.keyJump.setDown(false);
            System.out.printf("ABYSS_CONTINUOUS_PASS y=%.2f%n", mc.player.getY());
            stage = 5;
        } else if (stage == 6 && mc.level.dimension() == Level.OVERWORLD && mc.player.getBlockX() == AbyssLayer.CENTER_X
                && Math.abs(mc.player.getY() - (AbyssLayer.MOUTH_GATE + 9)) < 2) {
            layer = AbyssLayer.EDGE;
            descend(mc);
        } else if (stage == 7 && mc.level.dimension() == layer.key && mc.level.hasChunk(0, 0)) {
            mc.options.keyShift.setDown(false);
            require(mc.player.getAbilities().flying && mc.player.getBlockX() == AbyssLayer.CENTER_X
                    && Math.abs(mc.player.getY() - layer.entrance) < 48, "Flight or position lost at passage");
            require(mc.level.getBlockState(new BlockPos(AbyssLayer.CENTER_X, layer.entrance, AbyssLayer.CENTER_Z)).isAir(),
                    "Arrival chute obstructed");
            System.out.printf("ABYSS_LAYER_CLIENT_PASS %s y=%.2f elapsed=%dms%n", layer.id,
                    mc.player.getY(), (System.nanoTime() - crossing) / 1_000_000);
            stage = 8;
        } else if (stage == 9 && mc.level.dimension() == layer.key && Math.abs(mc.player.getY() - (layer.exit + 9)) < 2) {
            layer = layer.next();
            descend(mc);
        } else if (stage == 10 && mc.level.dimension() == AbyssLayer.MAELSTROM.key
                && Math.abs(mc.player.getY() - (AbyssLayer.MAELSTROM.entrance - 9)) < 2) {
            mc.options.keyJump.setDown(true);
            stage = 11;
        } else if (stage == 11 && mc.level.dimension() == AbyssLayer.CAPITAL.key && mc.level.hasChunk(0, 0)) {
            mc.options.keyJump.setDown(false);
            require(mc.player.getAbilities().flying && mc.player.getY() > AbyssLayer.CAPITAL.exit, "Wrong ascending arrival");
            System.out.println("ABYSS_ASCENT_PASS");
            stage = -1;
            mc.stop();
        }
    }

    private void descend(Minecraft mc) {
        crossing = System.nanoTime();
        mc.options.keyShift.setDown(true);
        stage = 7;
    }

    @SubscribeEvent
    public void server(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer player = event.getServer().getPlayerList().getPlayers().get(0);
        try {
            if (stage == 2) {
                var root = event.getServer().overworld();
                require(root.getMinBuildHeight() == -512 && root.getMaxBuildHeight() == 1024, "Incorrect Overworld bounds");
                require(root.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noise && noise.getSeaLevel() == 78,
                        "Overworld did not use the adjusted vanilla generator");
                require(root.setBlockAndUpdate(HIGH_MARKER, Blocks.GOLD_BLOCK.defaultBlockState()), "Top block rejected");
                require(!root.setBlockAndUpdate(HIGH_MARKER.above(), Blocks.GOLD_BLOCK.defaultBlockState()), "Top bound not enforced");
                require(root.getBlockState(new BlockPos(552, -512, 8)).is(Blocks.BEDROCK), "Vanilla bottom bedrock missing");
                compareTerrain(root);
                var command = player.createCommandSourceStack().withPermission(4);
                int found = event.getServer().getCommands().performPrefixedCommand(command, "locate structure minecraft:mineshaft");
                require(found > 0, "Vanilla structure missing");
                require(root.getBlockState(new BlockPos(8, 112, 8)).isAir(), "Mouth is blocked");
                require(root.getBlockState(new BlockPos(8, 60, 8)).isAir(), "Entrance shaft below sea level is flooded");
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(552.5, 893, 8.5);
                stage = 3;
                System.out.printf("ABYSS_OVERWORLD_PASS seed=%d spawn=%s vanilla_structure_distance=%d%n",
                        root.getSeed(), root.getSharedSpawnPos(), found);
            } else if (stage == 5) {
                center(player, AbyssLayer.MOUTH_GATE + 9);
                stage = 6;
            } else if (stage == 8) {
                var level = player.serverLevel();
                require(level.dimension() == layer.key && level.getChunkSource().getGenerator() instanceof AbyssChunkGenerator,
                        "Unexpected server layer before verifying " + layer.id + ": " + level.dimension().location() + " " + player.position());
                if (layer == AbyssLayer.FOREST) checkFormation(level, Blocks.JUNGLE_LOG, 7, 14);
                if (layer == AbyssLayer.GOBLETS) checkFormation(level, Blocks.SHROOMLIGHT, 8, 16);
                if (layer == AbyssLayer.SEA) {
                    require(level.getBlockState(new BlockPos(80, 380, 8)).is(Blocks.WATER), "Sea basin missing");
                    require(level.getBlockState(new BlockPos(35, 380, 8)).is(Blocks.CALCITE), "Water containment missing");
                    require(level.getBlockState(new BlockPos(8, 380, 8)).isAir(), "Central descent flooded");
                    System.out.println("ABYSS_SEA_PASS");
                }
                if (layer.next() == null) {
                    center(player, layer.entrance - 9);
                    stage = 10;
                } else {
                    center(player, layer.exit + 9);
                    stage = 9;
                }
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void center(ServerPlayer player, int y) {
        player.teleportTo(AbyssLayer.CENTER_X + 0.5, y, AbyssLayer.CENTER_Z + 0.5);
    }

    private static void compareTerrain(ServerLevel level) {
        var registries = level.registryAccess();
        var settings = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var vanilla = new NoiseBasedChunkGenerator(level.getChunkSource().getGenerator().getBiomeSource(), settings);
        var legacyState = RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), level.getSeed());
        var legacyHeight = LevelHeightAccessor.create(-64, 384);
        var current = level.getChunkSource().getGenerator();
        var currentState = level.getChunkSource().randomState();
        int land = 0, sea = 0, raised = 0, deeper = 0;
        int oldLow = Integer.MAX_VALUE, oldHigh = Integer.MIN_VALUE;
        int newLow = Integer.MAX_VALUE, newHigh = Integer.MIN_VALUE;
        for (int z = -2048; z <= 2048; z += 256) {
            for (int x = -2048; x <= 2048; x += 256) {
                if (AbyssLayer.passage(x, z, 224)) continue;
                var type = Heightmap.Types.OCEAN_FLOOR_WG;
                int oldY = vanilla.getBaseHeight(x, z, type, legacyHeight, legacyState) - 1;
                int newY = current.getBaseHeight(x, z, type, level, currentState) - 1;
                oldLow = Math.min(oldLow, oldY);
                oldHigh = Math.max(oldHigh, oldY);
                newLow = Math.min(newLow, newY);
                newHigh = Math.max(newHigh, newY);
                if (oldY >= vanilla.getSeaLevel() + 3) {
                    land++;
                    if (newY > oldY) raised++;
                } else if (oldY <= vanilla.getSeaLevel() - 12) {
                    sea++;
                    if (current.getSeaLevel() - newY > vanilla.getSeaLevel() - oldY) deeper++;
                }
            }
        }
        require(land > 0 && sea > 0 && raised * 4 >= land * 3 && deeper * 2 >= sea && newHigh - newLow > oldHigh - oldLow,
                "Terrain profile did not raise the land and deepen the sea");
        System.out.printf("ABYSS_RELIEF_PASS land=%d/%d deeper=%d/%d old=%d..%d new=%d..%d%n",
                raised, land, deeper, sea, oldLow, oldHigh, newLow, newHigh);
    }

    private static void checkFormation(ServerLevel level, Block block, int first, int last) {
        int[] count = {0};
        for (int cx = first; cx <= last && count[0] == 0; cx++) {
            for (var section : level.getChunk(cx, 0).getSections()) {
                section.getStates().count((state, amount) -> { if (state.is(block)) count[0] += amount; });
            }
        }
        require(count[0] > 0, "Missing formation " + block);
        System.out.printf("ABYSS_FORMATION_PASS %s blocks=%d%n", level.dimension().location(), count[0]);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private void fail(Throwable failure) {
        stage = -1;
        System.err.println("ABYSS_SMOKE_FAIL");
        failure.printStackTrace();
        Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
    }
}
