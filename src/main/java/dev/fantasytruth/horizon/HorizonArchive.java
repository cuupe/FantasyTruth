package dev.fantasytruth.horizon;

import com.mojang.logging.LogUtils;
import dev.fantasytruth.horizon.HorizonTile.Span;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.slf4j.Logger;

/** Server-owned LOD data in its own region files, independent of playable chunk NBT. */
final class HorizonArchive implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CACHE_CHUNKS = 192;
    private static final int MAX_PENDING_WRITES = 32;
    private final Path folder;
    private final Storage storage;
    private final Map<Long, Optional<HorizonChunkData>> cache = new LinkedHashMap<>(64, 0.75f, true);
    private final AtomicInteger pendingWrites = new AtomicInteger();
    private final AtomicBoolean reported = new AtomicBoolean();
    private boolean closed;

    HorizonArchive(Path folder) {
        this.folder = folder;
        storage = new Storage(folder);
    }

    boolean canRecord() {
        return pendingWrites.get() < MAX_PENDING_WRITES;
    }

    synchronized boolean record(ChunkPos pos, HorizonChunkData chunk) {
        if (closed || pendingWrites.get() >= MAX_PENDING_WRITES) return false;
        remember(pos.toLong(), Optional.of(chunk));
        pendingWrites.incrementAndGet();
        storage.store(pos, chunk.encode()).whenComplete((ignored, error) -> {
            pendingWrites.decrementAndGet();
            if (error != null) report(error);
        });
        return true;
    }

    Map<Long, List<Span>> readTile(HorizonTile tile, Map<Long, List<Span>> live) {
        int step = tile.step();
        if (step > 16) return Map.of();
        var chunks = new HashMap<Long, Optional<HorizonChunkData>>();
        var result = new HashMap<Long, List<Span>>();
        for (int z = tile.z() - step; z <= tile.z() + tile.size() + step; z += step) {
            for (int x = tile.x() - step; x <= tile.x() + tile.size() + step; x += step) {
                long key = ChunkPos.asLong(x, z);
                if (live.containsKey(key)) continue;
                long chunkKey = ChunkPos.asLong(x >> 4, z >> 4);
                var chunk = chunks.get(chunkKey);
                if (chunk == null) {
                    chunk = lookup(new ChunkPos(chunkKey));
                    chunks.put(chunkKey, chunk);
                }
                if (chunk.isPresent()) result.put(key, chunk.get().column(x, z, step));
            }
        }
        return result;
    }

    private Optional<HorizonChunkData> lookup(ChunkPos pos) {
        long key = pos.toLong();
        synchronized (this) {
            var cached = cache.get(key);
            if (cached != null) return cached;
            if (closed) return Optional.empty();
        }
        Optional<HorizonChunkData> value = Optional.empty();
        if (Files.isRegularFile(folder.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca"))) {
            try {
                var load = beginRead(pos);
                if (load != null) value = load.join().map(HorizonChunkData::decode);
            } catch (CompletionException exception) {
                report(exception);
            }
        }
        synchronized (this) {
            // The tick thread may have recorded a newer snapshot during the read.
            var latest = cache.get(key);
            if (latest != null) return latest;
            remember(key, value);
            return value;
        }
    }

    private synchronized CompletableFuture<Optional<CompoundTag>> beginRead(ChunkPos pos) {
        return closed ? null : storage.loadAsync(pos);
    }

    private void remember(long key, Optional<HorizonChunkData> value) {
        cache.put(key, value);
        if (cache.size() > CACHE_CHUNKS) cache.remove(cache.keySet().iterator().next());
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
        }
        try {
            storage.synchronize(true).join();
        } catch (CompletionException exception) {
            report(exception);
        } finally {
            try {
                storage.close();
            } catch (IOException exception) {
                report(exception);
            }
        }
    }

    private void report(Throwable error) {
        if (reported.compareAndSet(false, true)) LOGGER.warn("Horizon archive I/O failed at {}", folder, error);
    }

    private static final class Storage extends IOWorker {
        Storage(Path path) {
            super(path, false, "FantasyTruth horizon");
        }
    }
}
