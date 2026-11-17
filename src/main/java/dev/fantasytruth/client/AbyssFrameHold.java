package dev.fantasytruth.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.abyss.AbyssEdgeGenerator;
import dev.fantasytruth.abyss.AbyssLayer;
import dev.fantasytruth.abyss.AbyssTransit;
import dev.fantasytruth.abyss.ForestCoordinates;
import dev.fantasytruth.abyss.OrthIsland;
import dev.fantasytruth.abyss.NaturalAbyssSite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Keeps the last composite while nearby chunks arrive, then blends into the live world. */
@Mod.EventBusSubscriber(modid = FantasyTruth.ID, value = Dist.CLIENT)
public final class AbyssFrameHold {
    private static final long WAIT_LIMIT = 3_000_000_000L;
    private static final long FADE_TIME = 180_000_000L;
    private static final long MESH_GRACE = 850_000_000L;
    private static TextureTarget previous;
    private static long started, liveStarted, fadeStarted;
    private static int readyTicks;

    public static void capture(ClientboundRespawnPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        release();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || !isPassage(mc, packet)) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (main.viewWidth < 1 || main.viewHeight < 1) return;
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            previous = new TextureTarget(main.viewWidth, main.viewHeight, false, Minecraft.ON_OSX);
            copy(main, previous);
            started = System.nanoTime();
            liveStarted = fadeStarted = 0;
            readyTicks = 0;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
    }

    private static boolean isPassage(Minecraft mc, ClientboundRespawnPacket packet) {
        var source = mc.level.dimension();
        var target = packet.getDimension();
        if (source.equals(target)) return false;
        var from = AbyssLayer.of(source);
        var to = AbyssLayer.of(target);
        double y = mc.player.getY();
        double x = mc.player.getX(), z = mc.player.getZ();
        if (source.equals(Level.OVERWORLD) && to == AbyssLayer.EDGE) {
            if (mc.level.dimensionTypeId().location().equals(NaturalAbyssSite.OVERWORLD_TYPE)) return near(y, 0);
            return mc.level.dimensionTypeId().location().equals(AbyssTransit.EXPEDITION)
                    && (near(y, 0) && OrthIsland.inShaft(x, z) || near(y, AbyssLayer.MOUTH_GATE) && AbyssLayer.passage(x, z, 42));
        }
        if (from == AbyssLayer.EDGE && target.equals(Level.OVERWORLD)) {
            if (packet.getDimensionType().location().equals(NaturalAbyssSite.OVERWORLD_TYPE)) return near(y, 0) && OrthIsland.inShaft(x, z);
            return packet.getDimensionType().location().equals(AbyssTransit.EXPEDITION)
                    && (near(y, 0) && OrthIsland.inShaft(x, z) || near(y, from.entrance) && AbyssLayer.passage(x, z, 42));
        }
        if (from == null || to == null || Math.abs(from.ordinal() - to.ordinal()) != 1) return false;
        if (from == AbyssLayer.EDGE) {
            return AbyssLayer.passage(x, z, 145) && (near(y, AbyssEdgeGenerator.EXIT) || near(y, from.exit));
        }
        if (to == from.previous()) {
            int radius = from == AbyssLayer.FOREST ? 145 : 56;
            return AbyssLayer.passage(x, z, radius) && near(y, from.entrance);
        }
        if (!AbyssLayer.passage(x, z, 56)) return false;
        return near(y, from.exit) || from == AbyssLayer.FOREST
                && (near(y, ForestCoordinates.DEEP_EXIT) || near(y, from.entrance - 1250));
    }

    private static boolean near(double y, int plane) { return Math.abs(y - plane) <= 86; }

    public static boolean waitingForTerrain() {
        return previous != null && fadeStarted == 0 && System.nanoTime() - started < WAIT_LIMIT;
    }

    public static boolean terrainReady(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level == null || level.isOutsideBuildHeight(pos)) {
            readyTicks = 0;
            return false;
        }
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (level.getChunkSource().getChunk((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz,
                    ChunkStatus.FULL, false) == null) {
                readyTicks = 0;
                return false;
            }
        }
        return ++readyTicks >= 2;
    }

    public static boolean draw() {
        if (!waitingForTerrain()) return false;
        drawPrevious();
        return true;
    }

    private static void drawPrevious() {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            copy(previous, Minecraft.getInstance().getMainRenderTarget());
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
    }

    public static void fade() {
        Minecraft mc = Minecraft.getInstance();
        if (previous == null || mc.level == null || mc.screen != null) return;
        long now = System.nanoTime();
        if (liveStarted == 0) liveStarted = now;
        if (fadeStarted == 0) {
            // Let the first live frames assemble meshes while the previous picture is still visible.
            if (now - liveStarted < MESH_GRACE && !visibleTerrainReady(mc)) {
                drawPrevious();
                return;
            }
            fadeStarted = now;
        }
        float t = (float) (now - fadeStarted) / FADE_TIME;
        if (t >= 1) {
            release();
            return;
        }
        int alpha = Math.round(255 * (1 - t * t * (3 - 2 * t)));
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blending = GL11.glIsEnabled(GL11.GL_BLEND);
        var projection = RenderSystem.getProjectionMatrix();
        var sorting = RenderSystem.getVertexSorting();
        var shader = RenderSystem.getShader();
        var color = RenderSystem.getShaderColor().clone();
        int texture = RenderSystem.getShaderTexture(0);
        var model = RenderSystem.getModelViewStack();
        model.pushPose();
        try {
            mc.getMainRenderTarget().bindWrite(false);
            model.setIdentity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f(), sorting);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, previous.getColorTextureId());
            float u = (float) previous.viewWidth / previous.width;
            float v = (float) previous.viewHeight / previous.height;
            var buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(-1, -1, 0).uv(0, 0).color(255, 255, 255, alpha).endVertex();
            buffer.vertex(1, -1, 0).uv(u, 0).color(255, 255, 255, alpha).endVertex();
            buffer.vertex(1, 1, 0).uv(u, v).color(255, 255, 255, alpha).endVertex();
            buffer.vertex(-1, 1, 0).uv(0, v).color(255, 255, 255, alpha).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setProjectionMatrix(projection, sorting);
            model.popPose();
            RenderSystem.applyModelViewMatrix();
            if (depth) RenderSystem.enableDepthTest();
            if (!blending) RenderSystem.disableBlend();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
    }

    private static boolean visibleTerrainReady(Minecraft mc) {
        if (mc.levelRenderer.countRenderedChunks() == 0) return false;
        var camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return false;
        Vec3 eye = camera.getPosition();
        var look = camera.getLookVector();
        var left = camera.getLeftVector();
        var up = camera.getUpVector();
        var forward = new Vec3(look.x(), look.y(), look.z());
        var side = new Vec3(left.x(), left.y(), left.z());
        var vertical = new Vec3(up.x(), up.y(), up.z());
        // Wait for what is actually in front of the camera, rather than every section in the tall dimension.
        int missing = 0;
        for (int row = -1; row <= 1; row++) for (int col = -1; col <= 1; col++) {
            var ray = forward.add(side.scale(col * 0.54)).add(vertical.scale(row * 0.32)).normalize();
            var hit = mc.level.clip(new ClipContext(eye, eye.add(ray.scale(144)), ClipContext.Block.VISUAL,
                    ClipContext.Fluid.ANY, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK && !mc.levelRenderer.isChunkCompiled(hit.getBlockPos())
                    && ++missing >= 2) return false;
        }
        return true;
    }

    private static void copy(RenderTarget from, RenderTarget to) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, from.viewWidth, from.viewHeight, 0, 0, to.viewWidth, to.viewHeight,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || previous == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null
                && (!(mc.screen instanceof ReceivingLevelScreen) || System.nanoTime() - started >= WAIT_LIMIT)) release();
    }

    private static void release() {
        if (previous == null) return;
        previous.destroyBuffers();
        previous = null;
    }

    private AbyssFrameHold() {}
}
