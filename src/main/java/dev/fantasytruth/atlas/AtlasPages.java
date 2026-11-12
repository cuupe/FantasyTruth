package dev.fantasytruth.atlas;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.fantasytruth.terrain.AtlasField;
import dev.fantasytruth.terrain.AtlasSpawn;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Pages are native level views of one absolute-Y terrain, mounted on demand on the server thread. */
public final class AtlasPages {
    public static final AtlasPages INSTANCE = new AtlasPages();
    private record Edit(ServerLevel level, BlockPos pos) {}
    private record Loaded(ServerLevel level, ChunkPos pos) {}
    private record GlobalBlock(int x, long y, int z) {}
    private record Change(BlockState state, boolean owned) {}
    private record Projection(Edit edit, BlockState state) {}
    private record SurfaceLanding(Altitude altitude, int x, int z) {}
    public record InventoryTarget(ServerLevel level, BlockPos pos) {}
    private record TeleportAltitude(Altitude block, double fraction) {}
    private static final ThreadLocal<Projection> PROJECTING = new ThreadLocal<>();
    private static final class Pending {
        final Set<Edit> edits = new LinkedHashSet<>();
        final Set<Loaded> projections = new HashSet<>();
    }

    private static final SimpleCommandExceptionType ATLAS_REQUIRED = new SimpleCommandExceptionType(
            Component.translatable("commands.stratacubes.atlas_required"));
    private static final SimpleCommandExceptionType INVALID_TELEPORT = new SimpleCommandExceptionType(
            Component.translatable("commands.teleport.invalidPosition"));
    private static final int HANDOFF = Altitude.HALF + 8;
    private static final TicketType<ChunkPos> SEAM_TICKET = TicketType.create("stratacubes_seam",
            Comparator.comparingLong(ChunkPos::toLong), 20);
    private static final ChunkProgressListener NO_PROGRESS = new ChunkProgressListener() {
        @Override public void updateSpawnPos(ChunkPos pos) {}
        @Override public void onStatusChange(ChunkPos pos, ChunkStatus status) {}
        @Override public void start() {}
        @Override public void stop() {}
    };

    private final Map<MinecraftServer, Pending> queues = new IdentityHashMap<>();

    private AtlasPages() {}

