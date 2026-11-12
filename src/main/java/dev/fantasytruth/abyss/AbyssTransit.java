package dev.fantasytruth.abyss;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** The surface and the Edge share Y=0. Deeper transitions use their corresponding descent planes. */
public final class AbyssTransit {
    public static final ResourceLocation EXPEDITION = new ResourceLocation("stratacubes", "expedition");
    public static final ResourceLocation ABYSS = new ResourceLocation("stratacubes", "abyss");
    public static final ResourceLocation EDGE_TYPE = new ResourceLocation("stratacubes", "abyss_edge");
    private static final TicketType<ChunkPos> PASSAGE = TicketType.create("stratacubes_abyss_passage",
            Comparator.comparingLong(ChunkPos::toLong), 40);
    private record Position(ServerLevel level, double y, int settleUntil) {}
    private record Warmup(ServerLevel level, ChunkPos chunk, int tick) {}
    private final Map<ServerPlayer, Position> last = new WeakHashMap<>();
    private final Map<ServerPlayer, Warmup> warming = new WeakHashMap<>();

    public static boolean expedition(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD && level.dimensionTypeId().location().equals(EXPEDITION);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("abyss")
                .then(Commands.literal("route").executes(context -> route(context.getSource())))
                .executes(context -> {
            var source = context.getSource();
            AbyssLayer layer = AbyssLayer.of(source.getLevel().dimension());
            if (source.getLevel().getChunkSource().getGenerator() instanceof AbyssEdgeGenerator) {
                int depth = Math.max(0, -Mth.floor(source.getPosition().y));
                source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.edge", depth), false);
            } else if (layer != null) {
                source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.layer", layer.ordinal() + 1,
                        Component.translatable("abyss.stratacubes." + layer.id), layer.entrance, layer.exit), false);
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

    private static int route(CommandSourceStack source) {
        ServerLevel edge = source.getServer().getLevel(AbyssLayer.EDGE.key);
        boolean inExpedition = expedition(source.getLevel()) || source.getLevel() == edge;
        if (!inExpedition || edge == null
                || !(edge.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator generator) || !generator.expanded()) {
            source.sendFailure(Component.translatable("commands.stratacubes.abyss.route_required"));
            return 0;
        }
        var entrance = EdgeLandscape.point(18, EdgeLandscape.TRAIL_RADIUS);
        var trail = EdgeLandscape.at(entrance.x(), entrance.z(), 18, generator.forested());
        int startY = trail == null ? -17 : trail.top() + 1;
        source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.route_start",
                entrance.x(), startY, entrance.z()), false);
        for (var landmark : EdgeExploration.LANDMARKS) {
            var point = landmark.kind() == EdgeExploration.Kind.CAVE ? EdgeLandscape.point(landmark.depth(), 413) : landmark.point();
            var span = EdgeLandscape.at(point.x(), point.z(), landmark.depth(), generator.forested());
            if (span == null) continue;
            int y = landmark.kind() == EdgeExploration.Kind.CAVE ? -landmark.depth() : span.top() + 1;
            source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.route_point",
                    Component.translatable("edge.stratacubes." + landmark.name()), point.x(), y, point.z()), false);
        }
        var exit = generator.forested() ? EdgeBasin.point(14) : EdgeLandscape.point(EdgeLandscape.END, 14);
        source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss.route_exit",
                exit.x(), AbyssEdgeGenerator.EXIT, exit.z()), false);
        return 1;
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        ServerLevel current = player.serverLevel();
        AbyssLayer layer = AbyssLayer.of(current.dimension());
        boolean surface = expedition(current);
        boolean shaft = surface && OrthIsland.vertical(current.getChunkSource().getGenerator());
        boolean edge = current.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator;
        if (shaft || edge) {
            tickEdge(player, current, shaft);
            return;
        }
        if ((!surface && layer == null) || !player.isAlive() || player.isPassenger() || player.isSleeping()
                || !AbyssLayer.passage(player.getX(), player.getZ(), AbyssLayer.PASSAGE_RADIUS + 24)) {
            last.remove(player);
            return;
        }
        double y = player.getY();
        int now = current.getServer().getTickCount();
        Position previous = last.get(player);
        boolean tracked = previous != null && previous.level == current;
        int settleUntil = tracked ? previous.settleUntil : now;
        last.put(player, new Position(current, y, settleUntil));
        double previousY = tracked && now >= settleUntil && Math.abs(previous.y - y) <= 64 ? previous.y : y;
        if (surface) {
            if (Math.abs(y - AbyssLayer.MOUTH_GATE) <= 96) {
                pass(player, current.getServer().getLevel(AbyssLayer.EDGE.key), AbyssLayer.MOUTH_GATE,
                        AbyssLayer.EDGE.entrance, previousY, true);
            }
            return;
        }
        if (!expedition(current.getServer().overworld())
                || !(current.getChunkSource().getGenerator() instanceof AbyssChunkGenerator generator) || generator.layer() != layer) return;
        if (Math.abs(y - layer.entrance) <= 96) {
            AbyssLayer upper = layer.previous();
            ServerLevel destination = upper == null ? current.getServer().overworld() : current.getServer().getLevel(upper.key);
            int destinationY = upper == null ? AbyssLayer.MOUTH_GATE : upper.exit;
            if (destination != null && destination.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator) {
                destinationY = AbyssEdgeGenerator.EXIT;
            }
            pass(player, destination, layer.entrance, destinationY, previousY, false);
        } else if (Math.abs(y - layer.exit) <= 96 && layer.next() != null) {
            AbyssLayer lower = layer.next();
            pass(player, current.getServer().getLevel(lower.key), layer.exit, lower.entrance, previousY, true);
        }
    }

    private void tickEdge(ServerPlayer player, ServerLevel current, boolean surface) {
        if (!player.isAlive() || player.isPassenger() || player.isSleeping()
                || !OrthIsland.inShaft(player.getX(), player.getZ())) {
            last.remove(player);
            warming.remove(player);
            return;
        }
        int tick = current.getServer().getTickCount();
        double y = player.getY();
        Position previous = last.get(player);
        int settle = previous != null && previous.level == current ? previous.settleUntil : tick;
        last.put(player, new Position(current, y, settle));
        ServerLevel adjacent = surface ? current.getServer().getLevel(AbyssLayer.EDGE.key) : current.getServer().overworld();
        if (adjacent == null || (surface && !(adjacent.getChunkSource().getGenerator() instanceof AbyssEdgeGenerator))
                || (!surface && !OrthIsland.vertical(adjacent.getChunkSource().getGenerator()))) return;
        if (Math.abs(y) < 210) warm(player, adjacent, tick);
        if (tick >= settle && (surface ? y < AbyssEdgeGenerator.ENTRANCE : y >= AbyssEdgeGenerator.ENTRANCE)) {
            move(player, adjacent, y, 4);
            return;
        }
        if (!surface && Math.abs(y - AbyssEdgeGenerator.EXIT) <= 96
                && AbyssLayer.passage(player.getX(), player.getZ(), AbyssLayer.PASSAGE_RADIUS + 24)) {
            double before = previous != null && previous.level == current && tick >= settle ? previous.y : y;
            pass(player, current.getServer().getLevel(AbyssLayer.FOREST.key), AbyssEdgeGenerator.EXIT,
                    AbyssLayer.FOREST.entrance, before, true);
        }
    }

    private void warm(ServerPlayer player, ServerLevel destination, int tick) {
        ChunkPos pos = new ChunkPos(player.blockPosition());
        Warmup previous = warming.get(player);
        if (previous != null && previous.level == destination && previous.chunk.equals(pos) && tick - previous.tick < 24) return;
        destination.getChunkSource().addRegionTicket(PASSAGE, pos, 2, pos);
        warming.put(player, new Warmup(destination, pos, tick));
    }

    private void pass(ServerPlayer player, ServerLevel destination, int fromY, int toY, double previousY, boolean descending) {
        if (destination == null || destination == player.serverLevel()) return;
        ChunkPos center = new ChunkPos(player.blockPosition());
        destination.getChunkSource().addRegionTicket(PASSAGE, center, 2, center);
        double overshoot = player.getY() - fromY;
        double before = previousY - fromY;
        if (!AbyssLayer.passage(player.getX(), player.getZ(), AbyssLayer.PASSAGE_RADIUS)
                || (descending ? before < -2 || overshoot >= -2 : before > 2 || overshoot <= 2)) return;
        move(player, destination, toY + Mth.clamp(overshoot, -44, 44), 10);
    }

    private void move(ServerPlayer player, ServerLevel destination, double y, int settleTicks) {
        ChunkPos center = new ChunkPos(player.blockPosition());
        destination.getChunk(center.x, center.z);
        var motion = player.getDeltaMovement();
        float fall = player.fallDistance;
        player.teleportTo(destination, player.getX(), y, player.getZ(), Set.of(), player.getYRot(), player.getXRot());
        if (player.serverLevel() != destination) return;
        last.put(player, new Position(destination, y, destination.getServer().getTickCount() + settleTicks));
        player.onUpdateAbilities();
        // Teleport sends a respawn packet, which recreates the local player without its active effects.
        for (var effect : player.getActiveEffects()) {
            player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), effect));
        }
        player.setDeltaMovement(motion);
        player.hurtMarked = true;
        player.fallDistance = fall;
    }
}
