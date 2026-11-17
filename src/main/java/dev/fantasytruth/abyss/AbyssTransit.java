package dev.fantasytruth.abyss;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.extensions.IForgeEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** The surface and the Edge share Y=0. Deeper transitions use their corresponding descent planes. */
public final class AbyssTransit {
    public static final ResourceLocation EXPEDITION = new ResourceLocation("stratacubes", "expedition");
    public static final ResourceLocation ABYSS = new ResourceLocation("stratacubes", "abyss");
    public static final ResourceLocation EDGE_TYPE = new ResourceLocation("stratacubes", "abyss_edge");
    public static final int SURFACE_BUFFER = 8;
    private static final String PLAYER_PERSISTED_DATA = "PlayerPersisted";
    private static final String RETURN_SITE = "FantasyTruthAbyssReturn";
    private static final int PRELOAD_DISTANCE = 180;
    private static final int HOLD_DISTANCE = 38;
    private static final int LAYER_BUFFER = 4;
    private static final TicketType<ChunkPos> PASSAGE = TicketType.create("stratacubes_abyss_passage",
            Comparator.comparingLong(ChunkPos::toLong), 60);
    private record Arrival(ServerLevel level, int settleUntil) {}
    private record Warmup(ServerLevel level, ChunkPos chunk, int refreshed) {}
    private record Approach(ServerLevel level, int started, Double heldVelocity) {}
    private record Shift(ServerLevel source, ServerLevel destination, double x, double y, double z, NaturalAbyssSite entry) {}
    private final Map<ServerPlayer, Arrival> arrivals = new WeakHashMap<>();
    private final Map<ServerPlayer, Warmup> warming = new WeakHashMap<>();
    private final Map<ServerPlayer, Approach> approaches = new WeakHashMap<>();
    private final Map<ServerPlayer, Shift> shifts = new WeakHashMap<>();

    public static boolean expedition(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD && level.dimensionTypeId().location().equals(EXPEDITION);
    }

    private static boolean hasAbyss(ServerLevel level) {
        return expedition(level) || NaturalAbyssSite.distribution(level) != null;
    }

    private static void remember(ServerPlayer player, NaturalAbyssSite site) {
        var data = ((IForgeEntity) player).getPersistentData();
        var persistent = data.getCompound(PLAYER_PERSISTED_DATA);
        persistent.putLong(RETURN_SITE, ChunkPos.asLong(site.offsetX() >> 4, site.offsetZ() >> 4));
        data.put(PLAYER_PERSISTED_DATA, persistent);
    }

    private static NaturalAbyssSite returnSite(ServerPlayer player, ServerLevel surface) {
        var persistent = ((IForgeEntity) player).getPersistentData().getCompound(PLAYER_PERSISTED_DATA);
        if (!persistent.contains(RETURN_SITE, Tag.TAG_LONG)) return null;
        var offset = new ChunkPos(persistent.getLong(RETURN_SITE));
        var site = NaturalAbyssSite.find(surface, offset.getMiddleBlockX(), offset.getMiddleBlockZ());
        return site != null && site.offsetX() == offset.getMinBlockX() && site.offsetZ() == offset.getMinBlockZ() ? site : null;
    }

    private static NaturalAbyssSite commandSite(CommandSourceStack source) {
        var level = source.getLevel();
        if (level.dimension() == Level.OVERWORLD) {
            var pos = source.getPosition();
            return NaturalAbyssSite.find(level, Mth.floor(pos.x), Mth.floor(pos.z));
        }
        return source.getEntity() instanceof ServerPlayer player ? returnSite(player, source.getServer().overworld()) : null;
    }

