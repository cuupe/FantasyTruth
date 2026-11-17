package dev.fantasytruth.client;

import com.mojang.logging.LogUtils;
import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.horizon.HorizonClientConfig;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/** Binds Embeddium's optional menu API without adding it to the mod or build classpath. */
final class EmbeddiumHorizonOptions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OPTIONS = "me.jellysquid.mods.sodium.client.gui.options.";
    private static final ResourceLocation HORIZON = new ResourceLocation(FantasyTruth.ID, "horizon_distance");

    private final Method getOptions;
    private final Method getId;
    private final Method matches;
    private final Method getStorage;
    private final Method createBuilder;
    private final Constructor<?> slider;
    private final Object formatter;
    private final ResourceLocation renderDistance;

    static void register() {
        if (!ModList.get().isLoaded("embeddium")) return;
        try {
            var event = Class.forName("org.embeddedt.embeddium.api.OptionGroupConstructionEvent").asSubclass(Event.class);
            var integration = new EmbeddiumHorizonOptions(event);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, event, integration::addDistance);
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("Unable to install the Embeddium horizon slider", exception);
        }
    }

    private EmbeddiumHorizonOptions(Class<? extends Event> event) throws ReflectiveOperationException {
        var option = Class.forName(OPTIONS + "Option");
        var implementation = Class.forName(OPTIONS + "OptionImpl");
        var storage = Class.forName(OPTIONS + "storage.OptionStorage");
        var valueFormatter = Class.forName(OPTIONS + "control.ControlValueFormatter");
        getOptions = event.getMethod("getOptions");
        getId = option.getMethod("getId");
        matches = getId.getReturnType().getMethod("matches", ResourceLocation.class);
        getStorage = option.getMethod("getStorage");
        createBuilder = implementation.getMethod("createBuilder", Class.class, storage);
        slider = Class.forName(OPTIONS + "control.SliderControl")
                .getConstructor(option, int.class, int.class, int.class, valueFormatter);
        var label = MethodHandles.lookup().findStatic(HorizonVideoSettings.class, "distanceLabel",
                MethodType.methodType(Component.class, int.class));
        formatter = MethodHandleProxies.asInterfaceInstance(valueFormatter, label);
        renderDistance = (ResourceLocation) Class.forName("org.embeddedt.embeddium.client.gui.options.StandardOptions$Option")
                .getField("RENDER_DISTANCE").get(null);
    }

    @SuppressWarnings("unchecked")
    private void addDistance(Event event) {
        try {
            var options = (List<Object>) getOptions.invoke(event);
            for (int index = 0; index < options.size(); index++) {
                var option = options.get(index);
                if (Boolean.TRUE.equals(matches.invoke(getId.invoke(option), renderDistance))) {
                    options.add(index + 1, buildOption(getStorage.invoke(option)));
                    return;
                }
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("Unable to add the horizon slider to Embeddium's rendering options", exception);
        }
    }

    private Object buildOption(Object storage) throws ReflectiveOperationException {
        Object builder = createBuilder.invoke(null, int.class, storage);
        Class<?> type = builder.getClass();
        type.getMethod("setId", ResourceLocation.class).invoke(builder, HORIZON);
        type.getMethod("setName", Component.class).invoke(builder, Component.translatable(HorizonVideoSettings.LABEL));
        type.getMethod("setTooltip", Component.class).invoke(builder, HorizonVideoSettings.tooltip());
        BiConsumer<Object, Integer> setter = (unused, value) -> HorizonClientConfig.setDistanceChunks(value);
        Function<Object, Integer> getter = unused -> HorizonClientConfig.distanceChunks();
        type.getMethod("setBinding", BiConsumer.class, Function.class).invoke(builder, setter, getter);
        Function<Object, Object> control = this::createSlider;
        type.getMethod("setControl", Function.class).invoke(builder, control);
        return type.getMethod("build").invoke(builder);
    }

    private Object createSlider(Object option) {
        try {
            return slider.newInstance(option, 0, HorizonClientConfig.MAX_CHUNKS, 1, formatter);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to construct the Embeddium horizon slider", exception);
        }
    }
}
