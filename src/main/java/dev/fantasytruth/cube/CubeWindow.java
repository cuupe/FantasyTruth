package dev.fantasytruth.cube;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Single-threaded, bounded cube residency window. Moving never scans between heights. */
public final class CubeWindow {
    private record Offset(int x, int y, int z) {
        int distanceSquared() {
            return x * x + y * y + z * z;
        }
    }

    private final CubeTerrain terrain;
    private final List<Offset> offsets;
    private final Map<CubePos, CubeTerrain.Cube> loaded = new HashMap<>();
    private final ArrayDeque<CubePos> pending = new ArrayDeque<>();
    private CubePos center;

    public CubeWindow(CubeTerrain terrain, int horizontalRadius, int verticalRadius) {
        if (horizontalRadius < 0 || horizontalRadius > 16 || verticalRadius < 0 || verticalRadius > 16) {
            throw new IllegalArgumentException("Cube radii must be in [0, 16]");
        }
        this.terrain = Objects.requireNonNull(terrain);
        offsets = new ArrayList<>();
        for (int y = -verticalRadius; y <= verticalRadius; y++) {
            for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                    offsets.add(new Offset(x, y, z));
                }
            }
        }
        offsets.sort(Comparator.comparingInt(Offset::distanceSquared));
    }

    public void moveTo(CubePos target) {
        Objects.requireNonNull(target);
        if (target.equals(center)) {
            return;
        }
        center = target;
        Set<CubePos> wanted = new HashSet<>();
        pending.clear();
        for (Offset offset : offsets) {
            int x = center.x() + offset.x;
            long y = center.y() + offset.y;
            int z = center.z() + offset.z;
            if (CubePos.valid(x, y, z)) {
                CubePos pos = new CubePos(x, y, z);
                wanted.add(pos);
                if (!loaded.containsKey(pos)) {
                    pending.addLast(pos);
                }
            }
        }
        loaded.keySet().retainAll(wanted);
    }

    public int generatePending(int budget) {
        if (budget < 0) {
            throw new IllegalArgumentException("Negative generation budget");
        }
        int generated = 0;
        while (generated < budget && !pending.isEmpty()) {
            CubePos pos = pending.getFirst();
            loaded.put(pos, terrain.generate(pos));
            pending.removeFirst();
            generated++;
        }
        return generated;
    }

    public CubeTerrain.Cube getLoaded(CubePos pos) {
        return loaded.get(pos);
    }

    public int loadedCount() {
        return loaded.size();
    }
}
