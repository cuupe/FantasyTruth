package dev.fantasytruth.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.fantasytruth.horizon.HorizonTile;
import dev.fantasytruth.horizon.HorizonTile.Layer;
import dev.fantasytruth.horizon.HorizonTile.Span;
import java.util.List;

/** Groups vegetation into low rounded crowns when individual trunks no longer resolve on screen. */
final class HorizonCanopy {
    private static final int LEAVES = HorizonTile.Texture.LEAVES.ordinal();
    private static final float DIAGONAL = 0.70710677f;
    private static final float[] RING_X = {1, DIAGONAL, 0, -DIAGONAL, -1, -DIAGONAL, 0, DIAGONAL};
    private static final float[] RING_Z = {0, -DIAGONAL, -1, -DIAGONAL, 0, DIAGONAL, 1, DIAGONAL};

    static int scatter(BufferBuilder out, HorizonTile tile, int cellX, int cellZ, Layer layer,
                       List<Span> a, List<Span> b, List<Span> c, List<Span> d) {
        var banks = new Bank[]{bank(a, layer), bank(b, layer), bank(c, layer), bank(d, layer)};
        boolean hasTrees = false, continuous = true;
        for (var bank : banks) {
            hasTrees |= bank.trees != null;
            continuous &= bank.ground != null;
        }
        if (!hasTrees) return Integer.MIN_VALUE;

        int step = tile.step(), x = cellX * step, z = cellZ * step;
        int cellSeed = hash((tile.x() + x) * 6079 ^ (tile.z() + z) * 7919 ^ layer.ordinal() * 5237);
        int high = Integer.MIN_VALUE;
        for (int index = 0; index < 4; index++) {
            if ((step >= 128 || (cellSeed >>> 8 & 3) == 0) && index == (cellSeed >>> 12 & 3)) continue;
            int variant = hash(cellSeed + index * 2143);
            float u = ((index & 1) + 0.29f + (variant & 15) / 38.0f) * 0.5f;
            float v = ((index >> 1) + 0.29f + (variant >>> 4 & 15) / 38.0f) * 0.5f;
            if (continuous && tooSteep(banks, u, v, step)) continue;
            float[] weights = continuous ? new float[]{(1 - u) * (1 - v), (1 - u) * v, u * v, u * (1 - v)}
                    : u <= v ? new float[]{1 - v, v - u, u, 0} : new float[]{1 - u, 0, v, u - v};
            float land = 0, elevation = 0, trees = 0, height = 0, red = 0, green = 0, blue = 0;
            for (int i = 0; i < banks.length; i++) {
                var bank = banks[i];
                float weight = weights[i];
                if (bank.ground != null) {
                    land += weight;
                    elevation += bank.ground.top() * weight;
                }
                if (bank.trees != null) {
                    trees += weight;
                    height += (bank.trees.top() - bank.trees.bottom()) * weight;
                    red += (bank.trees.color() >> 16 & 255) * weight;
                    green += (bank.trees.color() >> 8 & 255) * weight;
                    blue += (bank.trees.color() & 255) * weight;
                }
            }
            if (land < 0.83f || trees < 0.43f + (variant >>> 8 & 7) * 0.026f) continue;
            float ground = elevation / land;
            float treeHeight = height / trees * (0.73f + (variant >>> 12 & 7) * 0.048f);
            float peak = ground + (step >= 128 ? Math.min(22, treeHeight) : treeHeight);
            float radius = step * (0.17f + (variant >>> 16 & 7) * 0.010f);
            radius = Math.min(radius, Math.min(Math.min(u, 1 - u), Math.min(v, 1 - v)) * step * 0.92f);
            int color = (int) (red / trees) << 16 | (int) (green / trees) << 8 | (int) (blue / trees);
            float centerX = x + step * u, centerZ = z + step * v;
            float crownBase = step >= 128 ? ground - 1 : Math.max(ground - 1, peak - Math.max(6, radius * 0.75f));
            if (crownBase > ground + 4) {
                stem(out, centerX, centerZ, Math.max(1.5f, radius * 0.10f), ground - 2, crownBase + 1);
            }
            dome(out, centerX, centerZ, radius, Math.max(radius * 0.78f, 3), crownBase, peak, color, variant);
            high = Math.max(high, (int) Math.ceil(peak));
        }
        return high;
    }

