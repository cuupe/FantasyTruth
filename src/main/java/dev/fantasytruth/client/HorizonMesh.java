package dev.fantasytruth.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fantasytruth.horizon.HorizonTile;
import dev.fantasytruth.horizon.HorizonTile.Layer;
import dev.fantasytruth.horizon.HorizonTile.Span;
import java.util.List;
import net.minecraft.world.phys.AABB;

record HorizonMesh(VertexBuffer buffer, AABB bounds) implements AutoCloseable {
    private static final int WATER = 0, ROCK = 1, SAND = 2, LEAVES = 3, GRASS = 4;
    private static final Layer[] FOREST_STRATA = {Layer.GROUND, Layer.SHELF, Layer.LEDGE, Layer.CEILING,
            Layer.INLET_UPPER, Layer.INLET_MIDDLE, Layer.INLET_LOWER, Layer.INLET_RIM};

    record Baked(BufferBuilder.RenderedBuffer rendered, AABB bounds) {
        HorizonMesh upload() {
            if (rendered == null) return new HorizonMesh(null, null);
            var buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            try {
                buffer.bind();
                buffer.upload(rendered);
                return new HorizonMesh(buffer, bounds);
            } catch (RuntimeException error) {
                buffer.close();
                throw error;
            } finally {
                VertexBuffer.unbind();
            }
        }

        void discard() {
            if (rendered != null) rendered.release();
        }
    }

