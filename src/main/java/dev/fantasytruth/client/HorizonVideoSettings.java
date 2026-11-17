package dev.fantasytruth.client;

import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.horizon.HorizonClientConfig;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = FantasyTruth.ID, value = Dist.CLIENT)
public final class HorizonVideoSettings {
    static final String LABEL = "options.stratacubes.horizon_distance";
    private static final String TOOLTIP = LABEL + ".tooltip";

    @Mod.EventBusSubscriber(modid = FantasyTruth.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(EmbeddiumHorizonOptions::register);
        }

        private Setup() {}
    }

    @SubscribeEvent
    public static void addDistance(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof VideoSettingsScreen)) return;
        for (var listener : event.getListenersList()) {
            if (listener instanceof OptionsList list) {
                addSlider(list);
                return;
            }
        }
    }

    private static void addSlider(OptionsList list) {
        var vanillaDistance = list.findOption(Minecraft.getInstance().options.renderDistance());
        list.addBig(distanceOption());
        if (vanillaDistance == null) return;
        List<?> rows = list.children();
        int last = rows.size() - 1;
        for (int row = 0; row < last - 1; row++) {
            if (((ContainerEventHandler) rows.get(row)).children().contains(vanillaDistance)) {
                Collections.rotate(rows.subList(row + 1, last + 1), 1);
                return;
            }
        }
    }

    private static OptionInstance<Integer> distanceOption() {
        return new OptionInstance<>(LABEL, OptionInstance.cachedConstantTooltip(tooltip()),
                (caption, distance) -> Options.genericValueLabel(caption, distanceLabel(distance)),
                new OptionInstance.IntRange(0, HorizonClientConfig.MAX_CHUNKS),
                HorizonClientConfig.distanceChunks(), HorizonClientConfig::setDistanceChunks);
    }

    static Component tooltip() {
        var text = Component.translatable(TOOLTIP);
        if (AbyssShaderPacks.active()) text.append(" ").append(Component.translatable(TOOLTIP + ".shader_pack"));
        return text;
    }

    static Component distanceLabel(int chunks) {
        return chunks == 0 ? CommonComponents.OPTION_OFF : Component.translatable("options.chunks", chunks);
    }

    static Screen open(Screen parent) {
        return new SettingsScreen(parent);
    }

    private static final class SettingsScreen extends Screen {
        private final Screen parent;

        private SettingsScreen(Screen parent) {
            super(Component.translatable(LABEL));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int center = width / 2;
            addRenderableWidget(distanceOption().createButton(minecraft.options, center - 100, height / 2 - 16, 200));
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, unused -> onClose())
                    .bounds(center - 75, height / 2 + 20, 150, 20).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            graphics.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xffffff);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }

    private HorizonVideoSettings() {}
}
