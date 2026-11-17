package dev.fantasytruth.horizon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

public record HorizonTile(int x, int z, int size) {
    public static final int MIN_SIZE = 64;
    public static final int MAX_SIZE = 2048;
    public static final int RANGE = 6144;
    public static final int MAX_SPANS = 12;
    public static final int STONE = 0x4f5556;
    private static final int LIQUID = 1 << 24;
    private static final int LAYER_SHIFT = 25;
    private static final int TEXTURE_SHIFT = 29;

    public enum Layer { GROUND, SHELF, CEILING, LEDGE, SCARP, CANOPY, INLET_UPPER, INLET_MIDDLE, INLET_LOWER, INLET_RIM, SNAPSHOT, CANOPY_COVER }
    public enum Texture { AUTO, STONE, SAND, LEAVES, GRASS, WOOD, PLANKS, SOIL }
    public static final int WATER = waterColor(0x314f7b);
    public static final int GRASS = 0x637d43;

    public int cells() { return size <= 256 ? 16 : 8; }
    public int step() { return size / cells(); }

    public int distance(int px, int pz) {
        return Math.max(Math.max(x - px, px - (x + size)), Math.max(z - pz, pz - (z + size)));
    }

    public boolean withinRange(int px, int pz) {
        return withinRange(px, pz, RANGE);
    }

    public boolean withinRange(int px, int pz, int range) {
        long dx = Math.max(0, Math.max(x - px, px - (x + size)));
        long dz = Math.max(0, Math.max(z - pz, pz - (z + size)));
        return dx * dx + dz * dz <= (long) range * range;
    }

    public boolean valid() {
        return size >= MIN_SIZE && size <= MAX_SIZE && Integer.bitCount(size) == 1
                && x % size == 0 && z % size == 0
                && Math.abs((long) x) <= 30_002_048 && Math.abs((long) z) <= 30_002_048;
    }

    public HorizonTile parent() {
        if (size == MAX_SIZE) return this;
        int parentSize = size * 2;
        return new HorizonTile(Math.floorDiv(x, parentSize) * parentSize, Math.floorDiv(z, parentSize) * parentSize, parentSize);
    }

    void write(FriendlyByteBuf buffer) {
        buffer.writeInt(x);
        buffer.writeInt(z);
        buffer.writeVarInt(size);
    }

    static HorizonTile read(FriendlyByteBuf buffer) {
        return new HorizonTile(buffer.readInt(), buffer.readInt(), buffer.readVarInt());
    }

    // Half-open, opaque vertical intervals. Water is represented by its distant surface.
    public static int waterColor(int color) { return LIQUID | (color & 0xffffff); }
    public static boolean isWaterColor(int color) { return (color & LIQUID) != 0; }

    public static int layerColor(int color, Layer layer) { return color | (layer.ordinal() << LAYER_SHIFT); }
    public static int snapshotColor(int color, Texture texture) {
        return layerColor(color, Layer.SNAPSHOT) | (texture.ordinal() << TEXTURE_SHIFT);
    }

    public record Span(int bottom, int top, int color) {
        public int layer() { return (color >>> LAYER_SHIFT) & 15; }
        public int texture() { return color >>> TEXTURE_SHIFT; }
        public boolean snapshot() { return layer() == Layer.SNAPSHOT.ordinal(); }
        public boolean ceiling() { return layer() == Layer.CEILING.ordinal(); }
        public boolean canopy() { return layer() == Layer.CANOPY.ordinal() || layer() == Layer.CANOPY_COVER.ordinal(); }
        public boolean tree() { return layer() == Layer.CANOPY.ordinal(); }
        public boolean water() { return isWaterColor(color); }
    }

    public record Data(int samples, boolean forest, boolean fine, List<List<Span>> columns) {
        public boolean continuousSurface() {
            int floor = 0;
            for (int i = 0; i < columns.size(); i++) {
                var spans = columns.get(i);
                if (spans.isEmpty() || spans.get(0).water()) return false;
                for (int j = 1; j < spans.size(); j++) {
                    if (!spans.get(j).water() && !spans.get(j).canopy()) return false;
                }
                if (i == 0) floor = spans.get(0).bottom;
                else if (floor != spans.get(0).bottom) return false;
            }
            return true;
        }
        public int index(int x, int z) {
            int offset = fine ? 1 : 0;
            return (z + offset) * samples + x + offset;
        }

        public List<Span> column(int x, int z) { return columns.get(index(x, z)); }

        void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(forest);
            buffer.writeBoolean(fine);
            for (var column : columns) {
                buffer.writeByte(column.size());
                for (Span span : column) {
                    buffer.writeShort(span.bottom);
                    buffer.writeShort(span.top);
                    buffer.writeInt(span.color);
                }
            }
        }

        static Data read(FriendlyByteBuf buffer, HorizonTile tile) {
            boolean forest = buffer.readBoolean();
            boolean fine = buffer.readBoolean();
            int samples = tile.cells() + (fine ? 3 : 1);
            var columns = new ArrayList<List<Span>>(samples * samples);
            for (int i = 0; i < samples * samples; i++) {
                int count = buffer.readUnsignedByte();
                if (count > MAX_SPANS) throw new IllegalArgumentException("Invalid horizon column");
                var spans = new ArrayList<Span>(count);
                for (int j = 0; j < count; j++) {
                    spans.add(new Span(buffer.readShort(), buffer.readShort(), buffer.readInt()));
                }
                columns.add(spans);
            }
            return new Data(samples, forest, fine, columns);
        }
    }
}
