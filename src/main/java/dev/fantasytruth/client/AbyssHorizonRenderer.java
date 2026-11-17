package dev.fantasytruth.client;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.abyss.AbyssLayer;
import dev.fantasytruth.horizon.HorizonClientConfig;
import dev.fantasytruth.horizon.HorizonNetwork;
import dev.fantasytruth.horizon.HorizonTile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.FrustumIntersection;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = FantasyTruth.ID, value = Dist.CLIENT)
public final class AbyssHorizonRenderer {
    private static final String PROGRAM = "fantasytruth_horizon";
    private static final int MAX_MESHES = 384;
    private static final int MAX_PENDING = 8;
    private static final int MAX_MESH_BACKLOG = 8;
    private static final int RETRY_TICKS = 48;
    private static final int OVERLAP = 64;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService BAKER = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "FantasyTruth horizon mesh");
        thread.setDaemon(true);
        return thread;
    });
    private static BufferBuilder bakeBuffer;
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final FrustumIntersection FRUSTUM = new FrustumIntersection();
    private static final Map<HorizonTile, Integer> PENDING = new HashMap<>();
    private static final Map<HorizonTile, HorizonTile.Data> RECEIVED = new LinkedHashMap<>();
    private static final LinkedHashSet<HorizonTile> DIRTY_DETAILS = new LinkedHashSet<>();
    private static final Map<HorizonTile, HorizonMesh> MESHES = new LinkedHashMap<>(MAX_MESHES, 0.75f, true);
    private static List<HorizonTile> visibleTiles = List.of();
    private static HorizonCoverage.Layout layout = new HorizonCoverage.Layout(List.of());
    private static List<HorizonTile> coarseTiles = List.of();
    private static List<HorizonTile> rootTiles = List.of();
    private static List<HorizonTile> detailPriority = List.of();
    private static List<HorizonTile> coveragePriority = List.of();
    private static List<HorizonTile> rootPriority = List.of();
    private record DrawTile(HorizonTile tile, HorizonMesh mesh, int coverage) {}
    private record Bake(HorizonTile tile, int epoch, CompletableFuture<HorizonMesh.Baked> future) {}
    private static Bake bake;
    private static List<DrawTile> renderTiles = List.of();
    private static ClientLevel world;
    private static ShaderInstance shader;
    private static ShaderInstance materialOwner;
    private static int epoch, ticks, centerX, centerZ, near, range;
    private static int coveredRange, drawRange;
    private static float priorityYaw;

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
        if (bake != null && bake.epoch() != epoch) finishBake();
        if (!available(client)) return;
        ticks++;
        int x = client.player.chunkPosition().getMiddleBlockX();
        int z = client.player.chunkPosition().getMiddleBlockZ();
        int clip = 16 * Math.max(1, client.options.getEffectiveRenderDistance() - 2);
        int distance = HorizonClientConfig.distanceBlocks();
        boolean changed = distance != range;
        if (changed) changeRange(distance, x, z);
        boolean moved = changed || near != clip || centerX != x || centerZ != z;
        if (moved) {
            centerX = x;
            centerZ = z;
            near = clip;
            visibleTiles = select(x, z, clip, range);
            DIRTY_DETAILS.removeIf(tile -> tile.distance(x, z) > clip + 64);
            layout = new HorizonCoverage.Layout(visibleTiles);
            coarseTiles = HorizonCoverage.ancestors(visibleTiles, false);
            rootTiles = HorizonCoverage.ancestors(visibleTiles, true);
            updateCoverage();
        }
        drawRange = Math.min(coveredRange, Math.max(near, drawRange) + 128);
        if (range <= near) return;
        PENDING.values().removeIf(until -> ticks >= until);
        float direction = client.player.getYRot();
        if (moved || Math.abs(Mth.wrapDegrees(direction - priorityYaw)) >= 12.0f) {
            priorityYaw = direction;
            float yaw = (float) Math.toRadians(direction);
            detailPriority = prioritized(visibleTiles, -Math.sin(yaw), Math.cos(yaw), true);
            coveragePriority = prioritized(coarseTiles, -Math.sin(yaw), Math.cos(yaw), false);
            rootPriority = prioritized(rootTiles, -Math.sin(yaw), Math.cos(yaw), false);
        }
        if (PENDING.size() >= MAX_PENDING || RECEIVED.size() >= MAX_MESH_BACKLOG) return;
        if (requestNext(rootPriority)) return;
        if (ticks % 4 == 0 && refreshDetail()) return;
        boolean fillCoverage = !coveragePriority.isEmpty() && (MESHES.size() < coveragePriority.size() || ticks % 3 == 0);
        if (fillCoverage) {
            if (!requestNext(coveragePriority)) requestNext(detailPriority);
        } else if (!requestNext(detailPriority)) {
            requestNext(coveragePriority);
        }
    }

    private static boolean requestNext(List<HorizonTile> tiles) {
        for (var tile : tiles) {
            if (MESHES.containsKey(tile) || PENDING.containsKey(tile) || RECEIVED.containsKey(tile)
                    || bake != null && bake.epoch() == epoch && bake.tile().equals(tile)) continue;
            HorizonNetwork.request(world.dimension().location(), epoch, tile, false);
            PENDING.put(tile, ticks + RETRY_TICKS);
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (world == null || event.getLevel() != world || range <= near
                || world.dimension() != Level.OVERWORLD && world.dimension() != AbyssLayer.FOREST.key) return;
        var pos = event.getChunk().getPos();
        for (int size = HorizonTile.MIN_SIZE; size <= 128; size *= 2) {
            int x = Math.floorDiv(pos.getMinBlockX(), size) * size;
            int z = Math.floorDiv(pos.getMinBlockZ(), size) * size;
            var tile = new HorizonTile(x, z, size);
            if (MESHES.containsKey(tile) || PENDING.containsKey(tile) || RECEIVED.containsKey(tile)
                    || bake != null && bake.epoch() == epoch && bake.tile().equals(tile)) {
                DIRTY_DETAILS.add(tile);
            }
        }
    }

    private static boolean refreshDetail() {
        var iterator = DIRTY_DETAILS.iterator();
        while (iterator.hasNext()) {
            var tile = iterator.next();
            if (!visibleTiles.contains(tile) || PENDING.containsKey(tile) || RECEIVED.containsKey(tile)
                    || bake != null && bake.epoch() == epoch && bake.tile().equals(tile)) continue;
            iterator.remove();
            if (!MESHES.containsKey(tile)) continue;
            HorizonNetwork.request(world.dimension().location(), epoch, tile, true);
            PENDING.put(tile, ticks + RETRY_TICKS);
            return true;
        }
        return false;
    }

    private record Priority(HorizonTile tile, double score) {}

    private static List<HorizonTile> prioritized(List<HorizonTile> tiles, double dx, double dz, boolean detail) {
        var priority = new ArrayList<Priority>(tiles.size());
        for (var tile : tiles) {
            double x = tile.x() + tile.size() * 0.5 - centerX;
            double z = tile.z() + tile.size() * 0.5 - centerZ;
            double facing = (x * dx + z * dz) / Math.max(1, Math.hypot(x, z));
            int distance = tile.distance(centerX, centerZ);
            if (detail && tile.size() < 1024 && distance > 640 && facing < -0.38) continue;
            priority.add(new Priority(tile, (distance + 96) * (1.0 - 0.54 * facing)));
        }
        priority.sort(Comparator.comparingDouble(Priority::score));
        return priority.stream().map(Priority::tile).toList();
    }

    static List<HorizonTile> select(int x, int z, int clip, int range) {
        if (range <= clip) return List.of();
        var result = new ArrayList<HorizonTile>();
        int max = HorizonTile.MAX_SIZE;
        int inner = Math.max(0, clip - OVERLAP);
        for (int tx = Math.floorDiv(x - range, max) * max; tx <= x + range; tx += max) {
            for (int tz = Math.floorDiv(z - range, max) * max; tz <= z + range; tz += max) {
                collect(result, new HorizonTile(tx, tz, max), x, z, inner, range);
            }
        }
        result.sort(Comparator.comparingInt(tile -> tile.distance(x, z)));
        return result;
    }

    private static void collect(List<HorizonTile> result, HorizonTile tile, int x, int z, int clip, int range) {
        int distance = tile.distance(x, z);
        if (!tile.withinRange(x, z, range) || (tile.x() >= x - clip && tile.z() >= z - clip
                && tile.x() + tile.size() <= x + clip && tile.z() + tile.size() <= z + clip)) return;
        double detailRange = tile.size() <= 256 ? 1.28 : 1.0;
        if (tile.size() <= HorizonTile.MIN_SIZE || distance > tile.size() * detailRange) {
            result.add(tile);
            return;
        }
        int half = tile.size() / 2;
        for (int dz = 0; dz <= half; dz += half) {
            for (int dx = 0; dx <= half; dx += half) {
                collect(result, new HorizonTile(tile.x() + dx, tile.z() + dz, half), x, z, clip, range);
            }
        }
    }

    private static void receive(HorizonNetwork.Response response) {
        if (world != null && response.epoch() == epoch && response.dimension().equals(world.dimension().location())
                && PENDING.remove(response.tile()) != null) {
            RECEIVED.put(response.tile(), response.data());
        }
    }

    private static void changeRange(int distance, int x, int z) {
        if (distance < range) {
            MESHES.entrySet().removeIf(entry -> {
                if (distance > 0 && entry.getKey().withinRange(x, z, distance)) return false;
                entry.getValue().close();
                return true;
            });
        }
        range = distance;
        PENDING.clear();
        RECEIVED.clear();
        DIRTY_DETAILS.clear();
        epoch++;
    }

    private static void reset(ClientLevel level) {
        MESHES.values().forEach(HorizonMesh::close);
        MESHES.clear();
        PENDING.clear();
        RECEIVED.clear();
        DIRTY_DETAILS.clear();
        visibleTiles = List.of();
        layout = new HorizonCoverage.Layout(visibleTiles);
        coarseTiles = List.of();
        rootTiles = List.of();
        detailPriority = List.of();
        coveragePriority = List.of();
        rootPriority = List.of();
        renderTiles = List.of();
        near = 0;
        range = 0;
        coveredRange = drawRange = 0;
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
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || drawRange <= near || renderTiles.isEmpty()
                || !available(Minecraft.getInstance()) || !clearView(event.getCamera())) return;
        event.setNearPlaneDistance(Math.min(drawRange - 1, Math.max(near, event.getNearPlaneDistance())));
        event.setFarPlaneDistance(drawRange);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || range <= near
                || !available(Minecraft.getInstance()) || !clearView(event.getCamera())) return;
        advanceBake();
        if (drawRange <= near || renderTiles.isEmpty()) return;

        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack().last().pose();
        // The horizon is a depth-tested background pass. Increasing its near plane preserves depth precision
        // without competing with the nearby terrain's depth buffer.
        float far = drawRange + HorizonTile.MAX_SIZE;
        float clipNear = Math.max(2, near * 0.05f);
        var projection = PROJECTION.set(event.getProjectionMatrix());
        projection.m22(-(far + clipNear) / (far - clipNear));
        projection.m32(-2 * far * clipNear / (far - clipNear));
        FRUSTUM.set(projection.mul(pose, VIEW_PROJECTION), false);

        var program = shader;
        var tileUniform = program.getUniform("Tile");
        if (tileUniform == null) return;
        program.safeGetUniform("ModelViewMat").set(pose);
        program.safeGetUniform("ProjMat").set(projection);
        program.safeGetUniform("HorizonFog").set(RenderSystem.getShaderFogColor());
        setupMaterials(program, Minecraft.getInstance());
        float light = 1.0f - 0.74f * Math.min(1, world.getStarBrightness(event.getPartialTick()) * 2f);
        program.safeGetUniform("TerrainLight").set(world.dimension() == Level.OVERWORLD ? light : light * 0.82f);
        program.safeGetUniform("Clip").set((float) -camera.y, (float) near, (float) drawRange);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        boolean drawn = false;
        try {
            program.apply();
            for (var draw : renderTiles) {
                var mesh = draw.mesh();
                var bounds = mesh.bounds();
                if (!FRUSTUM.testAab((float) (bounds.minX - camera.x), (float) (bounds.minY - camera.y), (float) (bounds.minZ - camera.z),
                        (float) (bounds.maxX - camera.x), (float) (bounds.maxY - camera.y), (float) (bounds.maxZ - camera.z))) continue;
                var tile = draw.tile();
                tileUniform.set((float) (tile.x() - camera.x), (float) (tile.z() - camera.z),
                        (float) tile.size(), (float) draw.coverage());
                tileUniform.upload();
                mesh.buffer().bind();
                mesh.buffer().draw();
                drawn = true;
            }
        } finally {
            VertexBuffer.unbind();
            program.clear();
            RenderSystem.disableBlend();
            // The original chunk layers render after the sky and own the new depth buffer.
            if (drawn) RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
    }

    private static void setupMaterials(ShaderInstance program, Minecraft client) {
        if (materialOwner == program) return;
        program.setSampler("Blocks", client.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS));
        var atlas = client.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        setSprite(program, atlas, "StoneUV", "block/stone");
        setSprite(program, atlas, "SandUV", "block/sand");
        setSprite(program, atlas, "LeavesUV", "block/oak_leaves");
        setSprite(program, atlas, "GrassUV", "block/grass_block_top");
        setSprite(program, atlas, "WoodUV", "block/spruce_log");
        setSprite(program, atlas, "PlankUV", "block/oak_planks");
        setSprite(program, atlas, "SoilUV", "block/dirt");
        materialOwner = program;
    }

    private static void setSprite(ShaderInstance program,
                                  Function<ResourceLocation, TextureAtlasSprite> atlas,
                                  String uniform, String id) {
        var sprite = atlas.apply(new ResourceLocation(id));
        program.safeGetUniform(uniform).set(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
    }

    private static void updateCoverage() {
        var coverage = HorizonCoverage.plan(layout, MESHES::containsKey, centerX, centerZ, range);
        var display = new ArrayList<DrawTile>(coverage.masks().size());
        for (var entry : coverage.masks().entrySet()) {
            var mesh = MESHES.get(entry.getKey());
            if (mesh.buffer() != null) display.add(new DrawTile(entry.getKey(), mesh, entry.getValue()));
        }
        renderTiles = display;
        coveredRange = coverage.radius();
        drawRange = Math.min(drawRange, coveredRange);
    }

    private static void advanceBake() {
        finishBake();
        if (bake != null || RECEIVED.isEmpty()) return;
        var iterator = RECEIVED.entrySet().iterator();
        var entry = iterator.next();
        var tile = entry.getKey();
        var data = entry.getValue();
        iterator.remove();
        // Keep the worker's buffer owned by this job until upload or discard releases its rendered view.
        bake = new Bake(tile, epoch, CompletableFuture.supplyAsync(() -> {
            if (bakeBuffer == null) bakeBuffer = new BufferBuilder(16384);
            try {
                return HorizonMesh.bake(tile, data, bakeBuffer);
            } catch (RuntimeException error) {
                if (bakeBuffer.building()) {
                    var incomplete = bakeBuffer.endOrDiscardIfEmpty();
                    if (incomplete != null) incomplete.release();
                }
                throw error;
            }
        }, BAKER));
    }

    private static void finishBake() {
        if (bake == null || !bake.future().isDone()) return;
        var completed = bake;
        bake = null;
        HorizonMesh.Baked mesh;
        try {
            mesh = completed.future().join();
        } catch (CompletionException error) {
            LOGGER.warn("Could not build horizon mesh {}", completed.tile(), error.getCause());
            return;
        }
        if (completed.epoch() != epoch) {
            mesh.discard();
            return;
        }
        var previous = MESHES.put(completed.tile(), mesh.upload());
        if (previous != null) previous.close();
        while (MESHES.size() > MAX_MESHES) {
            var oldest = MESHES.entrySet().iterator();
            oldest.next().getValue().close();
            oldest.remove();
        }
        updateCoverage();
    }

    private AbyssHorizonRenderer() {}
}
