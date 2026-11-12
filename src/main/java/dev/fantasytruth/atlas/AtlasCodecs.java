package dev.fantasytruth.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.fantasytruth.terrain.AtlasProfile;

public final class AtlasCodecs {
    private static final AtlasProfile DEFAULT = AtlasProfile.DEFAULT;
    private static final MapCodec<AtlasProfile> BASE = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(-40000, 40000).optionalFieldOf("sea_level", DEFAULT.seaLevel()).forGetter(AtlasProfile::seaLevel),
            Codec.doubleRange(0.25, 16).optionalFieldOf("horizontal_scale", DEFAULT.horizontalScale()).forGetter(AtlasProfile::horizontalScale),
            Codec.doubleRange(0, 20).optionalFieldOf("relief", DEFAULT.relief()).forGetter(AtlasProfile::relief),
            Codec.doubleRange(0, 8).optionalFieldOf("foothill_detail", DEFAULT.foothillDetail()).forGetter(AtlasProfile::foothillDetail),
            Codec.doubleRange(0, 8).optionalFieldOf("rock_detail", DEFAULT.rockDetail()).forGetter(AtlasProfile::rockDetail),
            Codec.doubleRange(0, 240).optionalFieldOf("ravine_depth", DEFAULT.ravineDepth()).forGetter(AtlasProfile::ravineDepth),
            Codec.doubleRange(0.025, 0.9).optionalFieldOf("ravine_width", DEFAULT.ravineWidth()).forGetter(AtlasProfile::ravineWidth),
            Codec.doubleRange(0, 2).optionalFieldOf("caves", DEFAULT.caves()).forGetter(AtlasProfile::caves),
            Codec.doubleRange(-2, 2).optionalFieldOf("temperature_offset", DEFAULT.temperatureOffset()).forGetter(AtlasProfile::temperatureOffset),
            Codec.doubleRange(-2, 2).optionalFieldOf("moisture_offset", DEFAULT.moistureOffset()).forGetter(AtlasProfile::moistureOffset),
            Codec.doubleRange(0, 1).optionalFieldOf("tree_density", DEFAULT.treeDensity()).forGetter(AtlasProfile::treeDensity),
            Codec.intRange(64, 4096).optionalFieldOf("settlement_spacing", DEFAULT.settlementSpacing()).forGetter(AtlasProfile::settlementSpacing),
            Codec.doubleRange(0, 1).optionalFieldOf("settlement_density", DEFAULT.settlementDensity()).forGetter(AtlasProfile::settlementDensity),
            Codec.doubleRange(0, 2).optionalFieldOf("runoff_strength", DEFAULT.runoffStrength()).forGetter(AtlasProfile::runoffStrength),
            Codec.intRange(16, 128).optionalFieldOf("runoff_spacing", DEFAULT.runoffSpacing()).forGetter(AtlasProfile::runoffSpacing),
            Codec.intRange(4, 32).optionalFieldOf("runoff_steps", DEFAULT.runoffSteps()).forGetter(AtlasProfile::runoffSteps)
    ).apply(instance, AtlasProfile::new));

    private static final Codec<AtlasProfile.Shape> SHAPE = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 4000).optionalFieldOf("summit_limit", 420).forGetter(AtlasProfile.Shape::summitLimit),
            Codec.doubleRange(-0.35, 0.65).optionalFieldOf("land_bias", 0.24).forGetter(AtlasProfile.Shape::landBias),
            Codec.doubleRange(0, 1).optionalFieldOf("mountain_frequency", 0.22).forGetter(AtlasProfile.Shape::mountainFrequency)
    ).apply(instance, AtlasProfile.Shape::new));

    public static final Codec<AtlasProfile> PROFILE = RecordCodecBuilder.create(instance -> instance.group(
            BASE.forGetter((AtlasProfile value) -> value),
            SHAPE.optionalFieldOf("shape", AtlasProfile.Shape.LEGACY).forGetter(AtlasProfile::shape)
    ).apply(instance, AtlasProfile::withShape));

    private AtlasCodecs() {}
}
