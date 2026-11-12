package dev.fantasytruth.client;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import net.minecraftforge.fml.ModList;

/** The optional Iris API is provided by Oculus on Forge. */
final class AbyssShaderPacks {
    static boolean active() { return Hook.IN_USE.getAsBoolean(); }

    private static final class Hook {
        private static final BooleanSupplier IN_USE = create();

        private static BooleanSupplier create() {
            if (!ModList.get().isLoaded("oculus") && !ModList.get().isLoaded("iris")) return () -> false;
            for (String name : new String[]{"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.api.v0.IrisApi"}) {
                try {
                    Class<?> api = Class.forName(name);
                    Object instance = api.getMethod("getInstance").invoke(null);
                    Method active = api.getMethod("isShaderPackInUse");
                    return () -> {
                        try {
                            return (boolean) active.invoke(instance);
                        } catch (ReflectiveOperationException | LinkageError unavailable) {
                            return true;
                        }
                    };
                } catch (ReflectiveOperationException | LinkageError unavailable) {
                    // Try the other public Iris API package.
                }
            }
            return () -> true;
        }
    }

    private AbyssShaderPacks() {}
}
