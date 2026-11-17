package dev.fantasytruth.horizon;

import dev.fantasytruth.horizon.HorizonTile.Span;
import dev.fantasytruth.horizon.HorizonTile.Texture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** Material runs observed in real chunks, with one sample for every two horizontal blocks. */
public final class HorizonChunkData {
    public static final int STEP = 2;
    public static final int WIDTH = 16 / STEP;
    private static final int VERSION = 1;
    private static final int WATER = 8;
    private final List<List<Span>> source;
    private volatile List<List<List<Span>>> levels;

    public HorizonChunkData(List<List<Span>> columns) {
        if (columns.size() != WIDTH * WIDTH) throw new IllegalArgumentException("Invalid observed grid size");
        source = List.copyOf(columns);
    }

    List<Span> column(int x, int z, int step) {
        var pyramid = levels;
        if (pyramid == null) pyramid = reduce();
        int index = Integer.numberOfTrailingZeros(step / STEP);
        int width = 16 / step;
        return pyramid.get(index).get((z & 15) / step * width + (x & 15) / step);
    }

    private synchronized List<List<List<Span>>> reduce() {
        if (levels != null) return levels;
        var pyramid = new ArrayList<List<List<Span>>>(4);
        var input = source;
        pyramid.add(input);
        for (int width = WIDTH; width > 1; width /= 2) {
            var next = new ArrayList<List<Span>>(width * width / 4);
            for (int z = 0; z < width; z += 2) {
                for (int x = 0; x < width; x += 2) {
                    int i = z * width + x;
                    next.add(reduce(List.of(input.get(i), input.get(i + 1), input.get(i + width), input.get(i + width + 1))));
                }
            }
            input = next;
            pyramid.add(input);
        }
        return levels = List.copyOf(pyramid);
    }

    private static List<Span> reduce(List<List<Span>> columns) {
        int capacity = columns.stream().mapToInt(List::size).sum() * 2;
        if (capacity == 0) return List.of();
        var edges = new int[capacity];
        int edge = 0;
        for (var column : columns) {
            for (var span : column) {
                edges[edge++] = span.bottom();
                edges[edge++] = span.top();
            }
        }
        Arrays.sort(edges);
        var output = new ArrayList<Span>();
        var samples = new Span[4];
        var current = new int[4];
        var votes = new int[9];
        var textures = Texture.values();
        for (int i = 1; i < edges.length; i++) {
            int bottom = edges[i - 1], top = edges[i];
            if (bottom == top) continue;
            Arrays.fill(votes, 0);
            int solid = 0;
            for (int j = 0; j < columns.size(); j++) {
                var column = columns.get(j);
                while (current[j] < column.size() && column.get(current[j]).top() <= bottom) current[j]++;
                var span = current[j] < column.size() ? column.get(current[j]) : null;
                samples[j] = span != null && span.bottom() <= bottom ? span : null;
                if (samples[j] == null) continue;
                if (!span.water()) solid++;
                votes[span.water() ? WATER : span.texture()]++;
            }
            int material;
            if (solid >= 2) {
                material = 0;
                for (int j = 1; j < WATER; j++) if (votes[j] > votes[material]) material = j;
            } else if (votes[WATER] >= 2) {
                material = WATER;
            } else continue;

            int red = 0, green = 0, blue = 0, count = 0;
            for (var span : samples) {
                if (span == null || (span.water() ? WATER : span.texture()) != material) continue;
                red += span.color() >>> 16 & 255;
                green += span.color() >>> 8 & 255;
                blue += span.color() & 255;
                count++;
            }
            int rgb = (red / count << 16) | (green / count << 8) | (blue / count);
            int color = material == WATER ? HorizonTile.waterColor(rgb) : HorizonTile.snapshotColor(rgb, textures[material]);
            int last = output.size() - 1;
            if (last >= 0 && output.get(last).top() == bottom && output.get(last).color() == color) {
                output.set(last, new Span(output.get(last).bottom(), top, color));
            } else output.add(new Span(bottom, top, color));
        }
        if (output.size() > HorizonTile.MAX_SPANS) {
            output.sort(Comparator.comparingInt((Span span) -> span.top() - span.bottom()).reversed());
            output.subList(HorizonTile.MAX_SPANS, output.size()).clear();
            output.sort(Comparator.comparingInt(Span::bottom));
        }
        return List.copyOf(output);
    }

    CompoundTag encode() {
        int[] runs = new int[source.size() + source.stream().mapToInt(List::size).sum() * 2];
        int offset = 0;
        for (var column : source) {
            runs[offset++] = column.size();
            for (var span : column) {
                runs[offset++] = (span.bottom() << 16) | (span.top() & 0xffff);
                runs[offset++] = span.color();
            }
        }
        var tag = new CompoundTag();
        tag.putInt("format", VERSION);
        tag.putIntArray("runs", runs);
        return tag;
    }

    static HorizonChunkData decode(CompoundTag tag) {
        if (tag.getInt("format") != VERSION) return null;
        var runs = tag.getIntArray("runs");
        var columns = new ArrayList<List<Span>>(WIDTH * WIDTH);
        int offset = 0;
        for (int i = 0; i < WIDTH * WIDTH; i++) {
            if (offset >= runs.length) return null;
            int count = runs[offset++];
            if (count < 0 || count > HorizonTile.MAX_SPANS || offset + count * 2 > runs.length) return null;
            var column = new ArrayList<Span>(count);
            int previousTop = Short.MIN_VALUE;
            for (int j = 0; j < count; j++) {
                int bounds = runs[offset++];
                int bottom = bounds >> 16, top = (short) bounds;
                var span = new Span(bottom, top, runs[offset++]);
                if (bottom < previousTop || top <= bottom || !span.snapshot() && !span.water()) return null;
                previousTop = top;
                column.add(span);
            }
            columns.add(List.copyOf(column));
        }
        return offset == runs.length ? new HorizonChunkData(columns) : null;
    }
}
