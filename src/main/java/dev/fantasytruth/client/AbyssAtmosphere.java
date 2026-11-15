package dev.fantasytruth.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.abyss.AbyssLayer;
import dev.fantasytruth.abyss.AbyssTransit;
import dev.fantasytruth.abyss.OrthIsland;
import java.io.IOException;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FantasyTruth.ID, value = Dist.CLIENT)
public final class AbyssAtmosphere {
    private static final String SHADER_NAME = "fantasytruth_abyss_air";
    private static ShaderInstance airShader;

    private record Atmosphere(Air air, float weight, float surfaceOnly) {}

    private record Color(float r, float g, float b) {
        Color(int rgb) { this((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F); }
        Color mix(Color other, float t) {
            return new Color(Mth.lerp(t, r, other.r), Mth.lerp(t, g, other.g), Mth.lerp(t, b, other.b));
        }
    }

    private record Air(Color upper, Color horizon, Color lower, float clearDistance, float cloud, float aperture) {
        Air(int upper, int horizon, int lower, float clearDistance, float cloud, float aperture) {
            this(new Color(upper), new Color(horizon), new Color(lower), clearDistance, cloud, aperture);
        }

        Air mix(Air other, float t) {
            return new Air(upper.mix(other.upper, t), horizon.mix(other.horizon, t), lower.mix(other.lower, t),
                    Mth.lerp(t, clearDistance, other.clearDistance), Mth.lerp(t, cloud, other.cloud),
                    Mth.lerp(t, aperture, other.aperture));
        }

        Color towards(float vertical) {
            return vertical >= 0 ? horizon.mix(upper, smooth(vertical) * 0.65F)
                    : horizon.mix(lower, smooth(-vertical));
        }
    }

    private static final Air MOUTH = new Air(0xbccdd5, 0xa9bab9, 0x91a7ab, 0.62F, 0.27F, 0.30F);
    private static final Air NIGHT = new Air(0x171e30, 0x111723, 0x0c121a, 0.87F, 0.12F, 0.0F);
    private static final Air[] EDGE = {
            new Air(0xd7e5ee, 0xbbd1df, 0x9bb6c9, 0.87F, 0.34F, 0.39F),
            new Air(0xd6e3d7, 0xb8cccb, 0x96b1b9, 0.86F, 0.32F, 0.34F),
            new Air(0xc7dce1, 0xa5c0cd, 0x82a1b2, 0.83F, 0.35F, 0.28F),
            new Air(0xd1e2e6, 0xadc8d3, 0x89acbd, 0.87F, 0.34F, 0.28F),
            new Air(0xbfd4cf, 0x9cb9bd, 0x7c9ba4, 0.84F, 0.33F, 0.21F),
            new Air(0xc0d5d9, 0x9db8c5, 0x7f9eae, 0.83F, 0.32F, 0.16F),
            new Air(0x9ebcb7, 0x7d9fa3, 0x64878d, 0.78F, 0.30F, 0.09F)
    };
    private static final int[] EDGE_DEPTHS = {0, 220, 420, 600, 820, 1080, 1350};
    private static final Air FOREST = new Air(0x8da99b, 0x6c8d7e, 0x38594e, 0.67F, 0.17F, 0.08F);
    private static final Air LOW_FOREST = new Air(0x819f91, 0x617f73, 0x345347, 0.60F, 0.15F, 0.07F);
    private static final Air INVERTED_FOREST = new Air(0x45444e, 0x393745, 0x302d42, 0.58F, 0.18F, 0.08F);
    private static final Air FAULT = new Air(0xa6afbc, 0x79889a, 0x515c71, 0.48F, 0.33F, 0.08F);
    private static final Air GOBLETS = new Air(0xa9bcb1, 0x779c92, 0x577f83, 0.50F, 0.31F, 0.12F);
    private static final Air SEA = new Air(0x807b8e, 0x5a627b, 0x353f58, 0.42F, 0.27F, 0.06F);
    private static final Air CAPITAL = new Air(0xcbb18d, 0xaa8b76, 0x716977, 0.53F, 0.26F, 0.17F);
    private static final Air MAELSTROM = new Air(0x7e8596, 0x60697d, 0x414961, 0.37F, 0.32F, 0.05F);

