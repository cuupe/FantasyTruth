package dev.fantasytruth.horizon;

import com.mojang.logging.LogUtils;
import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.abyss.AbyssHorizonSampler;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

public final class HorizonNetwork {
    private static final String PROTOCOL = "6";
    private static final int CACHE_TILES = 320;
    private static final int REQUEST_BURST = 4;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FantasyTruth.ID, "horizon_v6"), () -> PROTOCOL, HorizonNetwork::compatible, HorizonNetwork::compatible);
    private static final int WORKER_COUNT = Runtime.getRuntime().availableProcessors() >= 8 ? 2 : 1;
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(WORKER_COUNT, WORKER_COUNT, 20, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), task -> {
                Thread thread = new Thread(task, "FantasyTruth terrain horizon");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
    static { WORKER.allowCoreThreadTimeOut(true); }
    private static final Map<ServerLevel, Map<HorizonTile, HorizonTile.Data>> CACHE = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<HorizonTile, Job>> IN_FLIGHT = new WeakHashMap<>();
    private static final Map<ServerLevel, AbyssHorizonSampler> SAMPLERS = new WeakHashMap<>();
    private record RequestBudget(int tick, int remaining) {}
    private static final class Job {
        final Map<ServerPlayer, Request> listeners = new WeakHashMap<>();
        boolean stale;
    }
    private static final Map<ServerPlayer, RequestBudget> REQUEST_BUDGETS = new WeakHashMap<>();
    private static volatile Consumer<Response> receiver = response -> {};

    private static boolean compatible(String version) {
        return PROTOCOL.equals(version) || NetworkRegistry.ABSENT.equals(version)
                || NetworkRegistry.ACCEPTVANILLA.equals(version);
    }

    public static void register() {
        CHANNEL.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Request::write).decoder(Request::read).consumerMainThread((request, context) ->
                        serve(context.get().getSender(), request)).add();
        CHANNEL.messageBuilder(Response.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Response::write).decoder(Response::read).consumerMainThread((response, context) ->
                        receiver.accept(response)).add();
    }

    public static void setReceiver(Consumer<Response> listener) { receiver = listener; }
    public static boolean available(Connection connection) { return CHANNEL.isRemotePresent(connection); }
    public static void request(ResourceLocation dimension, int epoch, HorizonTile tile, boolean refresh) {
        CHANNEL.sendToServer(new Request(dimension, epoch, tile, refresh));
    }

    private static void serve(ServerPlayer player, Request request) {
        if (player == null || !request.tile.valid()) return;
        ServerLevel level = player.serverLevel();
        int distance = request.tile.distance(player.getBlockX(), player.getBlockZ());
        if (!level.dimension().location().equals(request.dimension) || !request.tile.withinRange(player.getBlockX(), player.getBlockZ())
                || (request.tile.size() < HorizonTile.MAX_SIZE && distance > request.tile.size() * 5)) return;
        int now = player.getServer().getTickCount();
        var previous = REQUEST_BUDGETS.get(player);
        // Client ticks can arrive together after a generation or network stall.
        int credits = previous == null ? REQUEST_BURST : Math.min(REQUEST_BURST, previous.remaining + now - previous.tick);
        if (credits <= 0) return;
        REQUEST_BUDGETS.put(player, new RequestBudget(now, credits - 1));
        var cache = CACHE.computeIfAbsent(level, unused -> new LinkedHashMap<>(CACHE_TILES, 0.75f, true));
        var cached = cache.get(request.tile);
        if (cached != null && (!request.refresh || request.tile.step() > 8)) {
            reply(player, request, cached);
            return;
        }
        var jobs = IN_FLIGHT.computeIfAbsent(level, unused -> new HashMap<>());
        var waiting = jobs.get(request.tile);
        if (waiting != null) {
            waiting.listeners.put(player, request);
            return;
        }
        var sampler = sampler(level);
        if (sampler == null) return;
        var captured = sampler.captureLoaded(level, request.tile);
        if (cached != null && captured.isEmpty()) {
            reply(player, request, cached);
            return;
        }
        var server = level.getServer();
        var archive = HorizonObservations.archive(level);
        var job = new Job();
        job.listeners.put(player, request);
        jobs.put(request.tile, job);
        try {
            WORKER.execute(() -> {
                try {
                    var data = sampler.sample(request.tile, captured,
                            archive == null ? Map.of() : archive.readTile(request.tile, captured));
                    server.execute(() -> {
                        jobs.remove(request.tile);
                        if (!job.stale) {
                            cache.put(request.tile, data);
                            if (cache.size() > CACHE_TILES) cache.remove(cache.keySet().iterator().next());
                        }
                        job.listeners.forEach((recipient, latest) -> {
                            if (recipient.serverLevel() == level && !recipient.hasDisconnected()) reply(recipient, latest, data);
                        });
                    });
                } catch (RuntimeException exception) {
                    server.execute(() -> jobs.remove(request.tile));
                    LOGGER.debug("Could not sample horizon tile {}", request.tile, exception);
                }
            });
        } catch (RejectedExecutionException busy) {
            jobs.remove(request.tile);
            // The client retries after its request expires. Never put generation on the tick thread.
        }
    }

    static AbyssHorizonSampler sampler(ServerLevel level) {
        return SAMPLERS.computeIfAbsent(level, AbyssHorizonSampler::of);
    }

    static void invalidate(ServerLevel level, ChunkPos pos) {
        var cache = CACHE.get(level);
        if (cache != null) cache.keySet().removeIf(tile -> samplesChunk(tile, pos));
        var jobs = IN_FLIGHT.get(level);
        if (jobs != null) jobs.forEach((tile, job) -> {
            if (samplesChunk(tile, pos)) job.stale = true;
        });
    }

    private static boolean samplesChunk(HorizonTile tile, ChunkPos pos) {
        int x = pos.getMinBlockX(), z = pos.getMinBlockZ(), step = tile.step();
        return step <= 16 && x <= tile.x() + tile.size() + step && x + 15 >= tile.x() - step
                && z <= tile.z() + tile.size() + step && z + 15 >= tile.z() - step;
    }

    private static void reply(ServerPlayer player, Request request, HorizonTile.Data data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Response(request.dimension, request.epoch, request.tile, data));
    }

    private record Request(ResourceLocation dimension, int epoch, HorizonTile tile, boolean refresh) {
        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(dimension);
            buffer.writeVarInt(epoch);
            tile.write(buffer);
            buffer.writeBoolean(refresh);
        }
        static Request read(FriendlyByteBuf buffer) {
            return new Request(buffer.readResourceLocation(), buffer.readVarInt(), HorizonTile.read(buffer), buffer.readBoolean());
        }
    }

    public record Response(ResourceLocation dimension, int epoch, HorizonTile tile, HorizonTile.Data data) {
        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(dimension);
            buffer.writeVarInt(epoch);
            tile.write(buffer);
            data.write(buffer);
        }
        static Response read(FriendlyByteBuf buffer) {
            var dimension = buffer.readResourceLocation();
            int epoch = buffer.readVarInt();
            var tile = HorizonTile.read(buffer);
            if (!tile.valid()) throw new IllegalArgumentException("Invalid horizon tile");
            return new Response(dimension, epoch, tile, HorizonTile.Data.read(buffer, tile));
        }
    }

    private HorizonNetwork() {}
}
