package dev.fantasytruth.atlas;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

final class AtlasPageData extends SavedData {
    record PlayerLocation(long page, double x, double y, double z, float yaw, float pitch) {}

    private final Map<UUID, PlayerLocation> players = new HashMap<>();
    private final Map<Long, NavigableMap<Long, Map<Integer, BlockState>>> edits = new HashMap<>();

    PlayerLocation player(UUID id) { return players.get(id); }

    void player(UUID id, PlayerLocation location) {
        PlayerLocation before = location == null ? players.remove(id) : players.put(id, location);
        if (!java.util.Objects.equals(before, location)) setDirty();
    }

    void record(int x, long y, int z, BlockState state) {
        long chunk = ChunkPos.asLong(x >> 4, z >> 4);
        int cell = (x & 15) | (Math.floorMod(y, 16) << 4) | ((z & 15) << 8);
        BlockState previous = edits.computeIfAbsent(chunk, key -> new TreeMap<>())
                .computeIfAbsent(Math.floorDiv(y, 16), key -> new HashMap<>()).put(cell, state);
        if (previous != state) setDirty();
    }

    List<BlockPos> apply(ServerLevel level, long page, ChunkPos chunk) {
        NavigableMap<Long, Map<Integer, BlockState>> cubes = edits.get(chunk.toLong());
        if (cubes == null) return List.of();
        var changed = new ArrayList<BlockPos>();
        long base = page * Altitude.STRIDE;
        long min = base + level.getMinBuildHeight();
        long max = base + level.getMaxBuildHeight() - 1;
        for (var cube : cubes.subMap(Math.floorDiv(min, 16), true, Math.floorDiv(max, 16), true).entrySet()) {
            for (var block : cube.getValue().entrySet()) {
                int cell = block.getKey();
                long globalY = cube.getKey() * 16 + ((cell >> 4) & 15);
                if (globalY < min || globalY > max) continue;
                BlockPos pos = new BlockPos(chunk.getMinBlockX() + (cell & 15), (int) (globalY - base),
                        chunk.getMinBlockZ() + ((cell >> 8) & 15));
                BlockState oldState = level.getBlockState(pos);
                if (oldState != block.getValue() && (AtlasPages.ownsBlock(level, pos) || !hasStoredContents(level, pos))
                        && AtlasPages.projectBlock(level, pos, block.getValue())) {
                    changed.add(pos);
                }
            }
        }
        return changed;
    }

    static boolean hasStoredContents(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).hasBlockEntity()) return false;
        var entity = level.getBlockEntity(pos);
        if (entity == null) return false;
        CompoundTag saved = entity.saveWithFullMetadata();
        return saved.contains("LootTable") || !saved.getList("Items", Tag.TAG_COMPOUND).isEmpty();
    }

    static AtlasPageData load(CompoundTag tag) {
        AtlasPageData data = new AtlasPageData();
        ListTag playerTags = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerTags.size(); i++) {
            CompoundTag p = playerTags.getCompound(i);
            data.players.put(p.getUUID("id"), new PlayerLocation(p.getLong("page"), p.getDouble("x"), p.getDouble("y"),
                    p.getDouble("z"), p.getFloat("yaw"), p.getFloat("pitch")));
        }
        ListTag cubeTags = tag.getList("cubes", Tag.TAG_COMPOUND);
        for (int i = 0; i < cubeTags.size(); i++) {
            CompoundTag cube = cubeTags.getCompound(i);
            Map<Integer, BlockState> changes = new HashMap<>();
            ListTag blocks = cube.getList("blocks", Tag.TAG_COMPOUND);
            for (int j = 0; j < blocks.size(); j++) {
                CompoundTag block = blocks.getCompound(j);
                changes.put(block.getInt("cell"), NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), block.getCompound("state")));
            }
            data.edits.computeIfAbsent(cube.getLong("xz"), key -> new TreeMap<>()).put(cube.getLong("y"), changes);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag playerTags = new ListTag();
        players.forEach((id, location) -> {
            CompoundTag p = new CompoundTag();
            p.putUUID("id", id);
            p.putLong("page", location.page());
            p.putDouble("x", location.x());
            p.putDouble("y", location.y());
            p.putDouble("z", location.z());
            p.putFloat("yaw", location.yaw());
            p.putFloat("pitch", location.pitch());
            playerTags.add(p);
        });
        tag.put("players", playerTags);
        ListTag cubeTags = new ListTag();
        edits.forEach((xz, cubes) -> cubes.forEach((y, changes) -> {
            CompoundTag cube = new CompoundTag();
            cube.putLong("xz", xz);
            cube.putLong("y", y);
            ListTag blocks = new ListTag();
            changes.forEach((cell, state) -> {
                CompoundTag block = new CompoundTag();
                block.putInt("cell", cell);
                block.put("state", NbtUtils.writeBlockState(state));
                blocks.add(block);
            });
            cube.put("blocks", blocks);
            cubeTags.add(cube);
        }));
        tag.put("cubes", cubeTags);
        return tag;
    }
}