    static Baked bake(HorizonTile tile, HorizonTile.Data data, BufferBuilder out) {
        int step = tile.step();
        int cells = tile.cells();
        out.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        boolean continuous = data.continuousSurface();
        Span ocean = continuous ? uniformlyFlooded(data) : null;
        if (ocean != null) {
            waterQuad(out, 0, 0, tile.size(), tile.size(), ocean.top() - 0.12f, ocean.color());
            return finish(tile, out, ocean.top() - 1, ocean.top());
        }
        ForestGrid forest = data.forest() ? ForestGrid.index(data) : null;
        if (forest != null) {
            low = forest.low();
            high = forest.high();
        } else if (data.fine()) {
            for (var column : data.columns()) for (var span : column) {
                low = Math.min(low, span.bottom());
                high = Math.max(high, span.top());
            }
        }
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                int x0 = x * step, z0 = z * step, x1 = x0 + step, z1 = z0 + step;
                var column = data.column(x, z);
                var southColumn = data.column(x, z + 1);
                var cornerColumn = data.column(x + 1, z + 1);
                var eastColumn = data.column(x + 1, z);
                if (step <= 16) {
                    for (var span : column) {
                        if (!span.tree()) continue;
                        crown(out, x0, z0, step, span, (tile.x() + x0) * 6173 ^ (tile.z() + z0) * 5531);
                        low = Math.min(low, span.bottom());
                        high = Math.max(high, span.top());
                    }
                } else {
                    high = Math.max(high, HorizonCanopy.scatter(out, tile, x, z, Layer.GROUND,
                            column, southColumn, cornerColumn, eastColumn));
                    if (forest != null) {
                        high = Math.max(high, HorizonCanopy.scatter(out, tile, x, z, Layer.SHELF,
                                column, southColumn, cornerColumn, eastColumn));
                    }
                }
                if (forest != null) {
                    int index = data.index(x, z);
                    int south = index + data.samples();
                    for (Layer layer : FOREST_STRATA) {
                        var spans = forest.layers()[layer.ordinal()];
                        if (spans == null) continue;
                        stratum(out, x0, z0, x1, z1, tile.size(), layer,
                                spans[index], spans[south], spans[south + 1], spans[index + 1]);
                    }
                    if (step <= 32) for (var span : column) {
                        if (span.water()) waterQuad(out, x0, z0, x1, z1, span.top() - 0.12f, span.color());
                    }
                    if (data.fine()) HorizonVoxelMesh.cell(out, data, x, z, step, true);
                    continue;
                }
                if (data.fine() && (step < 16 || !continuous)) {
                    HorizonVoxelMesh.cell(out, data, x, z, step, false);
                    continue;
                }
                if (continuous) {
                    Span a = column.get(0), b = southColumn.get(0), c = cornerColumn.get(0), d = eastColumn.get(0);
                    low = Math.min(low, Math.min(Math.min(a.top(), b.top()), Math.min(c.top(), d.top())) - step);
                    high = Math.max(high, Math.max(Math.max(a.top(), b.top()), Math.max(c.top(), d.top())));
                    Span wet = firstWater(column, southColumn, cornerColumn, eastColumn);
                    if (wet != null) {
                        high = Math.max(high, wet.top());
                        water(out, x0, z0, x1, z1, a, b, c, d, wet);
                        if (Math.max(Math.max(a.top(), b.top()), Math.max(c.top(), d.top())) < wet.top()) continue;
                    }
                    surface(out, x0, z0, x1, z1, a, b, c, d);
                    int drop = Math.min(20, step);
                    if (x == 0) skirt(out, drop, x0, z0, x0, z1, a, b, wet);
                    if (x == cells - 1) skirt(out, drop, x1, z1, x1, z0, c, d, wet);
                    if (z == 0) skirt(out, drop, x1, z0, x0, z0, d, a, wet);
                    if (z == cells - 1) skirt(out, drop, x0, z1, x1, z1, b, c, wet);
                    int quarter = cells / 4;
                    if (x > 0 && x % quarter == 0) coverageSkirt(out, drop, x0, z0, x0, z1, a, b, wet, 1);
                    if (x < cells - 1 && (x + 1) % quarter == 0) coverageSkirt(out, drop, x1, z1, x1, z0, c, d, wet, 2);
                    if (z > 0 && z % quarter == 0) coverageSkirt(out, drop, x1, z0, x0, z0, d, a, wet, 3);
                    if (z < cells - 1 && (z + 1) % quarter == 0) coverageSkirt(out, drop, x0, z1, x1, z1, b, c, wet, 4);
                    continue;
                }
                for (Span span : column) {
                    if (span.canopy()) continue;
                    low = Math.min(low, span.bottom());
                    high = Math.max(high, span.top());
                    if (span.water()) {
                        waterQuad(out, x0, z0, x1, z1, span.top() - 0.12f, span.color());
                        continue;
                    }
                    Span south = adjacent(span, southColumn, step);
                    Span east = adjacent(span, eastColumn, step);
                    Span corner = adjacent(span, cornerColumn, step);
                    Span b = south == null ? span : south;
                    Span c = corner == null ? span : corner;
                    Span d = east == null ? span : east;
                    low = Math.min(low, Math.min(Math.min(b.bottom(), c.bottom()), d.bottom()));
                    high = Math.max(high, Math.max(Math.max(b.top(), c.top()), d.top()));
                    surface(out, x0, z0, x1, z1, span, b, c, d);
                    underside(out, x0, z0, x1, z1, span, b, c, d);
                    if (x == 0 || adjacent(span, data.column(x - 1, z), step) == null)
                        edge(out, x0, z0, x0, z1, span, b, 0.72, x == 0);
                    if (x == cells - 1 || east == null)
                        edge(out, x1, z1, x1, z0, c, d, 0.80, x == cells - 1);
                    if (z == 0 || adjacent(span, data.column(x, z - 1), step) == null)
                        edge(out, x1, z0, x0, z0, d, span, 0.86, z == 0);
                    if (z == cells - 1 || south == null)
                        edge(out, x0, z1, x1, z1, b, c, 0.68, z == cells - 1);
                }
            }
        }
        return finish(tile, out, low, high);
    }

    private static void coverageSkirt(BufferBuilder out, int drop, int x0, int z0, int x1, int z1,
                                      Span a, Span b, Span water, int side) {
        if (water != null && Math.max(a.top(), b.top()) <= water.top()) return;
        skirtFaces(out, new StratumPoint(x0, z0, a), new StratumPoint(x1, z1, b),
                a.top(), b.top(), -drop, side, shade(a.color(), 0.75));
    }

    private static Baked finish(HorizonTile tile, BufferBuilder out, int low, int high) {
        var rendered = out.endOrDiscardIfEmpty();
        if (rendered == null) return new Baked(null, null);
        return new Baked(rendered, new AABB(tile.x() - 20, low, tile.z() - 20,
                tile.x() + tile.size() + 20, high, tile.z() + tile.size() + 20));
    }

    private record ForestGrid(Span[][] layers, int low, int high) {
        static ForestGrid index(HorizonTile.Data data) {
            var layers = new Span[Layer.values().length][];
            int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
            for (int i = 0; i < data.columns().size(); i++) {
                for (var span : data.columns().get(i)) {
                    low = Math.min(low, span.bottom() - 12);
                    high = Math.max(high, span.top());
                    int id = span.layer();
                    if (span.water() || span.canopy() || span.snapshot() || id >= layers.length) continue;
                    var layer = layers[id];
                    if (layer == null) layers[id] = layer = new Span[data.columns().size()];
                    if (layer[i] == null) layer[i] = span;
                }
            }
            return new ForestGrid(layers, low, high);
        }
    }

    private static void stratum(BufferBuilder out, int x0, int z0, int x1, int z1, int size,
                                Layer layer, Span a, Span b, Span c, Span d) {
        if (a == null && b == null && c == null && d == null) return;
        boolean ground = layer == Layer.GROUND;
        if (a != null && b != null && c != null && d != null) {
            surface(out, x0, z0, x1, z1, a, b, c, d);
            if (!ground) underside(out, x0, z0, x1, z1, a, b, c, d);
        } else {
            // Every shared edge uses the same endpoints and midpoint regardless of the adjacent cell.
            var first = new StratumPoint(x0, z0, a);
            var south = new StratumPoint(x0, z1, b);
            var corner = new StratumPoint(x1, z1, c);
            var east = new StratumPoint(x1, z0, d);
            stratumTriangle(out, ground, first, south, corner);
            stratumTriangle(out, ground, corner, east, first);
        }
        int quarter = size / 4;
        boolean maskEdges = size >= 256 && (layer == Layer.CEILING || layer == Layer.SHELF);
        if (x0 == 0 || maskEdges && x0 % quarter == 0)
            boundary(out, x0, z0, x0, z1, a, b, ground, x0 == 0 ? 0 : 1);
        if (x1 == size || maskEdges && x1 % quarter == 0)
            boundary(out, x1, z1, x1, z0, c, d, ground, x1 == size ? 0 : 2);
        if (z0 == 0 || maskEdges && z0 % quarter == 0)
            boundary(out, x1, z0, x0, z0, d, a, ground, z0 == 0 ? 0 : 3);
        if (z1 == size || maskEdges && z1 % quarter == 0)
            boundary(out, x0, z1, x1, z1, b, c, ground, z1 == size ? 0 : 4);
    }

    private record StratumPoint(int x, int z, Span span, float insetX, float insetZ) {
        StratumPoint(int x, int z, Span span) {
            this(x, z, span, 0, 0);
        }

        StratumPoint midpoint(StratumPoint other) {
            var solid = span != null ? this : other;
            var rock = solid.span;
            int step = Math.max(Math.abs(x - other.x), Math.abs(z - other.z));
            int midX = (x + other.x) / 2, midZ = (z + other.z) / 2;
            float insetX = 0, insetZ = 0;
            // A shared edge has the same taper whichever side emitted the clipped face.
            if (step >= 16 && rock.layer() != Layer.GROUND.ordinal() && !rock.ceiling()) {
                int thickness = rock.top() - rock.bottom();
                boolean shelf = rock.layer() == Layer.SHELF.ordinal() || rock.layer() == Layer.LEDGE.ordinal();
                int inset = Math.min((thickness - 1) / (shelf ? 3 : 5), Math.min(shelf ? 24 : 12, step / 4));
                if (shelf && thickness >= 32) {
                    insetX = (solid.x - midX) * 0.35f;
                    insetZ = (solid.z - midZ) * 0.35f;
                }
                rock = new Span(rock.bottom() + inset / 2, rock.top() - inset, rock.color());
            }
            return new StratumPoint(midX, midZ, rock, insetX, insetZ);
        }
    }

    private static void stratumTriangle(BufferBuilder out, boolean ground, StratumPoint a, StratumPoint b, StratumPoint c) {
        var triangle = new StratumPoint[]{a, b, c};
        var clipped = new StratumPoint[4];
        StratumPoint enter = null, leave = null;
        int count = 0;
        var previous = c;
        for (var next : triangle) {
            if ((previous.span != null) != (next.span != null)) {
                var point = previous.midpoint(next);
                clipped[count++] = point;
                if (next.span != null) enter = point;
                else leave = point;
            }
            if (next.span != null) clipped[count++] = next;
            previous = next;
        }
        if (count < 3) return;
        for (int i = 0; i < 4; i++) {
            var point = clipped[Math.min(i, count - 1)];
            var span = point.span;
            int material = span.ceiling() ? ROCK : GRASS;
            vertex(out, point.x, span.top(), point.z, shade(span.color(), 0.94), material);
        }
        if (!ground) for (int i = 0; i < 4; i++) {
            var point = clipped[Math.max(0, count - 1 - i)];
            vertex(out, point.x, point.span.bottom(), point.z, shade(point.span.color(), 0.45));
        }
        if (leave != null && enter != null) wall(out, leave, enter, ground);
    }

    private static void boundary(BufferBuilder out, int x0, int z0, int x1, int z1, Span a, Span b, boolean ground, int seam) {
        if (a == null && b == null) return;
        var first = new StratumPoint(x0, z0, a);
        var last = new StratumPoint(x1, z1, b);
        if (a == null) first = first.midpoint(last);
        if (b == null) last = first.midpoint(last);
        int span = Math.abs(x1 - x0) + Math.abs(z1 - z0);
        int depth = Math.min(first.span.ceiling() ? 48 : 18, Math.max(2, span / 2));
        skirtFaces(out, first, last, first.span.top(), last.span.top(), -depth, seam, shade(HorizonTile.STONE, 0.74));
        if (!ground) skirtFaces(out, first, last, first.span.bottom(), last.span.bottom(),
                depth, seam, shade(HorizonTile.STONE, 0.47));
    }

    private static void skirtFaces(BufferBuilder out, StratumPoint a, StratumPoint b, int y0, int y1,
                                   int offset, int seam, int color) {
        vertex(out, a.x, y0, a.z, color, ROCK, seam);
        vertex(out, b.x, y1, b.z, color, ROCK, seam);
        vertex(out, b.x, y1 + offset, b.z, color, ROCK, seam);
        vertex(out, a.x, y0 + offset, a.z, color, ROCK, seam);
        vertex(out, b.x, y1, b.z, color, ROCK, seam);
        vertex(out, a.x, y0, a.z, color, ROCK, seam);
        vertex(out, a.x, y0 + offset, a.z, color, ROCK, seam);
        vertex(out, b.x, y1 + offset, b.z, color, ROCK, seam);
    }

    private static void wall(BufferBuilder out, StratumPoint a, StratumPoint b, boolean ground) {
        int lowA = ground ? Math.max(a.span.bottom(), a.span.top() - 12) : a.span.bottom();
        int lowB = ground ? Math.max(b.span.bottom(), b.span.top() - 12) : b.span.bottom();
        int color = shade(HorizonTile.STONE, a.span.ceiling() ? 0.48 : 0.76);
        vertex(out, a.x, lowA, a.z, color);
        vertex(out, b.x, lowB, b.z, color);
        if (a.insetX != 0 || a.insetZ != 0 || b.insetX != 0 || b.insetZ != 0) {
            float waistA = lowA + (a.span.top() - lowA) * 0.58f;
            float waistB = lowB + (b.span.top() - lowB) * 0.58f;
            int middle = shade(HorizonTile.STONE, 0.66);
            vertex(out, b.x + b.insetX, waistB, b.z + b.insetZ, middle, ROCK);
            vertex(out, a.x + a.insetX, waistA, a.z + a.insetZ, middle, ROCK);
            vertex(out, a.x + a.insetX, waistA, a.z + a.insetZ, middle, ROCK);
            vertex(out, b.x + b.insetX, waistB, b.z + b.insetZ, middle, ROCK);
        }
        vertex(out, b.x, b.span.top(), b.z, color);
        vertex(out, a.x, a.span.top(), a.z, color);
    }

    private static void surface(BufferBuilder out, int x0, int z0, int x1, int z1,
                                Span a, Span b, Span c, Span d) {
        double dx = (c.top() + d.top() - a.top() - b.top()) / (2.0 * (x1 - x0));
        double dz = (b.top() + c.top() - a.top() - d.top()) / (2.0 * (z1 - z0));
        double light = 0.82 + 0.18 * (1 + 0.42 * (dx - dz)) / Math.sqrt(1 + dx * dx + dz * dz);
        int material = material(a, b, c, d, dx * dx + dz * dz);
        if (smoothTop(x1 - x0, a, b, c, d)) {
            smoothFace(out, x0, z0, x1, z1, a, b, c, d, material, false);
            return;
        }
        vertex(out, x0, a.top(), z0, shade(a.color(), Math.min(1, light)), material);
        vertex(out, x0, b.top(), z1, shade(b.color(), Math.min(1, light)), material);
        vertex(out, x1, c.top(), z1, shade(c.color(), Math.min(1, light)), material);
        vertex(out, x1, d.top(), z0, shade(d.color(), Math.min(1, light)), material);
    }

    private static boolean smoothTop(int step, Span a, Span b, Span c, Span d) {
        return step >= 16 && Math.abs(a.top() + c.top() - b.top() - d.top()) > 2;
    }

    private static void smoothFace(BufferBuilder out, int x0, int z0, int x1, int z1,
                                   Span a, Span b, Span c, Span d, int material, boolean underside) {
        float width = x1 - x0;
        int ay = underside ? a.bottom() : a.top(), by = underside ? b.bottom() : b.top();
        int cy = underside ? c.bottom() : c.top(), dy = underside ? d.bottom() : d.top();
        var points = new SurfacePoint[9];
        for (int z = 0; z < 3; z++) {
            float v = z * 0.5f;
            for (int x = 0; x < 3; x++) {
                float u = x * 0.5f;
                float height = blend(ay, by, cy, dy, u, v);
                double dx = ((dy - ay) * (1 - v) + (cy - by) * v) / width;
                double dz = ((by - ay) * (1 - u) + (cy - dy) * u) / width;
                double light = underside ? 0.45
                        : 0.90 + 0.10 * (1 + 0.36 * (dx - dz)) / Math.sqrt(1 + dx * dx + dz * dz);
                int color = (int) blend(red(a), red(b), red(c), red(d), u, v) << 16
                        | (int) blend(green(a), green(b), green(c), green(d), u, v) << 8
                        | (int) blend(blue(a), blue(b), blue(c), blue(d), u, v);
                points[z * 3 + x] = new SurfacePoint(x0 + u * width, height,
                        z0 + v * (z1 - z0), shade(color, Math.min(1, light)));
            }
        }
        for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
            int i = z * 3 + x;
            points[i].emit(out, material);
            points[i + (underside ? 1 : 3)].emit(out, material);
            points[i + 4].emit(out, material);
            points[i + (underside ? 3 : 1)].emit(out, material);
        }
    }

    private static float blend(int a, int b, int c, int d, float u, float v) {
        return (1 - v) * (a * (1 - u) + d * u) + v * (b * (1 - u) + c * u);
    }

    private record SurfacePoint(float x, float y, float z, int color) {
        void emit(BufferBuilder out, int material) {
            vertex(out, x, y, z, color, material);
        }
    }

    private static int material(Span a, Span b, Span c, Span d, double slope) {
        if (a.ceiling() || slope > 1.85) return ROCK;
        int red = red(a) + red(b) + red(c) + red(d);
        int green = green(a) + green(b) + green(c) + green(d);
        int blue = blue(a) + blue(b) + blue(c) + blue(d);
        if (green > red + 15 && green > blue + 30) return GRASS;
        return red > blue + 48 ? SAND : ROCK;
    }

    private static int red(Span span) { return span.color() >> 16 & 255; }
    private static int green(Span span) { return span.color() >> 8 & 255; }
    private static int blue(Span span) { return span.color() & 255; }

    private static void crown(BufferBuilder out, int x, int z, int step, Span span, int variant) {
        int height = span.top() - span.bottom();
        boolean tall = height >= 14;
        variant ^= variant >>> 13;
        float radius = tall ? Math.min(11, 4 + height * 0.19f) : step <= 4 ? 2.8f : 3.7f;
        float centerX = x + (tall ? (variant & 7) - 3 : step * 0.35f);
        float centerZ = z + (tall ? (variant >>> 4 & 7) - 3 : step * 0.35f);
        float y = span.top(), shoulder = y - Math.min(8, height * 0.54f);
        if (height >= 6) {
            float trunk = tall ? 0.85f + height * 0.024f : 0.52f;
            float xA = centerX - trunk, zA = centerZ - trunk;
            float xB = centerX + trunk, zB = centerZ + trunk;
            stemSide(out, xA, zA, xA, zB, span.bottom(), shoulder, 0x433e2d);
            stemSide(out, xB, zB, xB, zA, span.bottom(), shoulder, 0x3a3529);
            stemSide(out, xA, zB, xB, zB, span.bottom(), shoulder, 0x39362b);
            stemSide(out, xB, zA, xA, zA, span.bottom(), shoulder, 0x514536);
        }
        if (step == 16) {
            HorizonCanopy.dome(out, centerX, centerZ, radius, radius * 0.84f, shoulder, y, span.color(), variant);
            return;
        }
        float middle = (shoulder + y) * 0.5f;
        foliageBox(out, centerX, centerZ, radius, shoulder, middle, shade(span.color(), 0.94));
        foliageBox(out, centerX + radius * ((variant & 8) == 0 ? 0.18f : -0.18f),
                centerZ + radius * ((variant & 64) == 0 ? -0.2f : 0.2f), radius * 0.67f, middle, y, span.color());
        if (tall) {
            foliageBox(out, centerX + radius * ((variant & 16) == 0 ? 0.72f : -0.72f),
                    centerZ + radius * ((variant & 32) == 0 ? -0.32f : 0.32f),
                    radius * 0.56f, shoulder - 1, middle + 1, shade(span.color(), 0.89));
        }
    }

    private static void foliageBox(BufferBuilder out, float x, float z, float radius, float shoulder, float y, int color) {
        float x0 = x - radius, x1 = x + radius, z0 = z - radius, z1 = z + radius;
        vertex(out, x0, y, z0, color, LEAVES);
        vertex(out, x0, y, z1, color, LEAVES);
        vertex(out, x1, y, z1, color, LEAVES);
        vertex(out, x1, y, z0, color, LEAVES);
        int sides = shade(color, 0.80);
        crownSide(out, x0, z0, x0, z1, x0, z0, x0, z1, shoulder, y, sides, color);
        crownSide(out, x0, z1, x1, z1, x0, z1, x1, z1, shoulder, y, sides, color);
        crownSide(out, x1, z1, x1, z0, x1, z1, x1, z0, shoulder, y, sides, color);
        crownSide(out, x1, z0, x0, z0, x1, z0, x0, z0, shoulder, y, sides, color);
        int bottom = shade(color, 0.67);
        vertex(out, x0, shoulder, z0, bottom, LEAVES);
        vertex(out, x1, shoulder, z0, bottom, LEAVES);
        vertex(out, x1, shoulder, z1, bottom, LEAVES);
        vertex(out, x0, shoulder, z1, bottom, LEAVES);
    }

    private static void stemSide(BufferBuilder out, float x0, float z0, float x1, float z1,
                                 float ground, float crown, int color) {
        vertex(out, x0, ground, z0, color, ROCK);
        vertex(out, x1, ground, z1, color, ROCK);
        vertex(out, x1, crown, z1, color, ROCK);
        vertex(out, x0, crown, z0, color, ROCK);
    }

    private static void crownSide(BufferBuilder out, float x0, float z0, float x1, float z1,
                                  float tx0, float tz0, float tx1, float tz1, float shoulder, float y, int sides, int top) {
        vertex(out, x0, shoulder, z0, sides, LEAVES);
        vertex(out, x1, shoulder, z1, sides, LEAVES);
        vertex(out, tx1, y, tz1, top, LEAVES);
        vertex(out, tx0, y, tz0, top, LEAVES);
    }

    private static void skirt(BufferBuilder out, int drop, int x0, int z0, int x1, int z1, Span a, Span b, Span water) {
        if (water != null && Math.max(a.top(), b.top()) <= water.top()) return;
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
            if (other.water() || span.layer() != other.layer()
                    || Math.min(span.top(), other.top()) <= Math.max(span.bottom(), other.bottom())) continue;
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
        if (x1 - x0 >= 16 && Math.abs(a.bottom() + c.bottom() - b.bottom() - d.bottom()) > 2) {
            smoothFace(out, x0, z0, x1, z1, a, b, c, d, ROCK, true);
            return;
        }
        vertex(out, x0, a.bottom(), z0, shade(a.color(), 0.45));
        vertex(out, x1, d.bottom(), z0, shade(d.color(), 0.45));
        vertex(out, x1, c.bottom(), z1, shade(c.color(), 0.45));
        vertex(out, x0, b.bottom(), z1, shade(b.color(), 0.45));
    }

    private static void edge(BufferBuilder out, int x0, int z0, int x1, int z1, Span a, Span b,
                             double light, boolean tileBoundary) {
        int color = shade(HorizonTile.STONE, a.ceiling() ? 0.48 : light);
        vertex(out, x0, a.bottom(), z0, color);
        vertex(out, x1, b.bottom(), z1, color);
        vertex(out, x1, b.top(), z1, color);
        vertex(out, x0, a.top(), z0, color);
        if (a.ceiling() && tileBoundary) {
            // Close the underside at a change of detail level from both viewing directions.
            vertex(out, x1, b.bottom(), z1, color);
            vertex(out, x0, a.bottom(), z0, color);
            vertex(out, x0, a.top(), z0, color);
            vertex(out, x1, b.top(), z1, color);
        }
    }

    private static Span uniformlyFlooded(HorizonTile.Data data) {
        var water = wet(data.column(0, 0));
        if (water == null) return null;
        for (var column : data.columns()) {
            if (!water.equals(wet(column)) || column.get(0).top() >= water.top()) return null;
        }
        return water;
    }

    private static Span wet(List<Span> column) {
        for (var span : column) if (span.water()) return span;
        return null;
    }

    private static Span firstWater(List<Span> a, List<Span> b, List<Span> c, List<Span> d) {
        var span = wet(a);
        if (span == null) span = wet(b);
        if (span == null) span = wet(c);
        if (span == null) span = wet(d);
        return span;
    }

    private static void water(BufferBuilder out, int x0, int z0, int x1, int z1,
                              Span a, Span b, Span c, Span d, Span liquid) {
        float y = liquid.top() - 0.12f;
        if (a.top() <= y && b.top() <= y && c.top() <= y && d.top() <= y) {
            waterQuad(out, x0, z0, x1, z1, y, liquid.color());
            return;
        }
        // Clip the sea plane against each land triangle, keeping the shoreline and leaving no vertical water borders.
        if (smoothTop(x1 - x0, a, b, c, d)) {
            var grid = new EdgePoint[9];
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                float u = col * 0.5f, v = row * 0.5f;
                grid[row * 3 + col] = new EdgePoint(x0 + u * (x1 - x0),
                        blend(a.top(), b.top(), c.top(), d.top(), u, v), z0 + v * (z1 - z0));
            }
            for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++) {
                int index = row * 3 + col;
                waterTriangle(out, y, liquid.color(), grid[index], grid[index + 3], grid[index + 4]);
                waterTriangle(out, y, liquid.color(), grid[index + 4], grid[index + 1], grid[index]);
            }
            return;
        }
        var p = new EdgePoint(x0, a.top(), z0);
        var q = new EdgePoint(x0, b.top(), z1);
        var r = new EdgePoint(x1, c.top(), z1);
        var t = new EdgePoint(x1, d.top(), z0);
        waterTriangle(out, y, liquid.color(), p, q, r);
        waterTriangle(out, y, liquid.color(), r, t, p);
    }

    private record EdgePoint(float x, float y, float z) {}

    private static void waterTriangle(BufferBuilder out, float y, int color, EdgePoint a, EdgePoint b, EdgePoint c) {
        var points = new EdgePoint[]{a, b, c};
        var clipped = new EdgePoint[4];
        int size = 0;
        var previous = c;
        for (var next : points) {
            if ((previous.y <= y) != (next.y <= y)) {
                float f = (y - previous.y) / (next.y - previous.y);
                clipped[size++] = new EdgePoint(previous.x + f * (next.x - previous.x), y, previous.z + f * (next.z - previous.z));
            }
            if (next.y <= y) clipped[size++] = next;
            previous = next;
        }
        if (size < 3) return;
        for (int i = 0; i < 4; i++) {
            var point = clipped[Math.min(i, size - 1)];
            vertex(out, point.x, y, point.z, color, WATER);
        }
    }

    private static void waterQuad(BufferBuilder out, int x0, int z0, int x1, int z1, float y, int color) {
        vertex(out, x0, y, z0, color, WATER);
        vertex(out, x0, y, z1, color, WATER);
        vertex(out, x1, y, z1, color, WATER);
        vertex(out, x1, y, z0, color, WATER);
    }

    static int shade(int color, double factor) {
        return (int) ((color >> 16 & 255) * factor) << 16
                | (int) ((color >> 8 & 255) * factor) << 8 | (int) ((color & 255) * factor);
    }

    private static void vertex(BufferBuilder out, int x, int y, int z, int color) {
        vertex(out, x, y, z, color, ROCK);
    }

    static void vertex(BufferBuilder out, float x, float y, float z, int color, int material) {
        vertex(out, x, y, z, color, material, 0);
    }

    private static void vertex(BufferBuilder out, float x, float y, float z, int color, int material, int seam) {
        out.vertex(x, y, z).color(color >> 16 & 255, color >> 8 & 255, color & 255, material * 16 + seam).endVertex();
    }

    @Override public void close() { if (buffer != null) buffer.close(); }
}