    @Mod.EventBusSubscriber(modid = FantasyTruth.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws IOException {
            // The 1.20.1 loader opens core shaders by their unqualified program name.
            var provider = event.getResourceProvider();
            var shader = new ShaderInstance(id -> provider.getResource(
                    id.getPath().startsWith("shaders/core/" + SHADER_NAME)
                            ? new ResourceLocation(FantasyTruth.ID, id.getPath()) : id),
                    SHADER_NAME, DefaultVertexFormat.POSITION);
            event.registerShader(shader, loaded -> airShader = loaded);
        }

        private Shaders() {}
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (AbyssShaderPacks.active()) return;
        var atmosphere = atmosphere(event.getCamera(), (float) event.getPartialTick());
        if (atmosphere == null) return;
        Color fog = atmosphere.air.towards(event.getCamera().getLookVector().y());
        event.setRed(Mth.lerp(atmosphere.weight, event.getRed(), fog.r));
        event.setGreen(Mth.lerp(atmosphere.weight, event.getGreen(), fog.g));
        event.setBlue(Mth.lerp(atmosphere.weight, event.getBlue(), fog.b));
    }

    @SubscribeEvent
    public static void fogDistance(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || AbyssShaderPacks.active()) return;
        var atmosphere = atmosphere(event.getCamera(), (float) event.getPartialTick());
        if (atmosphere == null) return;
        event.setNearPlaneDistance(Mth.lerp(atmosphere.weight, event.getNearPlaneDistance(),
                event.getFarPlaneDistance() * atmosphere.air.clearDistance));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void sky(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || airShader == null || AbyssShaderPacks.active()) return;
        Camera camera = event.getCamera();
        var atmosphere = atmosphere(camera, event.getPartialTick());
        if (atmosphere == null) return;
        Air air = atmosphere.air;
        var projection = event.getProjectionMatrix();
        if (projection.m00() <= 0 || projection.m11() <= 0) return;
        Vec3 pos = camera.getPosition();

        Vec3 aperture = new Vec3(AbyssLayer.CENTER_X - pos.x, Math.max(86, 168 - pos.y), AbyssLayer.CENTER_Z - pos.z).normalize();
        var program = airShader;
        program.safeGetUniform("Forward").set(camera.getLookVector());
        program.safeGetUniform("Left").set(camera.getLeftVector());
        program.safeGetUniform("Up").set(camera.getUpVector());
        program.safeGetUniform("ViewScale").set(projection.m00(), projection.m11());
        program.safeGetUniform("LightDirection").set((float) aperture.x, (float) aperture.y, (float) aperture.z);
        program.safeGetUniform("CameraOffset").set((float) pos.x * 0.0018F, (float) pos.y * 0.0018F, (float) pos.z * 0.0018F);
        uniform(program, "UpperColor", air.upper);
        uniform(program, "HorizonColor", air.horizon);
        uniform(program, "LowerColor", air.lower);
        program.safeGetUniform("CloudStrength").set(air.cloud);
        program.safeGetUniform("ApertureStrength").set(air.aperture);
        program.safeGetUniform("SurfaceOnly").set(atmosphere.surfaceOnly);
        program.safeGetUniform("AtmosphereWeight").set(atmosphere.weight);

        var previous = RenderSystem.getShader();
        RenderSystem.depthMask(false);
        try {
            RenderSystem.setShader(() -> program);
            var vertices = Tesselator.getInstance().getBuilder();
            vertices.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            vertices.vertex(-1, -1, 0.9999).endVertex();
            vertices.vertex(1, -1, 0.9999).endVertex();
            vertices.vertex(1, 1, 0.9999).endVertex();
            vertices.vertex(-1, 1, 0.9999).endVertex();
            BufferUploader.drawWithShader(vertices.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(() -> previous);
        }
    }

    private static void uniform(ShaderInstance shader, String name, Color color) {
        shader.safeGetUniform(name).set(color.r, color.g, color.b);
    }

    public static boolean replacesHorizon() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && !AbyssShaderPacks.active() && (AbyssLayer.of(mc.level.dimension()) != null
                || mc.player != null && mouth(mc.level, mc.player.position()));
    }

