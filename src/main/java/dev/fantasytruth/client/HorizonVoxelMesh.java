package dev.fantasytruth.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.fantasytruth.horizon.HorizonTile.Data;
import dev.fantasytruth.horizon.HorizonTile.Span;
import dev.fantasytruth.horizon.HorizonTile.Texture;
import java.util.List;

final class HorizonVoxelMesh {
    static void cell(BufferBuilder out, Data data, int x, int z, int step, boolean decoration) {
        var spans = data.column(x, z);
        int x0 = x * step, z0 = z * step, x1 = x0 + step, z1 = z0 + step;
        for (var span : spans) {
            if (span.canopy() || decoration && !span.snapshot()) continue;
            if (span.water()) {
                if (!occupied(spans, span.top())) {
                    horizontal(out, x0, z0, x1, z1, span.top() - 0.12f, span.color(), 0, false);
                }
                continue;
            }
            int material = material(span);
            if (!occupied(spans, span.top())) {
                horizontal(out, x0, z0, x1, z1, span.top(), span.color(), material, false);
            }
            if ((decoration || span.layer() != 0 && span.top() - span.bottom() <= 40) && !occupied(spans, span.bottom() - 1)) {
                horizontal(out, x0, z0, x1, z1, span.bottom(), HorizonMesh.shade(span.color(), 0.59), material, true);
            }
            int last = data.samples() - 4;
            int skirt = !decoration && (material == Texture.GRASS.ordinal() || material == Texture.SAND.ordinal()
                    || material == Texture.STONE.ordinal() || material == Texture.SOIL.ordinal()) ? step * 2 : 0;
            side(out, data.column(x - 1, z), x0, z0, x0, z1, span, material, 0.79, x == 0 ? skirt : 0);
            side(out, data.column(x + 1, z), x1, z1, x1, z0, span, material, 0.84, x == last ? skirt : 0);
            side(out, data.column(x, z - 1), x1, z0, x0, z0, span, material, 0.90, z == 0 ? skirt : 0);
            side(out, data.column(x, z + 1), x0, z1, x1, z1, span, material, 0.74, z == last ? skirt : 0);
        }
    }

    private static boolean occupied(List<Span> column, int y) {
        for (var span : column) {
            if (!span.water() && !span.canopy() && span.bottom() <= y && span.top() > y) return true;
        }
        return false;
    }

    private static void side(BufferBuilder out, List<Span> neighbor, int x0, int z0, int x1, int z1,
                             Span span, int material, double light, int skirt) {
        int y = span.bottom(), end = span.top();
        int color = HorizonMesh.shade(span.color(), light);
        for (var other : neighbor) {
            int limit = Math.min(other.top(), end - skirt);
            if (other.water() || other.canopy() || limit <= y) continue;
            if (other.bottom() >= end - skirt) break;
            if (other.bottom() > y) sideFace(out, x0, z0, x1, z1, y, other.bottom(), end, color, material, light);
            y = limit;
            if (y >= end) return;
        }
        sideFace(out, x0, z0, x1, z1, y, end, end, color, material, light);
    }

    private static void sideFace(BufferBuilder out, int x0, int z0, int x1, int z1, int bottom, int top,
                                 int surface, int color, int material, double light) {
        if (material != Texture.GRASS.ordinal()) {
            quad(out, x0, z0, x1, z1, bottom, top, color, material);
            return;
        }
        int soil = Math.max(bottom, surface - 3);
        if (soil > bottom) {
            quad(out, x0, z0, x1, z1, bottom, Math.min(top, soil),
                    HorizonMesh.shade(0x858579, light), Texture.STONE.ordinal());
        }
        if (top > soil) {
            quad(out, x0, z0, x1, z1, soil, top, HorizonMesh.shade(0x826447, light), Texture.SOIL.ordinal());
        }
    }

    private static int material(Span span) {
        if (span.texture() != 0) return span.texture();
        int r = span.color() >> 16 & 255, g = span.color() >> 8 & 255, b = span.color() & 255;
        if (g > r + 4 && g > b + 8) return Texture.GRASS.ordinal();
        return r > b + 12 ? Texture.SAND.ordinal() : Texture.STONE.ordinal();
    }

    private static void horizontal(BufferBuilder out, int x0, int z0, int x1, int z1, float y,
                                   int color, int material, boolean bottom) {
        HorizonMesh.vertex(out, x0, y, z0, color, material);
        HorizonMesh.vertex(out, bottom ? x1 : x0, y, bottom ? z0 : z1, color, material);
        HorizonMesh.vertex(out, x1, y, z1, color, material);
        HorizonMesh.vertex(out, bottom ? x0 : x1, y, bottom ? z1 : z0, color, material);
    }

    private static void quad(BufferBuilder out, int x0, int z0, int x1, int z1, int bottom, int top, int color, int material) {
        HorizonMesh.vertex(out, x0, bottom, z0, color, material);
        HorizonMesh.vertex(out, x1, bottom, z1, color, material);
        HorizonMesh.vertex(out, x1, top, z1, color, material);
        HorizonMesh.vertex(out, x0, top, z0, color, material);
    }

    private HorizonVoxelMesh() {}
}
