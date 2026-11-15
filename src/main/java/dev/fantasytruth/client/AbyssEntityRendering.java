package dev.fantasytruth.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.abyss.AbyssEntities;
import dev.fantasytruth.abyss.CorpseWeeper;
import dev.fantasytruth.abyss.Demonfish;
import dev.fantasytruth.abyss.EdgeFlyer;
import dev.fantasytruth.abyss.Silkfang;
import net.minecraft.world.entity.Mob;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Adapts the bundled source rig to Minecraft's model parts and keyframe animation. */
@Mod.EventBusSubscriber(modid = FantasyTruth.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AbyssEntityRendering {
    private static final ModelLayerLocation FISH_LAYER =
            new ModelLayerLocation(new ResourceLocation(FantasyTruth.ID, "demonfish"), "main");
    private static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(FantasyTruth.ID, "corpse_weeper"), "main");

    private static final ModelLayerLocation HAMMERBEAK = layer("hammerbeak");
    private static final ModelLayerLocation SILKFANG = layer("silkfang");
    private static final ModelLayerLocation SPLITJAW = layer("crimson_splitjaw");
    private static final ModelLayerLocation INBYO = layer("inbyo");
    private static final ModelLayerLocation OTTOBAS = layer("ottobas");
    private static final ModelLayerLocation ROHANA = layer("rohana");

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(new ResourceLocation(FantasyTruth.ID, name), "main");
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, () -> mesh("e_weeper"));
        event.registerLayerDefinition(FISH_LAYER, () -> mesh("e_demonfish"));
        event.registerLayerDefinition(HAMMERBEAK, () -> mesh("e_hammerbeak"));
        event.registerLayerDefinition(SILKFANG, () -> mesh("e_silkfang"));
        event.registerLayerDefinition(SPLITJAW, () -> mesh("e_splitjaw"));
        event.registerLayerDefinition(INBYO, () -> mesh("e_inbyo"));
        event.registerLayerDefinition(OTTOBAS, () -> mesh("e_ottobas"));
        event.registerLayerDefinition(ROHANA, () -> mesh("e_rohana"));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AbyssEntities.WEEPER.get(),
                context -> new RigRenderer<>(context, LAYER, "e_weeper", 0.65F, 1.30F));
        event.registerEntityRenderer(AbyssEntities.DEMONFISH.get(),
                context -> new RigRenderer<>(context, FISH_LAYER, "e_demonfish", 0.35F, 1.18F));
        event.registerEntityRenderer(AbyssEntities.HAMMERBEAK.get(),
                context -> new RigRenderer<>(context, HAMMERBEAK, "e_hammerbeak", 0.4F, 0.89F));
        event.registerEntityRenderer(AbyssEntities.SILKFANG.get(),
                context -> new RigRenderer<>(context, SILKFANG, "e_silkfang", 0.62F, 1.04F));
        event.registerEntityRenderer(AbyssEntities.SPLITJAW.get(),
                context -> new RigRenderer<>(context, SPLITJAW, "e_splitjaw", 1.0F, 1.47F));
        event.registerEntityRenderer(AbyssEntities.INBYO.get(),
                context -> new RigRenderer<>(context, INBYO, "e_inbyo", 0.56F, 0.98F, true));
        event.registerEntityRenderer(AbyssEntities.OTTOBAS.get(),
                context -> new RigRenderer<>(context, OTTOBAS, "e_ottobas", 0.90F, 0.91F));
        event.registerEntityRenderer(AbyssEntities.ROHANA.get(),
                context -> new RigRenderer<>(context, ROHANA, "e_rohana", 0, 0.66F, true));
    }

    private static LayerDefinition mesh(String asset) {
        var geo = read("models/entity/" + asset + ".geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var mesh = new MeshDefinition();
        var parts = new HashMap<String, PartDefinition>();
        var pivots = new HashMap<String, Vector3f>();
        for (var element : geo.getAsJsonArray("bones")) {
            var bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            String parent = bone.has("parent") ? bone.get("parent").getAsString() : "";
            var pivot = vector(bone.getAsJsonArray("pivot"));
            var offset = new Vector3f(pivot).sub(pivots.getOrDefault(parent, new Vector3f(0, 24, 0)));
            var unrotated = CubeListBuilder.create();
            var rotated = new java.util.ArrayList<JsonObject>();
            if (bone.has("cubes")) for (var entry : bone.getAsJsonArray("cubes")) {
                var cube = entry.getAsJsonObject();
                if (cube.has("rotation")) rotated.add(cube);
                else addCube(unrotated, cube, pivot);
            }
            var part = parts.getOrDefault(parent, mesh.getRoot()).addOrReplaceChild(name, unrotated,
                    PartPose.offset(offset.x, -offset.y, offset.z));
            int partIndex = 0;
            for (var cube : rotated) {
                var anchor = cube.has("pivot") ? vector(cube.getAsJsonArray("pivot")) : pivot;
                var shift = new Vector3f(anchor).sub(pivot);
                var rotation = vector(cube.getAsJsonArray("rotation")).mul(Mth.DEG_TO_RAD);
                var shape = CubeListBuilder.create();
                addCube(shape, cube, anchor);
                part.addOrReplaceChild("cube_" + partIndex++, shape,
                        PartPose.offsetAndRotation(shift.x, -shift.y, shift.z, -rotation.x, rotation.y, -rotation.z));
            }
            parts.put(name, part);
            pivots.put(name, pivot);
        }
        var description = geo.getAsJsonObject("description");
        return LayerDefinition.create(mesh, description.get("texture_width").getAsInt(),
                description.get("texture_height").getAsInt());
    }

    private static void addCube(CubeListBuilder builder, JsonObject cube, Vector3f pivot) {
        var origin = vector(cube.getAsJsonArray("origin")).sub(pivot);
        var size = vector(cube.getAsJsonArray("size"));
        var uv = cube.getAsJsonArray("uv");
        float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0;
        builder.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                .addBox(origin.x, -origin.y - size.y, origin.z, size.x, size.y, size.z, new CubeDeformation(inflate));
    }

    private static Map<String, AnimationDefinition> animations(String asset) {
        var result = new HashMap<String, AnimationDefinition>();
        read("animations/entity/" + asset + ".animation.json").getAsJsonObject("animations").entrySet().forEach(animation -> {
            var clip = animation.getValue().getAsJsonObject();
            var definition = AnimationDefinition.Builder.withLength(clip.get("animation_length").getAsFloat());
            if (clip.has("loop") && clip.get("loop").getAsBoolean()) definition.looping();
            for (var bone : clip.getAsJsonObject("bones").entrySet()) {
                for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                    boolean rotation = channel.getKey().equals("rotation");
                    var frames = channel.getValue().getAsJsonObject().entrySet().stream()
                            .sorted(java.util.Comparator.comparingDouble(frame -> Double.parseDouble(frame.getKey())))
                            .map(frame -> {
                                var value = vector(frame.getValue().getAsJsonObject().getAsJsonArray("vector"));
                                var coordinates = rotation ? KeyframeAnimations.degreeVec(-value.x, value.y, -value.z)
                                        : KeyframeAnimations.posVec(value.x, value.y, value.z);
                                return new Keyframe(Float.parseFloat(frame.getKey()), coordinates, AnimationChannel.Interpolations.LINEAR);
                            }).toArray(Keyframe[]::new);
                    definition.addAnimation(bone.getKey(), new AnimationChannel(
                            rotation ? AnimationChannel.Targets.ROTATION : AnimationChannel.Targets.POSITION, frames));
                }
            }
            result.put(animation.getKey().substring(animation.getKey().lastIndexOf('.') + 1), definition.build());
        });
        return Map.copyOf(result);
    }

    private static JsonObject read(String file) {
        String path = "/assets/stratacubes/" + file;
        try (var reader = new InputStreamReader(Objects.requireNonNull(AbyssEntityRendering.class.getResourceAsStream(path), path),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not read " + path, error);
        }
    }

    private static Vector3f vector(JsonArray values) {
        return new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
    }

    private static final class RigRenderer<T extends Mob> extends MobRenderer<T, RigModel<T>> {
        private final ResourceLocation texture;
        private final float scale;

        RigRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context,
                    ModelLayerLocation layer, String asset, float shadow, float scale) {
            this(context, layer, asset, shadow, scale, false);
        }

        RigRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context,
                    ModelLayerLocation layer, String asset, float shadow, float scale, boolean emissive) {
            super(context, new RigModel<>(context.bakeLayer(layer), asset), shadow);
            texture = new ResourceLocation(FantasyTruth.ID, "textures/entity/" + asset + ".png");
            this.scale = scale;
            if (emissive) {
                var pass = RenderType.eyes(new ResourceLocation(FantasyTruth.ID, "textures/entity/" + asset + "_emissive.png"));
                addLayer(new RenderLayer<T, RigModel<T>>(this) {
                    @Override public void render(PoseStack poses, MultiBufferSource buffers, int light, T entity,
                                                 float stride, float amount, float partialTicks, float age, float yaw, float pitch) {
                        if (!entity.isInvisible()) getParentModel().renderToBuffer(poses, buffers.getBuffer(pass),
                                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
                    }
                });
            }
        }
        @Override public ResourceLocation getTextureLocation(T entity) { return texture; }
        @Override protected void scale(T entity, PoseStack poses, float partialTicks) { poses.scale(scale, scale, scale); }
    }

    private static final class RigModel<T extends Mob> extends HierarchicalModel<T> {
        private final Map<String, AnimationDefinition> clips;
        private final ModelPart root;
        private final Vector3f scratch = new Vector3f();

        RigModel(ModelPart root, String asset) {
            this.root = root;
            clips = animations(asset);
        }

        @Override public ModelPart root() { return root; }

        @Override public void setupAnim(T entity, float stride, float amount, float age, float yaw, float pitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            String clip = entity instanceof CorpseWeeper bird
                    ? (bird.flying() ? "fly" : amount > 0.08F ? "walk" : "idle")
                    : entity instanceof EdgeFlyer ? "fly"
                    : entity instanceof Silkfang ? (amount > 0.53F ? "run" : amount > 0.08F ? "walk" : "idle")
                    : entity.getType() == AbyssEntities.ROHANA.get() ? "fly"
                    : entity.isInWater() ? "swim"
                    : (entity.getType() == AbyssEntities.INBYO.get() || entity.getType() == AbyssEntities.OTTOBAS.get())
                        && amount > 0.08F ? "walk" : "idle";
            long time = (long) (age * 50);
            if (entity.deathTime > 0) {
                clip = "death";
                time = entity.deathTime * 50L;
            } else if (entity.hurtTime > 0) {
                clip = "hurt";
                time = (10 - entity.hurtTime) * 50L;
            } else if (entity instanceof CorpseWeeper bird && bird.attackAnimation.isStarted()) {
                animate(bird.attackAnimation, clips.get("attack"), age);
                return;
            } else if (entity instanceof Demonfish fish && fish.attackAnimation.isStarted()) {
                animate(fish.attackAnimation, clips.get("attack"), age);
                return;
            }
            if (entity.deathTime == 0 && entity.hurtTime == 0) {
                if (entity instanceof EdgeFlyer flyer && flyer.strikeAnimation.isStarted()) {
                    animate(flyer.strikeAnimation, clips.get(entity.getType() == AbyssEntities.SPLITJAW.get() ? "dive" : "attack"), age);
                    return;
                }
                if (entity instanceof Silkfang silk && silk.strikeAnimation.isStarted()) {
                    animate(silk.strikeAnimation, clips.get("attack"), age);
                    return;
                }
            }
            var animation = clips.getOrDefault(clip, clips.get("idle"));
            if (animation != null) KeyframeAnimations.animate(this, animation, time, 1, scratch);
            if (clip.equals("idle") || clip.equals("walk")) getAnyDescendantWithName("part_head").ifPresent(head ->
                    head.yRot += Mth.clamp(yaw, -30, 30) * Mth.DEG_TO_RAD);
        }
    }

    private AbyssEntityRendering() {}
}
