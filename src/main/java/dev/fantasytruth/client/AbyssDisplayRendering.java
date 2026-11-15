package dev.fantasytruth.client;

import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.abyss.AbyssStores;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FantasyTruth.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AbyssDisplayRendering {
    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AbyssStores.TYPE.get(), context -> {
            var renderer = context.getItemRenderer();
            return (stand, partialTick, pose, buffers, light, overlay) -> {
                if (!BuiltInRegistries.BLOCK.getKey(stand.getBlockState().getBlock()).getPath().equals("whistle_stand")) return;
                var stack = stand.getItem(0);
                if (stack.isEmpty()) return;
                pose.pushPose();
                pose.translate(0.5, 0.61, 0.5);
                pose.mulPose(Axis.XP.rotationDegrees(78));
                pose.scale(0.8F, 0.8F, 0.8F);
                renderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, stand.getLevel(), 0);
                pose.popPose();
            };
        });
    }

    private AbyssDisplayRendering() {}
}
