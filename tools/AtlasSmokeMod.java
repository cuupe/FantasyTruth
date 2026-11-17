package dev.fantasytruth.smoke;

import com.mojang.serialization.Lifecycle;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.WorldTypeEntry;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.RegistryLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Fixture for a separate game directory. This class is never included in the production mod. */
@Mod("atlassmoke")
public final class AtlasSmokeMod {
    private static final long HIGH = 1_000_000_000_000L;
    private static final String WORLD = System.getProperty("atlas.smoke.world", "AtlasSmoke");
    private static final boolean REOPEN = Boolean.getBoolean("atlas.smoke.reopen");
    private static final boolean CONTENT = Boolean.getBoolean("atlas.smoke.content");
    private static final boolean MANUAL = Boolean.getBoolean("atlas.smoke.manual");
    private int creation;
    private int stage;
    private int ticks;
    private volatile int clientPage;
    private UUID dropId;
    private net.minecraft.server.level.ServerLevel upperPage;

    public AtlasSmokeMod() { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent
    public void client(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension().location().getPath().startsWith("atlas_page/")) clientPage++;
        try {
            if (creation == 0 && mc.screen instanceof TitleScreen title) {
                creation++;
                System.out.println("ATLAS_SMOKE_TITLE");
                if (REOPEN) {
                    ObfuscationReflectionHelper.findMethod(WorldOpenFlows.class, "m_233145_", Screen.class, String.class,
                            boolean.class, boolean.class).invoke(mc.createWorldOpenFlows(), title, WORLD, false, false);
                } else {
                    CreateWorldScreen.openFresh(mc, title);
                }
            } else if (creation == 1 && mc.screen instanceof CreateWorldScreen create) {
                creation++;
                var ui = create.getUiState();
                ui.setName(WORLD);
                ui.setSeed("72019");
                ui.setGameMode(SelectedGameMode.CREATIVE);
                ui.setAllowCheats(true);
                var preset = ResourceKey.create(Registries.WORLD_PRESET, new ResourceLocation("stratacubes", "atlas"));
                var registry = ui.getSettings().worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET);
                ui.setWorldType(new WorldTypeEntry(registry.getHolderOrThrow(preset)));
                var context = ui.getSettings();
                var dimensions = context.selectedDimensions().bake(context.datapackDimensions());
                var registries = context.worldgenRegistries().replaceFrom(RegistryLayer.DIMENSIONS, dimensions.dimensionsRegistryAccess());
                System.out.println("ATLAS_SMOKE_PRESET_READY");
                ObfuscationReflectionHelper.findMethod(CreateWorldScreen.class, "m_245184_", PrimaryLevelData.SpecialWorldProperty.class,
                        LayeredRegistryAccess.class, Lifecycle.class).invoke(create, dimensions.specialWorldProperty(), registries,
                        registries.compositeAccess().allRegistriesLifecycle());
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    @SubscribeEvent
    public void server(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage < 0 || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        if (++ticks < 35) return;
        ticks = 0;
        ServerPlayer player = event.getServer().getPlayerList().getPlayers().get(0);
        try {
            System.out.println("ATLAS_SMOKE_STEP " + stage + " " + player.serverLevel().dimension().location() + " y=" + player.getY());
            if (REOPEN) {
                require(page(player, "p_"), "restored native page");
                require(player.serverLevel().getBlockState(player.blockPosition().offset(1, -1, 0)).is(Blocks.DIAMOND_BLOCK),
                        "restored block in distant page");
                require(clientPage > 0, "client received restored page");
                if (MANUAL) {
                    var pos = new BlockPos(1025, 527, 1025);
                    require(player.serverLevel().getBlockEntity(pos) instanceof ChestBlockEntity, "on-disk datapack generated a fixed landmark");
                    var manual = (ChestBlockEntity) player.serverLevel().getBlockEntity(pos);
                    int emeralds = 0;
                    for (int i = 0; i < manual.getContainerSize(); i++) {
                        if (manual.getItem(i).is(Items.EMERALD)) emeralds += manual.getItem(i).getCount();
                    }
                    require(emeralds == 11, "datapack table produced the configured amount of loot");
                    System.out.println("ATLAS_SMOKE_DATAPACK_PASS");
                }
                System.out.println("ATLAS_SMOKE_REOPEN_PASS");
                stop();
                return;
            }
            switch (stage++) {
                case 0 -> {
                    require(player.serverLevel().dimension() == Level.OVERWORLD, "started in overworld");
                    require(player.serverLevel().getBiome(player.blockPosition()).unwrapKey().orElseThrow()
                            .location().getNamespace().equals("stratacubes"), "custom biome generated");
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    go(player, 895);
                    var root = player.serverLevel();
                    root.setBlock(new BlockPos(9, 900, 8), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                    root.setBlockAndUpdate(new BlockPos(15, 902, 14), Blocks.WATER.defaultBlockState());
                    if (CONTENT) {
                        BlockPos storage = new BlockPos(10, 894, 8);
                        root.setBlockAndUpdate(storage, Blocks.CHEST.defaultBlockState());
                        ((ChestBlockEntity) root.getBlockEntity(storage)).setItem(0, new ItemStack(Items.IRON_INGOT, 9));
                        var drop = new ItemEntity(root, 8.5, 930, 8.5, new ItemStack(Items.EMERALD, 7));
                        drop.setDeltaMovement(Vec3.ZERO);
                        drop.setNoGravity(true);
                        dropId = drop.getUUID();
                        require(root.addFreshEntity(drop), "seeded crossing item");
                        require(root.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),
                                "strata locate any") == 1, "locate command found a generated landmark");
                        verifyDeposit(root);
                    }
                }
                case 1 -> {
                    player.setDeltaMovement(Vec3.ZERO);
                    player.teleportTo(8.5, 966.25, 8.5);
                }
                case 2 -> {
                    require(page(player, "p_1"), "automatic positive seam crossing");
                    upperPage = player.serverLevel();
                    require(player.serverLevel().getBlockState(new BlockPos(9, -892, 8)).is(Blocks.REDSTONE_BLOCK),
                            "mirrored block state without neighbor notification");
                    require(upperPage.getFluidState(new BlockPos(15, -895, 14)).is(FluidTags.WATER), "water reached the upper-page side of the seam");
                    require(upperPage.getServer().overworld().getFluidState(new BlockPos(15, 895, 14)).is(FluidTags.WATER),
                            "water continued across the seam");
                    if (CONTENT) verifyContents(player);
                    upperPage.setBlock(new BlockPos(13, -896, 10), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_CLIENTS);
                    var root = upperPage.getServer().overworld();
                    root.setBlock(new BlockPos(13, 895, 10), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                    root.setBlockAndUpdate(new BlockPos(12, 895, 11), Blocks.CHEST.defaultBlockState());
                    var hopper = new BlockPos(12, -896, 11);
                    upperPage.setBlockAndUpdate(hopper, Blocks.HOPPER.defaultBlockState());
                    ((HopperBlockEntity) upperPage.getBlockEntity(hopper)).setItem(0, new ItemStack(Items.COAL, 3));
                }
                case 3 -> {
                    require(upperPage.getBlockState(new BlockPos(13, -896, 10)).getValue(RedstoneLampBlock.LIT),
                            "projected power triggered the owning page's lamp");
                    upperPage.getServer().overworld().setBlock(new BlockPos(13, 895, 10), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                case 4 -> {
                    require(!upperPage.getBlockState(new BlockPos(13, -896, 10)).getValue(RedstoneLampBlock.LIT),
                            "projected power removal scheduled lamp shutoff");
                    var output = (ChestBlockEntity) upperPage.getServer().overworld().getBlockEntity(new BlockPos(12, 895, 11));
                    var ghost = (ChestBlockEntity) upperPage.getBlockEntity(new BlockPos(12, -897, 11));
                    require(output.getItem(0).is(Items.COAL) && output.getItem(0).getCount() == 3 && ghost.isEmpty(),
                            "hopper transferred all items once into the neighboring owner");
                    System.out.println("ATLAS_SMOKE_NEIGHBORS_PASS");
                    go(player, -HIGH);
                }
                case 5 -> {
                    require(page(player, "m_"), "mounted distant negative page");
                    go(player, HIGH);
                }
                case 6 -> {
                    require(page(player, "p_"), "mounted distant positive page");
                    require(clientPage > 0, "client received an Atlas page");
                    if (CONTENT) {
                        require(player.serverLevel().getBlockEntity(new BlockPos(49, 527, 1)) instanceof ChestBlockEntity,
                                "fixed tower generated at global Y +10^12");
                    }
                    player.serverLevel().setBlock(player.blockPosition().offset(1, -1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                    event.getServer().saveEverything(true, true, true);
                    System.out.println("ATLAS_SMOKE_CREATE_PASS");
                    stop();
                }
                default -> throw new IllegalStateException("Unexpected smoke stage");
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void verifyDeposit(net.minecraft.server.level.ServerLevel root) {
        for (int y = -48; y <= -20; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (root.getBlockState(new BlockPos(x, y, z)).is(Blocks.AMETHYST_BLOCK)) return;
                }
            }
        }
        throw new AssertionError("custom deposit was not generated");
    }

    private void verifyContents(ServerPlayer player) {
        var upper = player.serverLevel();
        var root = upper.getServer().overworld();
        require(upper.getEntity(dropId) instanceof ItemEntity drop && drop.getItem().is(Items.EMERALD)
                && drop.getItem().getCount() == 7 && root.getEntity(dropId) == null, "single intact item stack crossed the seam");

        var tower = new BlockPos(33, -882, 1);
        var shadowTower = new BlockPos(33, 910, 1);
        require(upper.getBlockEntity(tower) instanceof ChestBlockEntity generated
                && generated.saveWithFullMetadata().getString("LootTable").equals("stratacubes:chests/watchtower"),
                "fixed watchtower generated loot on owning page");
        var generated = (ChestBlockEntity) upper.getBlockEntity(tower);
        require(!generated.isEmpty() && !generated.saveWithFullMetadata().contains("LootTable"), "real chest resolved loot table");
        require(root.getBlockEntity(shadowTower) instanceof ChestBlockEntity shadow && shadow.isEmpty(),
                "projection contains no duplicate structure loot");

        BlockPos storage = new BlockPos(10, -898, 8);
        require(upper.getBlockEntity(storage) instanceof ChestBlockEntity projected && projected.isEmpty(), "inventory is owned once");
        player.teleportTo(8.5, -894.0, 8.5);
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, storage,
                new BlockHitResult(Vec3.atCenterOf(storage), Direction.UP, storage, false));
        MinecraftForge.EVENT_BUS.post(event);
        require(event.isCanceled() && player.serverLevel() == root, "chest use redirects to the owning page");
        require(root.getBlockEntity(new BlockPos(10, 894, 8)) instanceof ChestBlockEntity original
                && original.getItem(0).is(Items.IRON_INGOT) && original.getItem(0).getCount() == 9, "owner inventory retained");
        System.out.println("ATLAS_SMOKE_CONTENT_PASS");
    }

    private static boolean page(ServerPlayer player, String suffix) {
        return player.serverLevel().dimension().location().getPath().startsWith("atlas_page/" + suffix);
    }

    private static void go(ServerPlayer player, long y) {
        int result = player.serverLevel().getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4), "strata go 8 " + y + " 8");
        require(result == 1, "absolute altitude command " + y);
    }

    private static void require(boolean ok, String check) {
        if (!ok) throw new AssertionError(check);
    }

    private void fail(Throwable failure) {
        System.err.println("ATLAS_SMOKE_FAIL stage=" + stage + " creation=" + creation);
        failure.printStackTrace();
        stop();
    }

    private void stop() {
        stage = -1;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(mc::stop);
    }
}