    @SubscribeEvent
    public void loadContent(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        AtlasChunkGenerator atlas = generator(level);
        if (atlas == null) return;
        try {
            atlas.content(AtlasContent.read(level.getServer().getResourceManager()));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load " + AtlasContent.FILE + " for Atlas", e);
        }
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("strata")
                .then(Commands.literal("altitude").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    AtlasChunkGenerator generator = generator(player.serverLevel());
                    if (generator == null) throw ATLAS_REQUIRED.create();
                    long y = new Altitude(generator.page(), Mth.floor(player.getY())).worldY();
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.stratacubes.altitude", Long.toString(y)), false);
                    return 1;
                }))
                .then(Commands.literal("surface").requires(source -> source.hasPermission(2)).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (generator(player.serverLevel()) == null) throw ATLAS_REQUIRED.create();
                    SurfaceLanding landing = findSurface(player);
                    if (landing == null) {
                        ctx.getSource().sendFailure(Component.translatable("commands.stratacubes.surface_missing"));
                        return 0;
                    }
                    move(player, landing.altitude().page(), landing.x() + 0.5, landing.altitude().localY(),
                            landing.z() + 0.5, player.getYRot(), player.getXRot(), false);
                    player.setDeltaMovement(Vec3.ZERO);
                    player.fallDistance = 0;
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.stratacubes.surface",
                            landing.x(), Long.toString(landing.altitude().worldY()), landing.z()), false);
                    return 1;
                }))
                .then(Commands.literal("locate").then(Commands.argument("landmark", StringArgumentType.string()).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    AtlasChunkGenerator atlas = generator(level);
                    if (atlas == null) throw ATLAS_REQUIRED.create();
                    String id = StringArgumentType.getString(ctx, "landmark");
                    var site = atlas.nearestLandmark(level.getChunkSource().randomState(), id, player.getBlockX(), player.getBlockZ());
                    if (site == null) {
                        ctx.getSource().sendFailure(Component.translatable("commands.stratacubes.landmark_missing", id));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.stratacubes.landmark",
                            site.rule().id(), site.x(), site.y(), site.z()), false);
                    return 1;
                })))
                .then(Commands.literal("go").requires(source -> source.hasPermission(2))
                    .then(Commands.argument("x", IntegerArgumentType.integer(-29_999_984, 29_999_984))
                        .then(Commands.argument("y", LongArgumentType.longArg(-Altitude.LIMIT, Altitude.LIMIT))
                            .then(Commands.argument("z", IntegerArgumentType.integer(-29_999_984, 29_999_984))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    if (generator(player.serverLevel()) == null) throw ATLAS_REQUIRED.create();
                                    long y = LongArgumentType.getLong(ctx, "y");
                                    Altitude target = Altitude.at(y);
                                    move(player, target.page(), IntegerArgumentType.getInteger(ctx, "x") + 0.5,
                                            target.localY(), IntegerArgumentType.getInteger(ctx, "z") + 0.5,
                                            player.getYRot(), player.getXRot(), false);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.stratacubes.altitude",
                                            Long.toString(y)), false);
                                    return 1;
                                }))))));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (generator(server.overworld()) == null) return;
        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        for (ServerPlayer player : players) {
            if (player.isAlive()) keepSeamTicking(player);
        }
        AtlasPageData data = data(server);
        Pending pending = queues.remove(server);
        if (pending != null) flush(server, data, pending);
        for (ServerPlayer player : players) {
            if (!player.isAlive()) continue;
            AtlasChunkGenerator generator = generator(player.serverLevel());
            if (generator != null) {
                int block = Mth.floor(player.getY());
                if (block >= HANDOFF || block < -HANDOFF) {
                    long absolute = new Altitude(generator.page(), block).worldY();
                    if (absolute >= -Altitude.LIMIT && absolute <= Altitude.LIMIT) {
                        Altitude target = Altitude.at(absolute);
                        move(player, target.page(), player.getX(), target.localY() + (player.getY() - block), player.getZ(),
                                player.getYRot(), player.getXRot(), true);
                    }
                }
            }
            remember(player, data);
        }
        transferDrops(server);
    }

    private static void keepSeamTicking(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AtlasChunkGenerator atlas = generator(level);
        if (atlas == null) return;
        int y = Mth.floor(player.getY());
        int direction = y >= Altitude.HALF - 24 ? 1 : y < -Altitude.HALF + 24 ? -1 : 0;
        if (direction == 0) return;
        long boundary = new Altitude(atlas.page(), direction > 0 ? Altitude.HALF : -Altitude.HALF - 1).worldY();
        if (boundary < -Altitude.LIMIT || boundary > Altitude.LIMIT) return;
        ServerLevel other = mount(level.getServer(), atlas.page() + direction);
        ChunkPos center = new ChunkPos(player.blockPosition());
        other.getChunkSource().addRegionTicket(SEAM_TICKET, center, 2, center);
    }

    @SubscribeEvent
    public void onLoadPlayer(PlayerEvent.LoadFromFile event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || generator(player.server.overworld()) == null) return;
        AtlasPageData.PlayerLocation saved = data(player.server).player(player.getUUID());
        // Vanilla resolves the dimension in player NBT just after this event.
        if (saved != null) mount(player.server, saved.page());
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || generator(player.server.getLevel(Level.OVERWORLD)) == null) return;
        AtlasPageData.PlayerLocation last = data(player.server).player(player.getUUID());
        if (last != null && player.serverLevel().dimension() != pageKey(last.page())) {
            move(player, last.page(), last.x(), last.y(), last.z(), last.yaw(), last.pitch(), false);
        }
    }

    @SubscribeEvent
    public void onPageChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && generator(player.serverLevel()) != null) {
            player.onUpdateAbilities();
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && moveForInteraction(player, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onAttack(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (event.getEntity() instanceof ServerPlayer player && moveForInteraction(player, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || generator(player.serverLevel()) == null) return;
        BlockPos target = event.getPos();
        if (event.getItemStack().getItem() instanceof BlockItem && (!player.serverLevel().getBlockState(target).hasBlockEntity()
                || player.isSecondaryUseActive())) {
            target = new BlockPlaceContext(player, event.getHand(), event.getItemStack(), event.getHitVec()).getClickedPos();
        }
        if (moveForInteraction(player, target)) event.setCanceled(true);
    }

    private static boolean moveForInteraction(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        AtlasChunkGenerator atlas = generator(level);
        if (atlas == null || otherPageOffset(pos.getY()) == 0 || ownsBlock(level, pos)) return false;
        if (AtlasPageData.hasStoredContents(level, pos)) return false;
        long owner = Altitude.at(new Altitude(atlas.page(), pos.getY()).worldY()).page();
        double localY = player.getY() + (atlas.page() - owner) * Altitude.STRIDE;
        move(player, owner, player.getX(), localY, player.getZ(), player.getYRot(), player.getXRot(), true);
        return true;
    }

    public static boolean ownsBlock(ServerLevel level, BlockPos pos) {
        return generator(level) == null || pos.getY() >= -Altitude.HALF && pos.getY() < Altitude.HALF;
    }

    /** Resolve a shadow inventory without blocking an inventory tick on new chunk generation. */
    public static InventoryTarget neighboringInventory(ServerLevel level, BlockPos pos) {
        long from = generator(level).page();
        long direction = pos.getY() < -Altitude.HALF ? -1 : 1;
        ServerLevel other = level.getServer().getLevel(pageKey(from + direction));
        if (other == null || !other.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return null;
        return new InventoryTarget(other, pos.offset(0, (int) (-direction * Altitude.STRIDE), 0));
    }

    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState state) {
        Projection projected = PROJECTING.get();
        if (projected != null && projected.edit.level() == level && projected.edit.pos().equals(pos)
                && projected.state == state) return;
        if (generator(level) == null || otherPageOffset(pos.getY()) == 0
                || !level.getServer().isSameThread()) return;
        // A shadow removal must not destroy the authoritative inventory.
        if (!ownsBlock(level, pos) && oldState.hasBlockEntity() && oldState.getBlock() != state.getBlock()) return;
        INSTANCE.pending(level.getServer()).edits.add(new Edit(level, pos.immutable()));
    }

    static boolean projectBlock(ServerLevel level, BlockPos pos, BlockState state) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        Projection previous = PROJECTING.get();
        PROJECTING.set(new Projection(new Edit(level, pos), state));
        try {
            return level.setBlock(pos, state, flags);
        } finally {
            if (previous == null) PROJECTING.remove();
            else PROJECTING.set(previous);
        }
    }

    private static void notifyProjection(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (ownsBlock(level, neighbor) && level.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)) {
                level.neighborChanged(neighbor, state.getBlock(), pos);
            }
        }
        var fluid = level.getFluidState(pos);
        if (ownsBlock(level, pos) && !fluid.isEmpty()) {
            level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || generator(level) == null) return;
        pending(level.getServer()).projections.add(new Loaded(level, event.getChunk().getPos()));
    }

    @SubscribeEvent
    public void beforeSaveOnStop(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        Pending pending = queues.remove(server);
        if (pending != null && generator(server.overworld()) != null) {
            flush(server, data(server), pending);
        }
    }

    @SubscribeEvent
    public void onStop(ServerStoppedEvent event) { queues.remove(event.getServer()); }

    private Pending pending(MinecraftServer server) { return queues.computeIfAbsent(server, key -> new Pending()); }

    private static void flush(MinecraftServer server, AtlasPageData data, Pending pending) {
        Map<GlobalBlock, Change> changes = new LinkedHashMap<>();
        for (Edit edit : pending.edits) {
            long page = generator(edit.level()).page();
            BlockPos pos = edit.pos();
            ChunkPos chunk = new ChunkPos(pos);
            if (!edit.level().hasChunk(chunk.x, chunk.z)) continue;
            BlockState state = edit.level().getBlockState(pos);
            boolean owned = ownsBlock(edit.level(), pos);
            if (!owned && state.hasBlockEntity() && AtlasPageData.hasStoredContents(edit.level(), pos)) continue;
            var key = new GlobalBlock(pos.getX(), new Altitude(page, pos.getY()).worldY(), pos.getZ());
            var previous = changes.get(key);
            if (previous == null || owned || !previous.owned) changes.put(key, new Change(state, owned));
            pending.projections.add(new Loaded(edit.level(), chunk));
            ServerLevel other = server.getLevel(pageKey(page + otherPageOffset(pos.getY())));
            if (other != null) {
                if (Math.abs(Math.abs(pos.getY()) - Altitude.HALF) <= 2) {
                    other.getChunkSource().addRegionTicket(SEAM_TICKET, chunk, 2, chunk);
                }
                pending.projections.add(new Loaded(other, chunk));
            }
        }
        changes.forEach((key, change) -> data.record(key.x, key.y, key.z, change.state));
        // Complete the state copy before running any reactions. A saved unpowered
        // lamp state must not overwrite the neighbor reaction from this batch.
        var notifications = new ArrayList<Edit>();
        for (Loaded loaded : pending.projections) {
            if (loaded.level().hasChunk(loaded.pos().x, loaded.pos().z)) {
                for (BlockPos pos : data.apply(loaded.level(), generator(loaded.level()).page(), loaded.pos())) {
                    notifications.add(new Edit(loaded.level(), pos));
                }
            }
        }
        notifications.forEach(edit -> notifyProjection(edit.level(), edit.pos()));
    }

    private static int otherPageOffset(int y) {
        if (y >= Altitude.STRIDE + Altitude.MIN_LOCAL) return 1;
        if (y < Altitude.MIN_LOCAL + Altitude.HEIGHT - Altitude.STRIDE) return -1;
        return 0;
    }

    private static void remember(ServerPlayer player, AtlasPageData data) {
        AtlasChunkGenerator generator = generator(player.serverLevel());
        AtlasPageData.PlayerLocation location = null;
        if (generator != null && generator.page() != 0) {
            location = new AtlasPageData.PlayerLocation(generator.page(), player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());
        }
        data.player(player.getUUID(), location);
    }

    private static void move(ServerPlayer player, long page, double x, double y, double z, float yaw, float pitch,
                             boolean preserveMotion) {
        MinecraftServer server = player.serverLevel().getServer();
        ServerLevel destination = prepareArrival(server, page, x, z);
        Vec3 velocity = player.getDeltaMovement();
        float fall = player.fallDistance;
        player.teleportTo(destination, x, y, z, Set.of(), yaw, pitch);
        if (preserveMotion) {
            player.setDeltaMovement(velocity);
            player.hurtMarked = true;
            player.fallDistance = fall;
        }
        remember(player, data(server));
    }

    /** Resolve coordinate-based /tp before vanilla checks the page-local bounds. */
    public static ServerLevel teleportLevel(ServerLevel level, CommandSourceStack source, Coordinates coordinates)
            throws CommandSyntaxException {
        AtlasChunkGenerator atlas = generator(level);
        if (atlas == null) return level;
        Vec3 raw = coordinates.getPosition(source);
        if (!Level.isInSpawnableBounds(BlockPos.containing(raw.x, 0, raw.z))) throw INVALID_TELEPORT.create();
        long page = teleportAltitude(atlas, raw.y, coordinates.isYRelative()).block().page();
        return page == atlas.page() ? level : prepareArrival(level.getServer(), page, raw.x, raw.z);
    }

    public static Vec3 teleportLocalPosition(ServerLevel source, Vec3 raw, boolean relative) throws CommandSyntaxException {
        AtlasChunkGenerator atlas = generator(source);
        if (atlas == null) return raw;
        TeleportAltitude altitude = teleportAltitude(atlas, raw.y, relative);
        return new Vec3(raw.x, altitude.block().localY() + altitude.fraction(), raw.z);
    }

    public static Vec3 teleportReportPosition(ServerLevel destination, Vec3 local) {
        AtlasChunkGenerator atlas = generator(destination);
        if (atlas == null || atlas.page() == 0) return local;
        return new Vec3(local.x, new Altitude(atlas.page(), 0).worldY() + local.y, local.z);
    }

    private static TeleportAltitude teleportAltitude(AtlasChunkGenerator atlas, double y, boolean relative)
            throws CommandSyntaxException {
        if (!Double.isFinite(y)) throw INVALID_TELEPORT.create();
        try {
            BigDecimal requested = BigDecimal.valueOf(y);
            BigDecimal block = requested.setScale(0, RoundingMode.FLOOR);
            long origin = relative ? new Altitude(atlas.page(), 0).worldY() : 0;
            Altitude target = Altitude.at(Math.addExact(origin, block.longValueExact()));
            return new TeleportAltitude(target, requested.subtract(block).doubleValue());
        } catch (ArithmeticException | IllegalArgumentException e) {
            throw INVALID_TELEPORT.create();
        }
    }

    private static ServerLevel prepareArrival(MinecraftServer server, long page, double x, double z) {
        ServerLevel destination = mount(server, page);
        ChunkPos chunk = new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
        destination.getChunk(chunk.x, chunk.z);
        data(server).apply(destination, page, chunk).forEach(pos -> notifyProjection(destination, pos));
        return destination;
    }

    private static SurfaceLanding findSurface(ServerPlayer player) {
        ServerLevel root = player.server.overworld();
        AtlasField terrain = generator(root).field(root.getChunkSource().randomState());
        int x = player.getBlockX();
        int z = player.getBlockZ();
        SurfaceLanding current = surfaceAt(root, terrain, x, z);
        if (current != null) return current;
        AtlasSpawn.Site inland = AtlasSpawn.find(terrain, x, z);
        if (inland != null) {
            SurfaceLanding found = surfaceAt(root, terrain, inland.x(), inland.z());
            if (found != null) return found;
        }
        BlockPos spawn = root.getSharedSpawnPos();
        return surfaceAt(root, terrain, spawn.getX(), spawn.getZ());
    }

    private static SurfaceLanding surfaceAt(ServerLevel root, AtlasField terrain, int x, int z) {
        BlockPos column = new BlockPos(x, 0, z);
        if (!Level.isInSpawnableBounds(column) || !root.getWorldBorder().isWithinBounds(column)) return null;
        int ground = terrain.column(x, z).height();
        if (ground <= terrain.profile().seaLevel()) return null;
        Altitude expected = Altitude.at(ground + 1L);
        ServerLevel level = prepareArrival(root.getServer(), expected.page(), x, z);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos head = feet.above();
        BlockPos floor = feet.below();
        if (level.isOutsideBuildHeight(head) || !level.getFluidState(floor).isEmpty()
                || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                || !level.getFluidState(feet).isEmpty() || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getFluidState(head).isEmpty() || !level.getBlockState(head).getCollisionShape(level, head).isEmpty()) return null;
        return new SurfaceLanding(Altitude.at(new Altitude(expected.page(), y).worldY()), x, z);
    }

    private static void transferDrops(MinecraftServer server) {
        var crossing = new ArrayList<Entity>();
        for (ServerLevel level : server.getAllLevels()) {
            if (generator(level) == null) continue;
            for (Entity entity : level.getAllEntities()) {
                if ((entity instanceof ItemEntity || entity instanceof ExperienceOrb) && entity.isAlive()
                        && (entity.getY() >= HANDOFF || entity.getY() < -HANDOFF)) crossing.add(entity);
            }
        }
        for (Entity entity : crossing) {
            if (!(entity.level() instanceof ServerLevel source) || !entity.isAlive()) continue;
            long from = generator(source).page();
            int y = Mth.floor(entity.getY());
            long absolute = new Altitude(from, y).worldY();
            if (absolute < -Altitude.LIMIT || absolute > Altitude.LIMIT) continue;
            Altitude target = Altitude.at(absolute);
            ServerLevel destination = mount(server, target.page());
            ChunkPos chunk = new ChunkPos(entity.blockPosition());
            destination.getChunk(chunk.x, chunk.z);
            data(server).apply(destination, target.page(), chunk).forEach(pos -> notifyProjection(destination, pos));
            Entity copy = entity.getType().create(destination);
            if (copy == null) continue;
            copy.restoreFrom(entity);
            copy.moveTo(entity.getX(), target.localY() + (entity.getY() - y), entity.getZ(), entity.getYRot(), entity.getXRot());
            copy.setDeltaMovement(entity.getDeltaMovement());
            if (destination.addFreshEntity(copy)) entity.discard();
        }
    }

    private static AtlasPageData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(AtlasPageData::load, AtlasPageData::new, "stratacubes_atlas");
    }

    private static AtlasChunkGenerator generator(ServerLevel level) {
        return level != null && level.getChunkSource().getGenerator() instanceof AtlasChunkGenerator generator ? generator : null;
    }

    private static ResourceKey<Level> pageKey(long page) {
        if (page == 0) return Level.OVERWORLD;
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation("stratacubes", "atlas_page/"
                + (page < 0 ? "m_" : "p_") + Math.abs(page)));
    }

    private static ServerLevel mount(MinecraftServer server, long page) {
        ResourceKey<Level> key = pageKey(page);
        ServerLevel loaded = server.getLevel(key);
        if (loaded != null) return loaded;
        ServerLevel root = server.overworld();
        LevelStem stem = new LevelStem(root.dimensionTypeRegistration(), generator(root).forPage(page));
        ServerLevel created = new ServerLevel(server, Util.backgroundExecutor(), ForgeLevels.storage(server),
                new DerivedLevelData(server.getWorldData(), (ServerLevelData) root.getLevelData()), key, stem, NO_PROGRESS,
                false, BiomeManager.obfuscateSeed(root.getSeed()), List.of(), false, root.getRandomSequences());
        created.getWorldBorder().applySettings(root.getWorldBorder().createSettings());
        ForgeLevels.add(server, key, created);
        MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(created));
        return created;
    }

    /** These Forge additions have stable names but are absent from the vanilla development client JAR. */
    private static final class ForgeLevels {
        private static final Field STORAGE = ObfuscationReflectionHelper.findField(MinecraftServer.class, "f_129744_");
        private static final Method MAP = method("forgeGetWorldMap");
        private static final Method DIRTY = method("markWorldsDirty");

        static LevelStorageSource.LevelStorageAccess storage(MinecraftServer server) {
            try {
                return (LevelStorageSource.LevelStorageAccess) STORAGE.get(server);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot access NeoForge world storage", e);
            }
        }

        @SuppressWarnings("unchecked")
        static void add(MinecraftServer server, ResourceKey<Level> key, ServerLevel level) {
            try {
                ((Map<ResourceKey<Level>, ServerLevel>) MAP.invoke(server)).put(key, level);
                DIRTY.invoke(server);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot mount Atlas page", e);
            }
        }

        private static Method method(String name) {
            try {
                return MinecraftServer.class.getMethod(name);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Atlas requires NeoForge 47.1.106", e);
            }
        }
    }
}
