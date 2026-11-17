package dev.fantasytruth.horizon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

public record HorizonTile(int x, int z, int size) {
    public static final int CELLS = 8;
    public static final int SAMPLES = CELLS + 1;
    public static final int MIN_SIZE = 64;
    public static final int MAX_SIZE = 2048;
    public static final int RANGE = 6144;
    public static final int MAX_SPANS = 12;
    public static final int STONE = 0x4f5556;
    public static final int WATER = 0x345e82;
    public static final int GRASS = 0x637d43;

    public int step() { return size / CELLS; }

    public int distance(int px, int pz) {
        return Math.max(Math.max(x - px, px - (x + size)), Math.max(z - pz, pz - (z + size)));
    }

    public boolean withinRange(int px, int pz) {
        long dx = Math.max(0, Math.max(x - px, px - (x + size)));
        long dz = Math.max(0, Math.max(z - pz, pz - (z + size)));
        return dx * dx + dz * dz <= (long) RANGE * RANGE;
    }

    public boolean valid() {
        return size >= MIN_SIZE && size <= MAX_SIZE && Integer.bitCount(size) == 1
                && x % size == 0 && z % size == 0
                && Math.abs((long) x) <= 30_002_048 && Math.abs((long) z) <= 30_002_048;
    }

    public HorizonTile root() {
        return new HorizonTile(Math.floorDiv(x, MAX_SIZE) * MAX_SIZE, Math.floorDiv(z, MAX_SIZE) * MAX_SIZE, MAX_SIZE);
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
    public record Span(int bottom, int top, int color) {}

    public record Data(List<List<Span>> columns) {
        public boolean continuousSurface() {
            int floor = 0;
            for (int i = 0; i < columns.size(); i++) {
                var spans = columns.get(i);
                if (spans.size() != 1) return false;
                if (i == 0) floor = spans.get(0).bottom;
                else if (floor != spans.get(0).bottom) return false;
            }
            return true;
        }
        public List<Span> column(int x, int z) { return columns.get(z * SAMPLES + x); }

        void write(FriendlyByteBuf buffer) {
            for (var column : columns) {
                buffer.writeByte(column.size());
                for (Span span : column) {
                    buffer.writeShort(span.bottom);
                    buffer.writeShort(span.top);
                    buffer.writeMedium(span.color);
                }
            }
        }

        static Data read(FriendlyByteBuf buffer) {
            var columns = new ArrayList<List<Span>>(SAMPLES * SAMPLES);
            for (int i = 0; i < SAMPLES * SAMPLES; i++) {
                int count = buffer.readUnsignedByte();
                if (count > MAX_SPANS) throw new IllegalArgumentException("Invalid horizon column");
                var spans = new ArrayList<Span>(count);
                for (int j = 0; j < count; j++) {
                    spans.add(new Span(buffer.readShort(), buffer.readShort(), buffer.readUnsignedMedium()));
                }
                columns.add(spans);
            }
            return new Data(columns);
        }
    }
}