    private static boolean tooSteep(Bank[] banks, float u, float v, int step) {
        int a = banks[0].ground.top(), b = banks[1].ground.top();
        int c = banks[2].ground.top(), d = banks[3].ground.top();
        float dx = (d - a) * (1 - v) + (c - b) * v;
        float dz = (b - a) * (1 - u) + (c - d) * u;
        return dx * dx + dz * dz > step * step * 2.9f;
    }

    private static void stem(BufferBuilder out, float x, float z, float width, float bottom, float top) {
        float x0 = x - width, x1 = x + width, z0 = z - width, z1 = z + width;
        stemFace(out, x0, z0, x0, z1, bottom, top);
        stemFace(out, x0, z1, x1, z1, bottom, top);
        stemFace(out, x1, z1, x1, z0, bottom, top);
        stemFace(out, x1, z0, x0, z0, bottom, top);
    }

    private static void stemFace(BufferBuilder out, float x0, float z0, float x1, float z1, float bottom, float top) {
        int color = 0x3e4230;
        int material = HorizonTile.Texture.WOOD.ordinal();
        HorizonMesh.vertex(out, x0, bottom, z0, color, material);
        HorizonMesh.vertex(out, x1, bottom, z1, color, material);
        HorizonMesh.vertex(out, x1, top, z1, color, material);
        HorizonMesh.vertex(out, x0, top, z0, color, material);
    }

    static void dome(BufferBuilder out, float x, float z, float rx, float rz,
                     float bottom, float top, int color, int seed) {
        float shoulder = bottom + (top - bottom) * 0.62f;
        for (int i = 0; i < RING_X.length; i++) {
            int j = (i + 1) & 7;
            float a = 0.89f + (hash(seed + i * 61) & 7) * 0.027f;
            float b = 0.89f + (hash(seed + j * 61) & 7) * 0.027f;
            float ax = RING_X[i] * rx * a, az = RING_Z[i] * rz * a;
            float bx = RING_X[j] * rx * b, bz = RING_Z[j] * rz * b;
            int middleA = HorizonMesh.shade(color, 0.80 + RING_X[i] * 0.095 + RING_Z[i] * 0.07);
            int middleB = HorizonMesh.shade(color, 0.80 + RING_X[j] * 0.095 + RING_Z[j] * 0.07);
            int shade = HorizonMesh.shade(color, 0.50);
            HorizonMesh.vertex(out, x + ax * 0.56f, bottom, z + az * 0.56f, shade, LEAVES);
            HorizonMesh.vertex(out, x + bx * 0.56f, bottom, z + bz * 0.56f, shade, LEAVES);
            HorizonMesh.vertex(out, x + bx, shoulder, z + bz, middleB, LEAVES);
            HorizonMesh.vertex(out, x + ax, shoulder, z + az, middleA, LEAVES);
            HorizonMesh.vertex(out, x + ax, shoulder, z + az, middleA, LEAVES);
            HorizonMesh.vertex(out, x + bx, shoulder, z + bz, middleB, LEAVES);
            HorizonMesh.vertex(out, x, top, z, color, LEAVES);
            HorizonMesh.vertex(out, x, top, z, color, LEAVES);
        }
    }

    private record Bank(Span ground, Span trees) {}

    private static Bank bank(List<Span> spans, Layer layer) {
        Span ground = null, trees = null;
        for (var span : spans) {
            if (!span.water() && span.layer() == layer.ordinal()) {
                ground = span;
                break;
            }
        }
        if (ground != null) for (var span : spans) {
            if (span.water() && span.top() > ground.top() && span.top() - ground.top() <= 32) return new Bank(null, null);
            if (span.canopy() && Math.abs(span.bottom() - ground.top()) <= 1) {
                trees = span;
            }
        }
        return new Bank(ground, trees);
    }

    private static int hash(int value) {
        value ^= value >>> 16;
        value *= 0x45d9f3b;
        value ^= value >>> 16;
        return value * 0x45d9f3b ^ value >>> 12;
    }

    private HorizonCanopy() {}
}
