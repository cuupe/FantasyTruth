package dev.fantasytruth.horizon;

import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.abyss.AbyssLayer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FantasyTruth.ID)
public final class HorizonObservations {
    private static final int MAX_QUEUED = 1024;
    private static final int RECENT_LIMIT = 2048;
    private static final Map<ServerLevel, State> WORLDS = new WeakHashMap<>();

    static HorizonArchive archive(ServerLevel level) {
        return supported(level) ? WORLDS.computeIfAbsent(level, State::new).archive : null;
    }

    private static boolean supported(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD || level.dimension() == AbyssLayer.FOREST.key;
    }

    @SubscribeEvent
    public static void watch(ChunkWatchEvent.Watch event) {
        if (!supported(event.getLevel())) return;
        var state = WORLDS.computeIfAbsent(event.getLevel(), State::new);
        int tick = event.getLevel().getServer().getTickCount();
        Integer last = state.recent.get(event.getPos());
        if (last == null || tick - last >= 1200) state.queue(event.getPos());
    }

    @SubscribeEvent
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        changed(event);
    }

    @SubscribeEvent
    public static void broken(BlockEvent.BreakEvent event) {
        changed(event);
    }

    private static void changed(BlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var state = WORLDS.get(level);
        if (state != null) state.queue(new ChunkPos(event.getPos()));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.haveTime()) return;
        WORLDS.forEach((level, state) -> {
            if (level.getServer() == event.getServer()) state.capture(level, event.getServer().getTickCount());
        });
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var state = WORLDS.remove(level);
            if (state != null) state.archive.close();
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        WORLDS.entrySet().removeIf(entry -> {
            if (entry.getKey().getServer() != event.getServer()) return false;
            entry.getValue().archive.close();
            return true;
        });
    }

    private static final class State {
        private final HorizonArchive archive;
        private final LinkedHashSet<ChunkPos> pending = new LinkedHashSet<>();
        private final Map<ChunkPos, Integer> recent = new LinkedHashMap<>(64, 0.75f, true);

        State(ServerLevel level) {
            var world = level.getServer().getWorldPath(LevelResource.ROOT);
            var dimension = DimensionType.getStorageFolder(level.dimension(), world);
            archive = new HorizonArchive(dimension.resolve("data").resolve(FantasyTruth.ID).resolve("horizon-v1"));
        }

        void queue(ChunkPos pos) {
            pending.add(pos);
            if (pending.size() > MAX_QUEUED) {
                var oldest = pending.iterator();
                oldest.next();
                oldest.remove();
            }
        }

        void capture(ServerLevel level, int tick) {
            if (pending.isEmpty() || !archive.canRecord()) return;
            var iterator = pending.iterator();
            var pos = iterator.next();
            iterator.remove();
            var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) return;
            var sampler = HorizonNetwork.sampler(level);
            if (sampler == null) return;
            var data = sampler.observe(chunk);
            if (data != null && archive.record(pos, data)) HorizonNetwork.invalidate(level, pos);
            recent.put(pos, tick);
            if (recent.size() > RECENT_LIMIT) recent.remove(recent.keySet().iterator().next());
        }
    }

    private HorizonObservations() {}
}
