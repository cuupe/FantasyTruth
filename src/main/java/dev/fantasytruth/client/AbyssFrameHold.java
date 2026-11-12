package dev.fantasytruth.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.abyss.AbyssLayer;
import dev.fantasytruth.abyss.AbyssTransit;
import dev.fantasytruth.abyss.OrthIsland;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Keeps the previous view visible while the respawn packet swaps the client world. */
@Mod.EventBusSubscriber(modid = FantasyTruth.ID, value = Dist.CLIENT)
public final class AbyssFrameHold {
    private static final long TIMEOUT = 2_500_000_000L;
    private static TextureTarget previous;
    private static long started;

    public static void capture(ClientboundRespawnPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (AbyssShaderPacks.active() || mc.level == null || mc.player == null || Math.abs(mc.player.getY()) > 96
                || !OrthIsland.inShaft(mc.player.getX(), mc.player.getZ())) return;
        var source = mc.level.dimension();
        var sourceType = mc.level.dimensionTypeId().location();
        boolean descent = source.equals(Level.OVERWORLD) && sourceType.equals(AbyssTransit.EXPEDITION)
                && packet.getDimension().equals(AbyssLayer.EDGE.key)
                && packet.getDimensionType().location().equals(AbyssTransit.EDGE_TYPE);
        boolean ascent = source.equals(AbyssLayer.EDGE.key) && sourceType.equals(AbyssTransit.EDGE_TYPE)
                && packet.getDimension().equals(Level.OVERWORLD)
                && packet.getDimensionType().location().equals(AbyssTransit.EXPEDITION);
        if (!descent && !ascent) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (main.viewWidth < 1 || main.viewHeight < 1) return;
        release();
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            previous = new TextureTarget(main.viewWidth, main.viewHeight, false, Minecraft.ON_OSX);
            copy(main, previous);
            started = System.nanoTime();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
    }

    public static boolean draw() {
        if (previous == null || AbyssShaderPacks.active() || System.nanoTime() - started >= TIMEOUT) return false;
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            copy(previous, Minecraft.getInstance().getMainRenderTarget());
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
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
        if (event.phase == TickEvent.Phase.END && previous != null
                && (AbyssShaderPacks.active() || !(Minecraft.getInstance().screen instanceof ReceivingLevelScreen)
                || System.nanoTime() - started >= TIMEOUT)) release();
    }

    private static void release() {
        if (previous == null) return;
        previous.destroyBuffers();
        previous = null;
    }

    private AbyssFrameHold() {}
}
