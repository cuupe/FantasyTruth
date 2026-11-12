package dev.fantasytruth.smoke;

import com.mojang.serialization.Lifecycle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

@Mod("atlasaltitudesmoke")
public final class AtlasAltitudeSmokeMod {
    private static final boolean BASELINE = Boolean.getBoolean("atlas.altitude.baseline");
    private static final BlockPos UPPER_MARKER = new BlockPos(9, -872, 8);
    private static final BlockPos ROOT_MARKER = new BlockPos(9, 920, 8);
    private static final BlockPos GROUND_MARKER = new BlockPos(-391, 150, 520);
    private static final BlockPos SAVED_MARKER = new BlockPos(9, -583, 8);
    private volatile int stage;
    private volatile List<String> debug = List.of();
    private int ticks;
    private double flightStart;

    public AtlasAltitudeSmokeMod() { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void debug(CustomizeGuiOverlayEvent.DebugText event) {
        debug = List.copyOf(event.getLeft().stream().filter(line -> line != null).toList());
    }

    @SubscribeEvent
    public void client(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage < 0) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks > 2200) throw new AssertionError("Timed out at stage " + stage + " client="
                    + (mc.player == null ? "missing" : mc.level.dimension().location() + " " + mc.player.position())
                    + " screen=" + mc.screen + " xyz=" + xyz());
            if (stage == 0 && mc.screen instanceof TitleScreen title) {
                stage = 1;
                CreateWorldScreen.openFresh(mc, title);
            } else if (stage == 1 && mc.screen instanceof CreateWorldScreen screen) {
                stage = 2;
                var ui = screen.getUiState();
                ui.setName("AtlasAltitudeSmoke");
                ui.setSeed("1");
                ui.setGameMode(SelectedGameMode.CREATIVE);
                ui.setWorldType(ui.getNormalPresetList().stream().filter(entry -> entry.preset() != null
                        && entry.preset().unwrapKey().orElseThrow().location().toString().equals("stratacubes:atlas"))
                        .findFirst().orElseThrow());
                var context = ui.getSettings();
                var dimensions = context.selectedDimensions().bake(context.datapackDimensions());
                var registries = context.worldgenRegistries().replaceFrom(RegistryLayer.DIMENSIONS, dimensions.dimensionsRegistryAccess());
                ObfuscationReflectionHelper.findMethod(CreateWorldScreen.class, "m_245184_", PrimaryLevelData.SpecialWorldProperty.class,
                        LayeredRegistryAccess.class, Lifecycle.class).invoke(screen, dimensions.specialWorldProperty(), registries,
                        registries.compositeAccess().allRegistriesLifecycle());
            } else if (stage == 14 && mc.screen instanceof TitleScreen title) {
                stage = 15;
                mc.createWorldOpenFlows().loadLevel(title, "AtlasAltitudeSmoke");
            } else if (mc.player != null && mc.screen == null) {
                checkClient(mc);
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private void checkClient(Minecraft mc) {
        if (stage == 3 && mc.player.getY() > 885 && mc.level.dimension() == Level.OVERWORLD) {
            if (!hasMarker(mc, ROOT_MARKER)) return;
            mc.options.renderDebug = true;
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
            flightStart = mc.player.getY();
            mc.options.keyJump.setDown(true);
            stage = 4;
        } else if (stage == 4 && page(mc, "p_1")) {
            mc.options.keyJump.setDown(false);
            stage = 5;
        } else if (stage == 5 && hasMarker(mc, UPPER_MARKER) && mc.levelRenderer.isChunkCompiled(UPPER_MARKER)) {
            double globalY = mc.player.getY() + 1792;
            if (globalY < flightStart || globalY > 930 || !mc.player.getAbilities().flying) {
                throw new AssertionError("Flight state changed at seam: " + globalY);
            }
            String xyz = xyz();
            if (xyz.isEmpty() || BASELINE && !xyz.contains(" / -") || !BASELINE && !xyz.matches("XYZ: .* / 9[0-9]{2}\\..*")) return;
            System.out.println("ATLAS_UPPER_STREAM_PASS local=" + mc.player.getY() + " global=" + globalY + " " + xyz);
            mc.options.keyShift.setDown(true);
            stage = 6;
        } else if (stage == 6 && mc.level.dimension() == Level.OVERWORLD) {
            mc.options.keyShift.setDown(false);
            stage = 7;
        } else if (stage == 7 && hasMarker(mc, ROOT_MARKER)) {
            System.out.println("ATLAS_FLIGHT_RETURN_PASS y=" + mc.player.getY());
            stage = 8;
        } else if (stage == 9 && hasMarker(mc, GROUND_MARKER)) {
            if (mc.level.dimension() != Level.OVERWORLD || Math.abs(mc.player.getY() - 151) > 1) {
                throw new AssertionError("Ground teleport restored wrong client position " + mc.player.position());
            }
            System.out.println("ATLAS_GROUND_STREAM_PASS client=" + mc.player.position());
            stage = BASELINE ? -1 : 10;
            if (BASELINE) mc.stop();
        } else if (stage == 11 && page(mc, "p_2") && mc.level.hasChunk(0, 0)) {
            if (Math.abs(mc.player.getY() + 3584 - 3000) > 1) throw new AssertionError("Wrong high-air arrival");
            System.out.println("ATLAS_HIGH_AIR_STREAM_PASS global=" + (mc.player.getY() + 3584));
            stage = 12;
        } else if (stage == 13 && hasMarker(mc, SAVED_MARKER)) {
            stage = 14;
            mc.level.disconnect();
            mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
            mc.setScreen(new TitleScreen());
        } else if (stage == 15 && page(mc, "p_2") && hasMarker(mc, SAVED_MARKER)) {
            if (Math.abs(mc.player.getY() + 3584 - 3000) > 1) throw new AssertionError("Incorrect altitude after reopening");
            System.out.println("ATLAS_RELOGIN_STREAM_PASS global=" + (mc.player.getY() + 3584));
            stage = 16;
        } else if (stage == 17 && mc.level.dimension() == Level.OVERWORLD
                && mc.level.hasChunk(mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4)) {
            var feet = mc.player.blockPosition();
            if (!mc.level.getFluidState(feet).isEmpty() || !mc.level.getFluidState(feet.below()).isEmpty()
                    || mc.level.getBlockState(feet.below()).isAir()) throw new AssertionError("Surface arrival not on dry land");
            System.out.println("ATLAS_DRY_SURFACE_PASS client=" + mc.player.position());
            stage = -1;
            mc.stop();
        }
    }

    @SubscribeEvent
    public void server(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer player = event.getServer().getPlayerList().getPlayers().get(0);
        try {
            if (stage == 2) {
                event.getServer().overworld().setBlockAndUpdate(ROOT_MARKER, Blocks.GOLD_BLOCK.defaultBlockState());
                event.getServer().overworld().setBlockAndUpdate(GROUND_MARKER, Blocks.GOLD_BLOCK.defaultBlockState());
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                run(player, "strata go 8 893 8");
                stage = 3;
            } else if (stage == 8) {
                if (BASELINE) {
                    run(player, "strata go 8 3000 8");
                    run(player, "tp -391.5 151 520.5");
                    if (player.serverLevel().dimension() == Level.OVERWORLD || player.getY() != 151) {
                        throw new AssertionError("Expected old /tp to stay on high page");
                    }
                    System.out.println("ATLAS_OLD_TP_REPRO page=" + player.serverLevel().dimension().location());
                    run(player, "strata go -392 151 520");
                } else {
                    run(player, "tp @s 8.5 3000.25 8.5 30 15");
                    assertAltitude(player, "p_2", 3000.25, 3584);
                    if (player.getYRot() != 30 || player.getXRot() != 15) throw new AssertionError("Lost teleport rotation");
                    run(player, "tp ~ ~-2000 ~");
                    assertAltitude(player, "p_1", 1000.25, 1792);
                    run(player, "tp ~ ~-2000 ~");
                    assertAltitude(player, "m_1", -999.75, -1792);
                    run(player, "tp -391.5 151 520.5");
                    if (player.serverLevel().dimension() != Level.OVERWORLD || player.getY() != 151) {
                        throw new AssertionError("Absolute /tp did not return to the surface page");
                    }
                }
                stage = 9;
            } else if (stage == 10) {
                run(player, "tp 8.5 3000 8.5");
                stage = 11;
            } else if (stage == 12) {
                player.serverLevel().setBlockAndUpdate(SAVED_MARKER, Blocks.GOLD_BLOCK.defaultBlockState());
                stage = 13;
            } else if (stage == 16) {
                run(player, "strata surface");
                stage = 17;
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void run(ServerPlayer player, String command) {
        int result = player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), command);
        if (result != 1) throw new AssertionError("Failed command " + command + " result=" + result);
    }

    private static boolean page(Minecraft mc, String page) {
        return mc.level.dimension().location().toString().equals("stratacubes:atlas_page/" + page);
    }

    private String xyz() {
        return debug.stream().filter(line -> line.startsWith("XYZ:")).findFirst().orElse("");
    }

    private static boolean hasMarker(Minecraft mc, BlockPos pos) {
        return mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && mc.level.getBlockState(pos).is(Blocks.GOLD_BLOCK);
    }

    private static void assertAltitude(ServerPlayer player, String page, double global, long offset) {
        if (!player.serverLevel().dimension().location().toString().equals("stratacubes:atlas_page/" + page)
                || player.getY() + offset != global) {
            throw new AssertionError("Expected " + page + " global Y=" + global + " but got "
                    + player.serverLevel().dimension().location() + " local Y=" + player.getY());
        }
    }

    private void fail(Throwable failure) {
        stage = -1;
        System.err.println("ATLAS_ALTITUDE_FAIL");
        failure.printStackTrace();
        Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
    }
}
