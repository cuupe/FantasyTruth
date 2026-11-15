package dev.fantasytruth.mixin;

import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.abyss.AbyssTransit;
import dev.fantasytruth.client.AbyssFrameHold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReceivingLevelScreen.class)
abstract class AtlasReceivingLevelMixin {
    @Inject(method = {"m_88315_", "render"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void fantasytruth$drawPrevious(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (AbyssFrameHold.draw()) ci.cancel();
    }

    @Redirect(method = "m_86600_", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;m_202430_(Lnet/minecraft/core/BlockPos;)Z"), remap = false, require = 0)
    private boolean fantasytruth$readySrg(LevelRenderer renderer, BlockPos pos) {
        return fantasytruth$ready(renderer, pos);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;isChunkCompiled(Lnet/minecraft/core/BlockPos;)Z"), remap = false, require = 0)
    private boolean fantasytruth$readyOfficial(LevelRenderer renderer, BlockPos pos) {
        return fantasytruth$ready(renderer, pos);
    }

    @Redirect(method = {"m_86600_", "tick"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;m_5833_()Z"), remap = false, require = 0)
    private boolean fantasytruth$spectatorSrg(LocalPlayer player) {
        return player.isSpectator() && !AbyssFrameHold.waitingForTerrain();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"), remap = false, require = 0)
    private boolean fantasytruth$spectatorOfficial(LocalPlayer player) {
        return player.isSpectator() && !AbyssFrameHold.waitingForTerrain();
    }

    @Unique
    private static boolean fantasytruth$ready(LevelRenderer renderer, BlockPos pos) {
        boolean crossing = AbyssFrameHold.waitingForTerrain();
        if (!crossing && renderer.isChunkCompiled(pos)) return true;
        if (crossing) return AbyssFrameHold.terrainReady(pos);
        var level = Minecraft.getInstance().level;
        if (level == null || level.dimensionTypeRegistration().unwrapKey().isEmpty()) return false;
        ResourceLocation type = level.dimensionTypeRegistration().unwrapKey().orElseThrow().location();
        if (!type.equals(AbyssTransit.ABYSS) && !(type.getNamespace().equals(FantasyTruth.ID) && type.getPath().startsWith("abyss_forest")) && !type.equals(AbyssTransit.EDGE_TYPE) && !type.equals(AbyssTransit.EXPEDITION)
                && (!type.getNamespace().equals(FantasyTruth.ID) || !type.getPath().equals("high_relief"))) return false;
        var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        // A renderer can omit the mesh for a section containing only air. Still wait for real chunk data.
        return chunk != null && chunk.getSection(chunk.getSectionIndex(pos.getY())).hasOnlyAir();
    }
}
