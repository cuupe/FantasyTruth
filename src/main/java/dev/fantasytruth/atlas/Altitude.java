package dev.fantasytruth.atlas;

/** The native height window has margins on both sides of each owned interval. */
public record Altitude(long page, int localY) {
    public static final int MIN_LOCAL = -1024;
    public static final int HEIGHT = 2032;
    public static final int STRIDE = 1792;
    public static final int HALF = STRIDE / 2;
    public static final long LIMIT = Long.MAX_VALUE - 4096;

    public static Altitude at(long y) {
        if (y < -LIMIT || y > LIMIT) {
            throw new IllegalArgumentException("Y is outside the signed altitude range");
        }
        long page = Math.floorDiv(y, STRIDE);
        int local = Math.floorMod(y, STRIDE);
        if (local >= HALF) {
            return new Altitude(page + 1, local - STRIDE);
        }
        return new Altitude(page, local);
    }

    public long worldY() {
        return Math.addExact(Math.multiplyExact(page, STRIDE), localY);
    }
}
