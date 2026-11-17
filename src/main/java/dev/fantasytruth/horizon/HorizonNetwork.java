package dev.fantasytruth.horizon;

import com.mojang.logging.LogUtils;
import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.abyss.AbyssHorizonSampler;
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
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

public final class HorizonNetwork {
    private static final String PROTOCOL = "1";
    private static final int CACHE_TILES = 320;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FantasyTruth.ID, "horizon"), () -> PROTOCOL, HorizonNetwork::compatible, HorizonNetwork::compatible);
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(0, 1, 20, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), task -> {
                Thread thread = new Thread(task, "FantasyTruth terrain horizon");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
    private static final Map<ServerLevel, Map<HorizonTile, HorizonTile.Data>> CACHE = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> LAST_REQUEST = new WeakHashMap<>();
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
    public static void request(ResourceLocation dimension, int epoch, HorizonTile tile) {
        CHANNEL.sendToServer(new Request(dimension, epoch, tile));
    }

    private static void serve(ServerPlayer player, Request request) {
        if (player == null || !request.tile.valid()) return;
        ServerLevel level = player.serverLevel();
        int distance = request.tile.distance(player.getBlockX(), player.getBlockZ());
        if (!level.dimension().location().equals(request.dimension) || !request.tile.withinRange(player.getBlockX(), player.getBlockZ())
                || (request.tile.size() < HorizonTile.MAX_SIZE && distance > request.tile.size() * 5)) return;
        int now = player.getServer().getTickCount();
        Integer previous = LAST_REQUEST.put(player, now);
        if (previous != null && now - previous < 1) return;
        var cache = CACHE.computeIfAbsent(level, unused -> new LinkedHashMap<>(CACHE_TILES, 0.75f, true));
        var cached = cache.get(request.tile);
        if (cached != null) {
            reply(player, request, cached);
            return;
        }
        var sampler = AbyssHorizonSampler.of(level);
        if (sampler == null) return;
        var server = level.getServer();
        try {
            WORKER.execute(() -> {
                try {
                    var data = sampler.sample(request.tile);
                    server.execute(() -> {
                        cache.put(request.tile, data);
                        if (cache.size() > CACHE_TILES) cache.remove(cache.keySet().iterator().next());
                        if (player.serverLevel() == level && !player.hasDisconnected()) reply(player, request, data);
                    });
                } catch (RuntimeException exception) {
                    LOGGER.debug("Could not sample horizon tile {}", request.tile, exception);
                }
            });
        } catch (RejectedExecutionException busy) {
            // The client retries after its request expires. Never put generation on the tick thread.
        }
    }

    private static void reply(ServerPlayer player, Request request, HorizonTile.Data data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Response(request.dimension, request.epoch, request.tile, data));
    }

    private record Request(ResourceLocation dimension, int epoch, HorizonTile tile) {
        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(dimension);
            buffer.writeVarInt(epoch);
            tile.write(buffer);
        }
        static Request read(FriendlyByteBuf buffer) {
            return new Request(buffer.readResourceLocation(), buffer.readVarInt(), HorizonTile.read(buffer));
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
            return new Response(buffer.readResourceLocation(), buffer.readVarInt(), HorizonTile.read(buffer), HorizonTile.Data.read(buffer));
        }
    }

    private HorizonNetwork() {}
}