    public static boolean hidesVanillaClouds(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || AbyssShaderPacks.active()) return false;
        return AbyssLayer.of(level.dimension()) != null || mouth(level, new Vec3(x, y, z));
    }

    private static boolean mouth(ClientLevel level, Vec3 pos) {
        return level.dimension() == Level.OVERWORLD && level.dimensionTypeId().location().equals(AbyssTransit.EXPEDITION)
                && pos.y < 330 && OrthIsland.inShaft(pos.x, pos.z);
    }

    private static Atmosphere atmosphere(Camera camera, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || camera.getFluidInCamera() != FogType.NONE) return null;
        if (camera.getEntity() instanceof LivingEntity entity
                && (entity.hasEffect(MobEffects.BLINDNESS) || entity.hasEffect(MobEffects.DARKNESS))) return null;
        var layer = AbyssLayer.of(level.dimension());
        Vec3 pos = camera.getPosition();
        boolean surface = level.dimension() == Level.OVERWORLD && level.dimensionTypeId().location().equals(AbyssTransit.EXPEDITION);
        boolean edge = layer == AbyssLayer.EDGE && level.dimensionTypeId().location().equals(AbyssTransit.EDGE_TYPE);
        if (surface || edge) {
            float weight = surface ? surfaceWeight(pos) : 1F;
            if (weight <= 0F) return null;
            float daylight = smooth(Mth.clamp((Mth.cos(level.getSunAngle(partialTick)) + 0.15F) / 0.44F, 0F, 1F));
            daylight *= 1F - 0.42F * level.getRainLevel(partialTick) - 0.18F * level.getThunderLevel(partialTick);
            float surfaceOnly = smooth(Mth.clamp(((float) pos.y - 16F) / 128F, 0F, 1F));
            return new Atmosphere(NIGHT.mix(collar(pos.y), daylight), weight, surfaceOnly);
        }
        if (layer == null) return null;
        Air air = switch (layer) {
            case EDGE -> edge((layer.entrance - pos.y) * 1350 / (layer.entrance - layer.exit));
            case FOREST -> level.dimensionTypeId().location().getPath().equals("abyss_forest_deep")
                    ? collar(-1350 - (736 - pos.y)).mix(FOREST, smooth(Mth.clamp((736F - (float) pos.y) / 116F, 0F, 1F)))
                        .mix(LOW_FOREST, smooth(Mth.clamp((300F - (float) pos.y) / 950F, 0F, 1F)))
                    : level.dimensionTypeId().location().getPath().equals("abyss_forest")
                    ? collar(-1350 - (736 - pos.y)).mix(FOREST, smooth(Mth.clamp((736F - (float) pos.y) / 116F, 0F, 1F)))
                        .mix(INVERTED_FOREST, smooth(Mth.clamp((145F - (float) pos.y) / 490F, 0F, 1F)))
                    : FOREST;
            case FAULT -> FAULT;
            case GOBLETS -> GOBLETS;
            case SEA -> SEA;
            case CAPITAL -> CAPITAL;
            case MAELSTROM -> MAELSTROM;
        };
        return new Atmosphere(air, 1F, 0F);
    }

    private static float surfaceWeight(Vec3 pos) {
        if (pos.y >= 330 || !AbyssLayer.passage(pos.x, pos.z, 650)) return 0F;
        float altitude = smooth(Mth.clamp((330F - (float) pos.y) / 110F, 0F, 1F));
        float setback = (float) OrthIsland.shaftSetback(pos.x, pos.z);
        return altitude * (1F - smooth(Mth.clamp(setback / 20F, 0F, 1F)));
    }

    private static Air collar(double y) {
        if (y >= 144) return MOUTH;
        if (y <= -75) return edge(-y);
        float blend = smooth((float) Math.max(0, Math.min(1, (144 - y) / 219)));
        return MOUTH.mix(edge(-y), blend);
    }

    private static Air edge(double depth) {
        if (depth <= 0) return EDGE[0];
        for (int i = 1; i < EDGE_DEPTHS.length; i++) {
            if (depth <= EDGE_DEPTHS[i]) {
                return EDGE[i - 1].mix(EDGE[i], smooth((float) ((depth - EDGE_DEPTHS[i - 1])
                        / (EDGE_DEPTHS[i] - EDGE_DEPTHS[i - 1]))));
            }
        }
        return EDGE[EDGE.length - 1];
    }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }

    private AbyssAtmosphere() {}
}
