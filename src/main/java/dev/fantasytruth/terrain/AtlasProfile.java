package dev.fantasytruth.terrain;

public record AtlasProfile(int seaLevel, double horizontalScale, double relief, double foothillDetail,
                           double rockDetail, double ravineDepth, double ravineWidth, double caves,
                           double temperatureOffset, double moistureOffset, double treeDensity,
                           int settlementSpacing, double settlementDensity, double runoffStrength,
                           int runoffSpacing, int runoffSteps, Shape shape) {
    public record Shape(int summitLimit, double landBias, double mountainFrequency) {
        public static final Shape LEGACY = new Shape(0, 0, 0.22);
        public static final Shape BALANCED = new Shape(420, 0.24, 0.22);
    }

    public static final AtlasProfile DEFAULT = new AtlasProfile(62, 1, 1, 1, 1, 36, 0.25, 1,
            0, 0, 0.85, 144, 0.65, 0, 48, 14);

    public AtlasProfile(int seaLevel, double horizontalScale, double relief, double foothillDetail,
                        double rockDetail, double ravineDepth, double ravineWidth, double caves,
                        double temperatureOffset, double moistureOffset, double treeDensity,
                        int settlementSpacing, double settlementDensity) {
        this(seaLevel, horizontalScale, relief, foothillDetail, rockDetail, ravineDepth, ravineWidth, caves,
                temperatureOffset, moistureOffset, treeDensity, settlementSpacing, settlementDensity, 0, 48, 14);
    }

    public AtlasProfile(int seaLevel, double horizontalScale, double relief, double foothillDetail,
                        double rockDetail, double ravineDepth, double ravineWidth, double caves,
                        double temperatureOffset, double moistureOffset, double treeDensity,
                        int settlementSpacing, double settlementDensity, double runoffStrength,
                        int runoffSpacing, int runoffSteps) {
        this(seaLevel, horizontalScale, relief, foothillDetail, rockDetail, ravineDepth, ravineWidth, caves,
                temperatureOffset, moistureOffset, treeDensity, settlementSpacing, settlementDensity,
                runoffStrength, runoffSpacing, runoffSteps, Shape.LEGACY);
    }

    public AtlasProfile withShape(Shape value) {
        return new AtlasProfile(seaLevel, horizontalScale, relief, foothillDetail, rockDetail, ravineDepth, ravineWidth, caves,
                temperatureOffset, moistureOffset, treeDensity, settlementSpacing, settlementDensity,
                runoffStrength, runoffSpacing, runoffSteps, value);
    }
}