    private static BlockPos translate(NaturalAbyssSite site, BlockPos pos) {
        return site == null ? pos : site.world(pos);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("abyss")
                .then(Commands.literal("entrances").executes(context -> entrances(context.getSource())))
                .then(Commands.literal("route").executes(context -> route(context.getSource())))
                .then(Commands.literal("forest").executes(context -> forestRoute(context.getSource())))
                .executes(context -> {
            var source = context.getSource();
            AbyssLayer layer = AbyssLayer.of(source.getLevel().dimension());
            if (source.getLevel().getChunkSource().getGenerator() instanceof AbyssEdgeGenerator) {
                int depth = Math.max(0, -Mth.floor(source.getPosition().y));
                source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.edge", depth), false);
            } else if (layer == AbyssLayer.FOREST
                    && source.getLevel().getChunkSource().getGenerator() instanceof AbyssChunkGenerator forest && forest.openForest()) {
                int y = Mth.floor(source.getPosition().y);
                source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.forest_depth",
                        ForestCoordinates.depth(source.getPosition().y, forest.exitY()), y, forest.exitY()), false);
            } else if (layer != null) {
                source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.layer", layer.ordinal() + 1,
                        Component.translatable("abyss.stratacubes." + layer.id), layer.entrance,
                        source.getLevel().getChunkSource().getGenerator() instanceof AbyssChunkGenerator generator
                                ? generator.exitY() : layer.exit), false);
            } else if (NaturalAbyssSite.extended(source.getLevel())) {
                var site = commandSite(source);
                if (site == null) {
                    source.sendFailure(Component.translatable("commands.stratacubes.abyss.locate"));
                    return 0;
                }
                source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.mouth",
                        AbyssLayer.CENTER_X + site.offsetX(), AbyssEdgeGenerator.ENTRANCE,
                        AbyssLayer.CENTER_Z + site.offsetZ()), false);
            } else if (expedition(source.getLevel())) {
                source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.mouth", AbyssLayer.CENTER_X,
                        OrthIsland.vertical(source.getLevel().getChunkSource().getGenerator()) ? AbyssEdgeGenerator.ENTRANCE
                                : AbyssLayer.MOUTH_GATE, AbyssLayer.CENTER_Z), false);
            } else {
                source.sendFailure(Component.translatable("commands.stratacubes.abyss.required"));
                return 0;
            }
            return 1;
        }));
    }

    private static int forestRoute(CommandSourceStack source) {
        var forest = source.getServer().getLevel(AbyssLayer.FOREST.key);
        if (forest == null || !(forest.getChunkSource().getGenerator() instanceof AbyssChunkGenerator generator)
                || !generator.openForest()) {
            source.sendFailure(Component.translatable("commands.stratacubes.abyss.route_required"));
            return 0;
        }
        var terrain = generator.forest(forest.getChunkSource().randomState());
        int millY = terrain.column(ForestTerrain.FIRST_MILL_X, ForestTerrain.FIRST_MILL_Z).shelfTop() + 2;
        int groveY = terrain.column(ForestTerrain.GROVE_X, ForestTerrain.GROVE_Z).floorWater() + 1;
        source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.forest_mill",
                ForestTerrain.FIRST_MILL_X, millY, ForestTerrain.FIRST_MILL_Z), false);
        ForestExpedition.landmarks(source, terrain);
        source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.inverted_grove",
                ForestTerrain.GROVE_X, groveY, ForestTerrain.GROVE_Z), false);
        source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.forest_landing",
                ForestTerrain.CAMP_X - 45, ForestTerrain.CAMP_LANDING + 1, ForestTerrain.CAMP_Z + 6), false);
        source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.seeker_camp",
                ForestTerrain.CAMP_X, ForestTerrain.CAMP_Y + 1, ForestTerrain.CAMP_Z), false);
        source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.forest_exit",
                AbyssLayer.CENTER_X, generator.exitY(), AbyssLayer.CENTER_Z), false);
        return 1;
    }

    private static int entrances(CommandSourceStack source) {
        var server = source.getServer();
        var edge = server.getLevel(AbyssLayer.EDGE.key);
        var site = commandSite(source);
        if (site == null && NaturalAbyssSite.extended(server.overworld())) {
            source.sendFailure(Component.translatable("commands.stratacubes.abyss.locate"));
            return 0;
        }
        if ((site == null && !OrthIsland.vertical(server.overworld().getChunkSource().getGenerator())) || edge == null
                || !(edge.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator generator) || !generator.mossPit()) {
            source.sendFailure(Component.translatable("commands.stratacubes.abyss.route_required"));
            return 0;
        }
        entrance(source, "gate", translate(site, OrthDescent.gate().entrance()));
        entrance(source, "wharf", translate(site, OrthDescent.wharf().entrance()));
        entrance(source, "pier", translate(site, OrthDescent.pierBell()));
        return 1;
    }

    private static void entrance(CommandSourceStack source, String id, net.minecraft.core.BlockPos position) {
        source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.entrance." + id,
                position.getX(), position.getY(), position.getZ()), false);
    }

    @SubscribeEvent
    public void useCable(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !player.serverLevel().getBlockState(event.getPos()).is(Blocks.BELL)) return;
        ServerLevel level = player.serverLevel();
        ServerLevel surface = level.getServer().overworld();
        ServerLevel edge = level.getServer().getLevel(AbyssLayer.EDGE.key);
        if (edge == null || !(edge.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator generator)
                || !generator.mossPit() || level != surface && level != edge) return;
        var site = level == surface ? NaturalAbyssSite.find(surface, event.getPos().getX(), event.getPos().getZ())
                : returnSite(player, surface);
        if (site == null && !OrthIsland.vertical(surface.getChunkSource().getGenerator())) return;
        var pier = translate(site, OrthDescent.pierBell());
        boolean down = level == surface && event.getPos().equals(pier);
        if (!down && (level != edge || !event.getPos().equals(OrthDescent.arkBell()))) return;
        var stop = down ? OrthDescent.arkBell() : pier;
        ServerLevel destination = down ? edge : surface;
        destination.getChunk(stop);
        var arrival = stop.east();
        if (!destination.getBlockState(stop).is(Blocks.BELL) || !destination.isEmptyBlock(arrival)
                || !destination.isEmptyBlock(arrival.above()) || destination.isEmptyBlock(arrival.below())) {
            player.displayClientMessage(Component.translatable("commands.stratacubes.abyss.cable_closed"), true);
            return;
        }
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        event.setCanceled(true);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        if (down && site != null) remember(player, site);
        move(player, destination, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, 12);
        player.displayClientMessage(Component.translatable("commands.stratacubes.abyss.cable_arrival." + (down ? "ark" : "pier")), true);
    }

    private static int route(CommandSourceStack source) {
        ServerLevel edge = source.getServer().getLevel(AbyssLayer.EDGE.key);
        boolean inExpedition = hasAbyss(source.getLevel()) || source.getLevel() == edge;
        if (!inExpedition || edge == null
                || !(edge.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator generator) || !generator.expanded()) {
            source.sendFailure(Component.translatable("commands.stratacubes.abyss.route_required"));
            return 0;
        }
        int entryDepth = generator.wildEdge() ? 53 : 18;
        var entrance = EdgeLandscape.point(entryDepth, EdgeLandscape.TRAIL_RADIUS);
        var trail = EdgeLandscape.at(entrance.x(), entrance.z(), entryDepth, generator.layout());
        int startY = trail == null ? -17 : trail.top() + 1;
        source.sendSuccess(() -> Component.translatable(generator.wildEdge() ? "commands.stratacubes.abyss.route_landing"
                : "commands.stratacubes.abyss.route_start",
                entrance.x(), startY, entrance.z()), false);
        for (var landmark : EdgeExploration.landmarks(generator.layout())) {
            var point = landmark.kind() == EdgeExploration.Kind.CAVE ? EdgeLandscape.point(landmark.depth(), 413) : landmark.point();
            var span = EdgeLandscape.at(point.x(), point.z(), landmark.depth(), generator.layout());
            if (span == null) continue;
            int y = landmark.kind() == EdgeExploration.Kind.CAVE ? -landmark.depth() : span.top() + 1;
            source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.route_point",
                    Component.translatable("edge.stratacubes." + landmark.name()), point.x(), y, point.z()), false);
        }
        var exit = generator.mossPit() ? new EdgeLandscape.Point(AbyssLayer.CENTER_X, AbyssLayer.CENTER_Z)
                : generator.forested() ? EdgeBasin.point(14) : EdgeLandscape.point(EdgeLandscape.END, 14);
        source.sendSuccess(() -> Component.translatable(generator.mossPit() ? "commands.stratacubes.abyss.route_pit"
                : "commands.stratacubes.abyss.route_exit",
                exit.x(), AbyssEdgeGenerator.EXIT, exit.z()), false);
        return 1;
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        ServerLevel current = player.serverLevel();
        AbyssLayer layer = AbyssLayer.of(current.dimension());
        boolean surface = expedition(current);
        var site = Math.abs(player.getY()) <= PRELOAD_DISTANCE + HOLD_DISTANCE
                ? NaturalAbyssSite.find(current, player.getBlockX(), player.getBlockZ()) : null;
        boolean shaft = site != null || surface && OrthIsland.vertical(current.getChunkSource().getGenerator());
        boolean edge = current.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator;
        if (shaft || edge) {
            if (current.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator generator && generator.eroded()) {
                EdgeFauna.tick(player, current, generator);
            }
            tickEdge(player, current, shaft, site);
            return;
        }
        double y = player.getY();
        if (layer == AbyssLayer.FOREST && current.getChunkSource().getGenerator() instanceof AbyssChunkGenerator forest
                && forest.openForest() && player.isAlive() && !player.isPassenger() && !player.isSpectator()) {
            ForestGrowth.tickCurrent(player);
            ForestExpedition.tick(player);
            ForestFauna.tick(player, current, forest);
        }
        int trackingRadius = AbyssLayer.PASSAGE_RADIUS + 24;
        if (layer == AbyssLayer.FOREST || layer == AbyssLayer.FAULT) {
            var forest = current.getServer().getLevel(AbyssLayer.FOREST.key);
            if (forest != null && forest.getChunkSource().getGenerator() instanceof AbyssChunkGenerator open
                    && open.openForest()) trackingRadius = ForestMouth.OUTLET_TRACKING_RADIUS;
        }
        if (layer == AbyssLayer.FOREST && Math.abs(y - layer.entrance) <= PRELOAD_DISTANCE) {
            var above = current.getServer().getLevel(AbyssLayer.EDGE.key);
            if (above != null && above.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator upper && upper.mossPit()) {
                trackingRadius = entranceRadius(upper);
            }
        }
        if ((!surface && layer == null) || !player.isAlive() || player.isPassenger() || player.isSleeping()
                || !AbyssLayer.passage(player.getX(), player.getZ(), trackingRadius)) {
            approaches.remove(player);
            return;
        }
        if (surface) {
            if (Math.abs(y - AbyssLayer.MOUTH_GATE) <= PRELOAD_DISTANCE) {
                pass(player, current.getServer().getLevel(AbyssLayer.EDGE.key), AbyssLayer.MOUTH_GATE,
                        AbyssLayer.EDGE.entrance, true, LAYER_BUFFER, false);
            }
            return;
        }
        if (!hasAbyss(current.getServer().overworld())
                || !(current.getChunkSource().getGenerator() instanceof AbyssChunkGenerator generator) || generator.layer() != layer) return;
        if (Math.abs(y - layer.entrance) <= PRELOAD_DISTANCE) {
            AbyssLayer upper = layer.previous();
            ServerLevel destination = upper == null ? current.getServer().overworld() : current.getServer().getLevel(upper.key);
            if (destination == null) return;
            int destinationY = upper == null ? AbyssLayer.MOUTH_GATE : upper.exit;
            if (destination.getChunkSource().getGenerator() instanceof AbyssChunkGenerator upperGenerator) {
                destinationY = upperGenerator.exitY();
            } else if (destination.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator) {
                destinationY = AbyssEdgeGenerator.EXIT;
            }
            pass(player, destination, layer.entrance, destinationY, false, LAYER_BUFFER, false);
        } else if (Math.abs(y - generator.exitY()) <= PRELOAD_DISTANCE && layer.next() != null) {
            AbyssLayer lower = layer.next();
            pass(player, current.getServer().getLevel(lower.key), generator.exitY(), lower.entrance, true, LAYER_BUFFER, false);
        } else {
            approaches.remove(player);
        }
    }

    private void tickEdge(ServerPlayer player, ServerLevel current, boolean surface, NaturalAbyssSite site) {
        boolean inShaft = site == null ? OrthIsland.inShaft(player.getX(), player.getZ())
                : site.inShaft(player.getX(), player.getZ());
        if (!player.isAlive() || player.isPassenger() || player.isSleeping() || !inShaft) {
            approaches.remove(player);
            return;
        }
        double y = player.getY();
        if (Math.abs(y) <= PRELOAD_DISTANCE) {
            ServerLevel adjacent = surface ? current.getServer().getLevel(AbyssLayer.EDGE.key) : current.getServer().overworld();
            var anchor = surface ? site : returnSite(player, adjacent);
            if (surface ? adjacent != null && adjacent.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator
                    : anchor != null || OrthIsland.vertical(adjacent.getChunkSource().getGenerator())) {
                int direction = surface ? -1 : 1;
                int dx = anchor == null ? 0 : direction * anchor.offsetX();
                int dz = anchor == null ? 0 : direction * anchor.offsetZ();
                pass(player, adjacent, 0, 0, surface, SURFACE_BUFFER, true, dx, dz, surface ? anchor : null);
            }
        } else if (!surface && Math.abs(y - AbyssEdgeGenerator.EXIT) <= PRELOAD_DISTANCE
                && current.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator edge
                && AbyssLayer.passage(player.getX(), player.getZ(), entranceRadius(edge))) {
            pass(player, current.getServer().getLevel(AbyssLayer.FOREST.key), AbyssEdgeGenerator.EXIT,
                    AbyssLayer.FOREST.entrance, true, LAYER_BUFFER, false);
        } else {
            approaches.remove(player);
        }
    }

    private static int entranceRadius(AbyssEdgeGenerator edge) {
        if (edge.forestMouth()) return ForestMouth.TRACKING_RADIUS;
        return edge.mossPit() ? EdgeBasin.APPROACH_RADIUS : AbyssLayer.PASSAGE_RADIUS + 24;
    }

    private ChunkPos warm(ServerPlayer player, ServerLevel destination, int tick, int dx, int dz) {
        ChunkPos center = new ChunkPos(player.blockPosition().offset(dx, 0, dz));
        Warmup previous = warming.get(player);
        if (previous == null || previous.level != destination || !previous.chunk.equals(center) || tick - previous.refreshed >= 24) {
            destination.getChunkSource().addRegionTicket(PASSAGE, center, 2, center);
            warming.put(player, new Warmup(destination, center, tick));
        }
        return center;
    }

    private static boolean loaded(ServerLevel level, ChunkPos center, int radius) {
        var chunks = level.getChunkSource();
        for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
            if (chunks.getChunkNow(center.x + dx, center.z + dz) == null) return false;
        }
        return true;
    }

    private static boolean inOpening(ServerPlayer player, ServerLevel destination) {
        var source = player.serverLevel();
        var block = player.blockPosition();
        int x = OrthIsland.localX(block.getX()), z = OrthIsland.localZ(block.getZ());
        if ((source.dimension() == AbyssLayer.FOREST.key && destination.dimension() == AbyssLayer.FAULT.key)
                || (source.dimension() == AbyssLayer.FAULT.key && destination.dimension() == AbyssLayer.FOREST.key)) {
            var forest = source.dimension() == AbyssLayer.FOREST.key ? source : destination;
            if (forest.getChunkSource().getGenerator() instanceof AbyssChunkGenerator generator && generator.openForest()) {
                return ForestMouth.outlet(x, z);
            }
        }
        if (source.dimension() == AbyssLayer.FOREST.key || destination.dimension() == AbyssLayer.FOREST.key) {
            var edge = source.dimension() == AbyssLayer.EDGE.key ? source : destination;
            if (edge.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator generator) {
                if (generator.forestMouth()) return ForestMouth.opening(x, z);
                if (generator.mossPit()) return EdgeBasin.hole(x, z);
            }
        }
        return AbyssLayer.passage(player.getX(), player.getZ(), AbyssLayer.PASSAGE_RADIUS);
    }

    private void pass(ServerPlayer player, ServerLevel destination, int fromY, int toY, boolean descending,
                      int buffer, boolean sharedHeight) {
        pass(player, destination, fromY, toY, descending, buffer, sharedHeight, 0, 0, null);
    }

    private void pass(ServerPlayer player, ServerLevel destination, int fromY, int toY, boolean descending,
                      int buffer, boolean sharedHeight, int dx, int dz, NaturalAbyssSite entry) {
        var source = player.serverLevel();
        if (destination == null || destination == source || (!sharedHeight && !inOpening(player, destination))) {
            approaches.remove(player);
            return;
        }
        int now = source.getServer().getTickCount();
        int direction = descending ? -1 : 1;
        double overshoot = player.getY() - fromY;
        double crossed = overshoot * direction;
        var recent = arrivals.get(player);
        if (recent != null && recent.level == source && now < recent.settleUntil) return;
        var center = warm(player, destination, now, dx, dz);
        if (crossed < buffer) {
            approaches.remove(player);
            return;
        }
        var approach = approaches.get(player);
        if (approach == null || approach.level != destination) {
            approach = new Approach(destination, now, null);
            approaches.put(player, approach);
        }
        boolean ready = loaded(destination, center, 1);
        // Slow storage can delay the ring even after the central chunk arrived. Bound the wait.
        if (!ready && now - approach.started >= 45) ready = loaded(destination, center, 0);
        if (!ready) {
            if (crossed >= HOLD_DISTANCE && player.getDeltaMovement().y * direction > 0) {
                var motion = player.getDeltaMovement();
                if (approach.heldVelocity == null)
                    approaches.put(player, new Approach(destination, approach.started, motion.y));
                player.setDeltaMovement(motion.x, 0, motion.z);
                player.hurtMarked = true;
            }
            return;
        }
        double arrival = sharedHeight ? player.getY() : toY + direction * Math.min(crossed, 44);
        if (!canArrive(player, destination, arrival, dx, dz)) {
            if (sharedHeight) return;
            // Stay on the same line through the aperture. Never move the player sideways into a different tunnel.
            arrival = toY + direction * buffer;
            if (!canArrive(player, destination, arrival, dx, dz)) return;
        }
        if (approach.heldVelocity != null && player.getDeltaMovement().y * direction >= 0) {
            var motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, approach.heldVelocity, motion.z);
        }
        approaches.remove(player);
        if (dx != 0 || dz != 0) {
            // The connection restores the pre-tick coordinates after ServerPlayer.doTick().
            shifts.put(player, new Shift(source, destination, player.getX() + dx, arrival, player.getZ() + dz, entry));
        } else {
            if (entry != null) remember(player, entry);
            move(player, destination, player.getX(), arrival, player.getZ(), 8);
        }
    }

    @SubscribeEvent
    public void finishShifts(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || shifts.isEmpty()) return;
        var queue = shifts.entrySet().iterator();
        while (queue.hasNext()) {
            var next = queue.next();
            var player = next.getKey();
            var shift = next.getValue();
            if (shift.source.getServer() != event.getServer()) continue;
            queue.remove();
            if (player.serverLevel() != shift.source || !player.isAlive() || player.hasDisconnected()) continue;
            move(player, shift.destination, shift.x, shift.y, shift.z, 8);
            if (shift.entry != null && player.serverLevel() == shift.destination) remember(player, shift.entry);
        }
    }

    private static boolean canArrive(ServerPlayer player, ServerLevel destination, double y, int dx, int dz) {
        var box = player.getBoundingBox().move(dx, y - player.getY(), dz);
        var min = BlockPos.containing(box.minX - 1, box.minY, box.minZ - 1);
        var max = BlockPos.containing(box.maxX + 1, box.maxY, box.maxZ + 1);
        return !destination.isOutsideBuildHeight(min) && !destination.isOutsideBuildHeight(max)
                && destination.hasChunksAt(min, max) && destination.noCollision(player, box);
    }

    private void move(ServerPlayer player, ServerLevel destination, double x, double y, double z, int settleTicks) {
        ChunkPos center = new ChunkPos(net.minecraft.core.BlockPos.containing(x, y, z));
        destination.getChunk(center.x, center.z);
        var motion = player.getDeltaMovement();
        float fall = player.fallDistance;
        player.teleportTo(destination, x, y, z, Set.of(), player.getYRot(), player.getXRot());
        if (player.serverLevel() != destination) return;
        arrivals.put(player, new Arrival(destination, destination.getServer().getTickCount() + settleTicks));
        player.onUpdateAbilities();
        // Teleport sends a respawn packet, which recreates the local player without its active effects.
        for (var effect : player.getActiveEffects()) {
            player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), effect));
        }
        player.setDeltaMovement(motion);
        player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), motion));
        player.hurtMarked = true;
        player.fallDistance = fall;
    }
}
