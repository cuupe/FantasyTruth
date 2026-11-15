package dev.fantasytruth.abyss;

/** Converts local Forest block coordinates to the depth scale used by the expedition. */
public final class ForestCoordinates {
    public static final int DEEP_MIN_Y = -1536;
    public static final int DEEP_EXIT = -1280;
    public static final int MOUTH_Y = 736;

    public static int depth(double y, int exit) {
        double progress = Math.max(0, Math.min(1, (MOUTH_Y - y) / (MOUTH_Y - exit)));
        return 1350 + (int) Math.round(progress * 1250);
    }

    private ForestCoordinates() {}
}
