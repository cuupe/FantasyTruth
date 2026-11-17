package dev.fantasytruth.client;

import dev.fantasytruth.horizon.HorizonTile;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

final class HorizonCoverage {
    static final int FULL_TILE = 0xffff;

    record Plan(Map<HorizonTile, Integer> masks, int radius) {}

    static final class Layout {
        private final HorizonTile[][] branches;
        private final int[] sources;

        Layout(List<HorizonTile> leaves) {
            branches = new HorizonTile[leaves.size()][];
            sources = new int[leaves.size()];
            for (int i = 0; i < leaves.size(); i++) {
                var leaf = leaves.get(i);
                var branch = new HorizonTile[Integer.numberOfTrailingZeros(HorizonTile.MAX_SIZE / leaf.size()) + 1];
                branch[0] = leaf;
                for (int j = 1; j < branch.length; j++) branch[j] = branch[j - 1].parent();
                branches[i] = branch;
            }
        }
    }

    static List<HorizonTile> ancestors(List<HorizonTile> leaves, boolean roots) {
        var tiles = new LinkedHashSet<HorizonTile>();
        for (var leaf : leaves) {
            int size = roots ? HorizonTile.MAX_SIZE : Math.min(leaf.size() * 4, HorizonTile.MAX_SIZE);
            var tile = ancestor(leaf, size);
            if (roots || !tile.equals(leaf)) tiles.add(tile);
        }
        return List.copyOf(tiles);
    }

    static Plan plan(Layout layout, Predicate<HorizonTile> ready, int x, int z, int range) {
        var fallback = new HashMap<HorizonTile, HorizonTile>();
        int radius = range;
        for (int i = 0; i < layout.branches.length; i++) {
            var branch = layout.branches[i];
            int level = 0;
            while (level < branch.length && !ready.test(branch[level])) level++;
            layout.sources[i] = level;
            if (level == branch.length) {
                var leaf = branch[0];
                int dx = Math.max(0, Math.max(leaf.x() - x, x - leaf.x() - leaf.size()));
                int dz = Math.max(0, Math.max(leaf.z() - z, z - leaf.z() - leaf.size()));
                radius = Math.min(radius, Math.max(0, (int) Math.hypot(dx, dz) - 12));
                continue;
            }
            // The mask works at quarter-tile resolution. Reserve that entire patch until it can be refined.
            if (level > 2) fallback.put(branch[level - 2], branch[level]);
        }

        var masks = new LinkedHashMap<HorizonTile, Integer>();
        for (int i = 0; i < layout.branches.length; i++) {
            var branch = layout.branches[i];
            int level = layout.sources[i];
            if (level == branch.length) continue;
            var area = branch[0];
            var tile = branch[level];
            if (!fallback.isEmpty()) {
                for (int j = branch.length - 3; j >= 0; j--) {
                    var coarse = fallback.get(branch[j]);
                    if (coarse != null) {
                        area = branch[j];
                        tile = coarse;
                        break;
                    }
                }
            }
            masks.merge(tile, mask(tile, area), (first, second) -> first | second);
        }
        return new Plan(masks, radius);
    }

    private static HorizonTile ancestor(HorizonTile tile, int size) {
        while (tile.size() < size) tile = tile.parent();
        return tile;
    }

    private static int mask(HorizonTile tile, HorizonTile area) {
        if (tile.equals(area)) return FULL_TILE;
        int cell = tile.size() / 4;
        int x = (area.x() - tile.x()) / cell, z = (area.z() - tile.z()) / cell;
        int count = area.size() / cell;
        int row = ((1 << count) - 1) << x;
        int mask = 0;
        for (int i = z; i < z + count; i++) mask |= row << (i * 4);
        return mask;
    }

    private HorizonCoverage() {}
}
