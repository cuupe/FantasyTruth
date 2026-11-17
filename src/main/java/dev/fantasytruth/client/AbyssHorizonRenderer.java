package dev.fantasytruth.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.abyss.AbyssLayer;
import dev.fantasytruth.horizon.HorizonNetwork;
import dev.fantasytruth.horizon.HorizonTile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = FantasyTruth.ID, value = Dist.CLIENT)
public final class AbyssHorizonRenderer {
    private static final String PROGRAM = "fantasytruth_horizon";
    private static final int MAX_MESHES = 384;
    private static final int MAX_PENDING = 6;
    private static final int RETRY_TICKS = 120;
    private static final Map<HorizonTile, Integer> PENDING = new HashMap<>();
    private static final Map<HorizonTile, HorizonTile.Data> RECEIVED = new LinkedHashMap<>();
    private static final Map<HorizonTile, HorizonMesh> MESHES = new LinkedHashMap<>(MAX_MESHES, 0.75f, true);
    private static List<HorizonTile> visibleTiles = List.of();
    private static List<HorizonTile> coarseTiles = List.of();
    private static List<HorizonTile> renderTiles = List.of();
    private static ClientLevel world;
    private static ShaderInstance shader;
    private static int epoch, ticks, centerX, centerZ, near;

    @Mod.EventBusSubscriber(modid = FantasyTruth.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws IOException {
            var provider = event.getResourceProvider();
            var program = new ShaderInstance(id -> provider.getResource(id.getPath().startsWith("shaders/core/" + PROGRAM)
                    ? new ResourceLocation(FantasyTruth.ID, id.getPath()) : id), PROGRAM, DefaultVertexFormat.POSITION_COLOR);
            event.registerShader(program, loaded -> shader = loaded);
            HorizonNetwork.setReceiver(AbyssHorizonRenderer::receive);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (client.level != world) reset(client.level);
        if (!available(client)) return;
        ticks++;
        int x = client.player.chunkPosition().getMiddleBlockX();
        int z = client.player.chunkPosition().getMiddleBlockZ();
        int clip = 16 * Math.max(1, client.options.getEffectiveRenderDistance() - 2);
        if (near != clip || centerX != x || centerZ != z) {
            centerX = x;
            centerZ = z;
            near = clip;
            visibleTiles = select(x, z, clip);
            coarseTiles = visibleTiles.stream().map(HorizonTile::root).distinct()
                    .filter(root -> root.distance(centerX, centerZ) >= 3072).toList();
            renderTiles = displayTiles();
        }
        PENDING.values().removeIf(until -> ticks >= until);
        if (PENDING.size() >= MAX_PENDING) return;
        List<HorizonTile> priority = ticks % 3 == 0 ? coarseTiles : visibleTiles;
        if (!requestNext(priority)) requestNext(priority == coarseTiles ? visibleTiles : coarseTiles);
    }

    private static boolean requestNext(List<HorizonTile> tiles) {
        for (var tile : tiles) {
            if (MESHES.containsKey(tile) || PENDING.containsKey(tile) || RECEIVED.containsKey(tile)) continue;
            HorizonNetwork.request(world.dimension().location(), epoch, tile);
            PENDING.put(tile, ticks + RETRY_TICKS);
            return true;
        }
        return false;
    }

    static List<HorizonTile> select(int x, int z, int clip) {
        var result = new ArrayList<HorizonTile>();
        int max = HorizonTile.MAX_SIZE, range = HorizonTile.RANGE;
        for (int tx = Math.floorDiv(x - range, max) * max; tx <= x + range; tx += max) {
            for (int tz = Math.floorDiv(z - range, max) * max; tz <= z + range; tz += max) {
                collect(result, new HorizonTile(tx, tz, max), x, z, clip);
            }
        }
        result.sort(Comparator.comparingInt(tile -> tile.distance(x, z)));
        return result;
    }

    private static void collect(List<HorizonTile> result, HorizonTile tile, int x, int z, int clip) {
        int distance = tile.distance(x, z);
        if (!tile.withinRange(x, z) || (tile.x() >= x - clip && tile.z() >= z - clip
                && tile.x() + tile.size() <= x + clip && tile.z() + tile.size() <= z + clip)) return;
        if (tile.size() <= HorizonTile.MIN_SIZE || distance > tile.size()) {
            result.add(tile);
            return;
        }
        int half = tile.size() / 2;
        for (int dz = 0; dz <= half; dz += half) {
            for (int dx = 0; dx <= half; dx += half) {
                collect(result, new HorizonTile(tile.x() + dx, tile.z() + dz, half), x, z, clip);
            }
        }
    }

    private static void receive(HorizonNetwork.Response response) {
        if (world != null && response.epoch() == epoch && response.dimension().equals(world.dimension().location())
                && PENDING.remove(response.tile()) != null) {
            RECEIVED.put(response.tile(), response.data());
        }
    }

    private static void reset(ClientLevel level) {
        MESHES.values().forEach(HorizonMesh::close);
        MESHES.clear();
        PENDING.clear();
        RECEIVED.clear();
        visibleTiles = List.of();
        coarseTiles = List.of();
        renderTiles = List.of();
        near = 0;
        world = level;
        epoch++;
    }

    private static boolean available(Minecraft client) {
        if (world == null || client.player == null || client.getConnection() == null || shader == null) return false;
        return (world.dimension() == Level.OVERWORLD || AbyssLayer.of(world.dimension()) != null)
                && HorizonNetwork.available(client.getConnection().getConnection()) && !AbyssShaderPacks.active();
    }

    private static boolean clearView(net.minecraft.client.Camera camera) {
        return camera.getFluidInCamera() == FogType.NONE
                && (!(camera.getEntity() instanceof LivingEntity living)
                || !living.hasEffect(MobEffects.BLINDNESS) && !living.hasEffect(MobEffects.DARKNESS));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || MESHES.isEmpty()
                || !available(Minecraft.getInstance()) || !clearView(event.getCamera())) return;
        event.setNearPlaneDistance(Math.max(near, event.getNearPlaneDistance()));
        event.setFarPlaneDistance(HorizonTile.RANGE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS
                || !available(Minecraft.getInstance()) || !clearView(event.getCamera())) return;
        upload();
        if (MESHES.isEmpty()) return;
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack().last().pose();
        var tileView = new Matrix4f();
        var projection = event.getProjectionMatrix();
        float far = HorizonTile.RANGE + HorizonTile.MAX_SIZE;
        float clipNear = projection.m32() / (projection.m22() - 1);
        var cullingProjection = new Matrix4f(projection);
        cullingProjection.m22(-(far + clipNear) / (far - clipNear));
        cullingProjection.m32(-2 * far * clipNear / (far - clipNear));
        var frustum = new Frustum(pose, cullingProjection);
        frustum.prepare(camera.x, camera.y, camera.z);
        var previous = RenderSystem.getShader();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        try {
            var program = shader;
            float light = 1.0f - 0.74f * Math.min(1, world.getStarBrightness(event.getPartialTick()) * 2f);
            program.safeGetUniform("TerrainLight").set(world.dimension() == Level.OVERWORLD ? light : light * 0.82f);
            program.safeGetUniform("Clip").set((float) (centerX - camera.x), (float) (centerZ - camera.z),
                    (float) near, (float) HorizonTile.RANGE);
            for (var tile : renderTiles) {
                var mesh = MESHES.get(tile);
                if (mesh == null || mesh.buffer() == null || !frustum.isVisible(mesh.bounds())) continue;
                float x = (float) (tile.x() - camera.x), y = (float) -camera.y, z = (float) (tile.z() - camera.z);
                program.safeGetUniform("Origin").set(x, y, z);
                tileView.set(pose).translate(x, y, z);
                mesh.buffer().bind();
                mesh.buffer().drawWithShader(tileView, projection, program);
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShader(() -> previous);
        }
    }

    private static List<HorizonTile> displayTiles() {
        Set<HorizonTile> fallback = new HashSet<>();
        for (var tile : visibleTiles) {
            var root = tile.root();
            if (root.distance(centerX, centerZ) >= 3072 && !MESHES.containsKey(tile) && MESHES.containsKey(root)) fallback.add(root);
        }
        var display = new ArrayList<HorizonTile>(visibleTiles.size());
        display.addAll(fallback);
        for (var tile : visibleTiles) {
            if (!fallback.contains(tile.root())) display.add(tile);
        }
        return display;
    }

    private static void upload() {
        if (RECEIVED.isEmpty()) return;
        var iterator = RECEIVED.entrySet().iterator();
        for (int count = 0; count < 2 && iterator.hasNext(); count++) {
            var entry = iterator.next();
            MESHES.put(entry.getKey(), HorizonMesh.build(entry.getKey(), entry.getValue()));
            iterator.remove();
        }
        while (MESHES.size() > MAX_MESHES) {
            var oldest = MESHES.entrySet().iterator();
            oldest.next().getValue().close();
            oldest.remove();
        }
        renderTiles = displayTiles();
    }

    private AbyssHorizonRenderer() {}
}
