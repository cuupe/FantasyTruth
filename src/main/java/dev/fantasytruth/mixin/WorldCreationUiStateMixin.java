package dev.fantasytruth.mixin;

import dev.fantasytruth.client.AtlasStudioHooks;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldCreationUiState.class)
abstract class WorldCreationUiStateMixin {
    @Inject(method = {"m_267744_", "getPresetEditor"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void fantasytruth$editor(CallbackInfoReturnable<PresetEditor> result) {
        if (AtlasStudioHooks.isAtlas((WorldCreationUiState) (Object) this)) result.setReturnValue(AtlasStudioHooks.EDITOR);
    }

    @Inject(method = {"m_267692_", "setSettings"}, at = @At("RETURN"), remap = false)
    private void fantasytruth$settings(WorldCreationContext context, CallbackInfo callback) {
        AtlasStudioHooks.onSettings((WorldCreationUiState) (Object) this, context);
    }
}
