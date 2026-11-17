package dev.fantasytruth.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fantasytruth.horizon.HorizonTile;
import dev.fantasytruth.horizon.HorizonTile.Span;
import java.util.List;
import net.minecraft.world.phys.AABB;

record HorizonMesh(VertexBuffer buffer, AABB bounds) implements AutoCloseable {
    static HorizonMesh build(HorizonTile tile, HorizonTile.Data data) {
        int step = tile.step();
        var out = Tesselator.getInstance().getBuilder();
        out.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        boolean continuous = data.continuousSurface();
        for (int z = 0; z < HorizonTile.CELLS; z++) {
            for (int x = 0; x < HorizonTile.CELLS; x++) {
                int x0 = x * step, z0 = z * step, x1 = x0 + step, z1 = z0 + step;
                var column = data.column(x, z);
                if (continuous) {
                    Span a = column.get(0), b = data.column(x, z + 1).get(0);
                    Span c = data.column(x + 1, z + 1).get(0), d = data.column(x + 1, z).get(0);
                    low = Math.min(low, Math.min(Math.min(a.top(), b.top()), Math.min(c.top(), d.top())) - step);
                    high = Math.max(high, Math.max(Math.max(a.top(), b.top()), Math.max(c.top(), d.top())));
                    surface(out, x0, z0, x1, z1, a, b, c, d);
                    if (x == 0) skirt(out, step, x0, z0, x0, z1, a, b);
                    if (x == HorizonTile.CELLS - 1) skirt(out, step, x1, z1, x1, z0, c, d);
                    if (z == 0) skirt(out, step, x1, z0, x0, z0, d, a);
                    if (z == HorizonTile.CELLS - 1) skirt(out, step, x0, z1, x1, z1, b, c);
                    continue;
                }
                for (Span span : column) {
                    int y0 = span.bottom(), y1 = span.top();
                    low = Math.min(low, y0);
                    high = Math.max(high, y1);
                    Span south = adjacent(span, data.column(x, z + 1), step);
                    Span east = adjacent(span, data.column(x + 1, z), step);
                    Span corner = adjacent(span, data.column(x + 1, z + 1), step);
                    Span b = south == null ? span : south;
                    Span c = corner == null ? span : corner;
                    Span d = east == null ? span : east;
                    low = Math.min(low, Math.min(Math.min(b.bottom(), c.bottom()), d.bottom()));
                    high = Math.max(high, Math.max(Math.max(b.top(), c.top()), d.top()));
                    surface(out, x0, z0, x1, z1, span, b, c, d);
                    underside(out, x0, z0, x1, z1, span, b, c, d);
                    if (x == 0 || adjacent(span, data.column(x - 1, z), step) == null)
                        wall(out, x0, z0, x0, z1, span, b, 0.72);
                    if (x == HorizonTile.CELLS - 1 || east == null)
                        wall(out, x1, z1, x1, z0, c, d, 0.80);
                    if (z == 0 || adjacent(span, data.column(x, z - 1), step) == null)
                        wall(out, x1, z0, x0, z0, d, span, 0.86);
                    if (z == HorizonTile.CELLS - 1 || south == null)
                        wall(out, x0, z1, x1, z1, b, c, 0.68);
                }
            }
        }
        var rendered = out.endOrDiscardIfEmpty();
        if (rendered == null) return new HorizonMesh(null, null);
        var buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(rendered);
        VertexBuffer.unbind();
        return new HorizonMesh(buffer, new AABB(tile.x(), low, tile.z(), tile.x() + tile.size(), high, tile.z() + tile.size()));
    }

    private static void surface(BufferBuilder out, int x0, int z0, int x1, int z1,
                                Span a, Span b, Span c, Span d) {
        double dx = (c.top() + d.top() - a.top() - b.top()) / (2.0 * (x1 - x0));
        double dz = (b.top() + c.top() - a.top() - d.top()) / (2.0 * (z1 - z0));
        double light = 0.82 + 0.18 * (1 + 0.42 * (dx - dz)) / Math.sqrt(1 + dx * dx + dz * dz);
        vertex(out, x0, a.top(), z0, shade(a.color(), Math.min(1, light)));
        vertex(out, x0, b.top(), z1, shade(b.color(), Math.min(1, light)));
        vertex(out, x1, c.top(), z1, shade(c.color(), Math.min(1, light)));
        vertex(out, x1, d.top(), z0, shade(d.color(), Math.min(1, light)));
    }

    private static void skirt(BufferBuilder out, int drop, int x0, int z0, int x1, int z1, Span a, Span b) {
        int first = shade(a.color(), 0.75), second = shade(b.color(), 0.75);
        vertex(out, x0, a.top() - drop, z0, first);
        vertex(out, x1, b.top() - drop, z1, second);
        vertex(out, x1, b.top(), z1, second);
        vertex(out, x0, a.top(), z0, first);
    }

    private static Span adjacent(Span span, List<Span> candidates, int step) {
        int tolerance = Math.max(22, step * 3);
        int closest = Integer.MAX_VALUE;
        Span match = null;
        for (var other : candidates) {
            int topDistance = Math.abs(span.top() - other.top());
            int bottomDistance = Math.abs(span.bottom() - other.bottom());
            if (span.bottom() != other.bottom() && (topDistance > tolerance || bottomDistance > tolerance)) continue;
            int distance = topDistance + bottomDistance;
            if (distance < closest) {
                match = other;
                closest = distance;
            }
        }
        return match;
    }

    private static void underside(BufferBuilder out, int x0, int z0, int x1, int z1,
                                  Span a, Span b, Span c, Span d) {
        vertex(out, x0, a.bottom(), z0, shade(a.color(), 0.45));
        vertex(out, x1, d.bottom(), z0, shade(d.color(), 0.45));
        vertex(out, x1, c.bottom(), z1, shade(c.color(), 0.45));
        vertex(out, x0, b.bottom(), z1, shade(b.color(), 0.45));
    }

    private static void wall(BufferBuilder out, int x0, int z0, int x1, int z1, Span a, Span b, double light) {
        int first = shade(HorizonTile.STONE, light), second = first;
        if (a.color() == HorizonTile.WATER) first = shade(HorizonTile.WATER, light);
        if (b.color() == HorizonTile.WATER) second = shade(HorizonTile.WATER, light);
        vertex(out, x0, a.bottom(), z0, first);
        vertex(out, x1, b.bottom(), z1, second);
        vertex(out, x1, b.top(), z1, second);
        vertex(out, x0, a.top(), z0, first);
    }

    private static int shade(int color, double factor) {
        return (int) ((color >> 16 & 255) * factor) << 16
                | (int) ((color >> 8 & 255) * factor) << 8 | (int) ((color & 255) * factor);
    }

    private static void vertex(BufferBuilder out, int x, int y, int z, int color) {
        out.vertex(x, y, z).color(color >> 16 & 255, color >> 8 & 255, color & 255, 255).endVertex();
    }

    @Override public void close() { if (buffer != null) buffer.close(); }
}
