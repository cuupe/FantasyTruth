package dev.fantasytruth.client;

import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/** DH does this work off-thread. Identify the level being built, not Minecraft.level. */
public final class AbyssDistantHorizons {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EDGE = "stratacubes:abyss/edge";
    private static final ThreadLocal<LevelMatch> LAST_LEVEL = new ThreadLocal<>();
    private static final AtomicBoolean REPORTED_COLUMNS = new AtomicBoolean();
    private static final AtomicBoolean REPORTED_FACES = new AtomicBoolean();
    private static final ClassValue<SectionAccess> SECTION_ACCESS = new ClassValue<>() {
        @Override protected SectionAccess computeValue(Class<?> source) {
            try {
                Class<?> positions = Class.forName("com.seibel.distanthorizons.core.pos.DhSectionPos", false,
                        source.getClassLoader());
                return new SectionAccess(source.getMethod("getPos"), source.getMethod("getDataDetailLevel"),
                        positions.getMethod("getMinCornerBlockX", long.class),
                        positions.getMethod("getMinCornerBlockZ", long.class));
            } catch (ReflectiveOperationException e) {
                return new SectionAccess(null, null, null, null);
            }
        }
    };

    private AbyssDistantHorizons() {}

    public static boolean isEdgeWrapper(Object levelWrapper) {
        return matches(levelWrapper, false);
    }

    public static boolean isEdgeClientLevel(Object clientLevel) {
        return matches(clientLevel, true);
    }

    public static int verticalBudget(int original, Object wrapper, Object source) {
        if (!isEdgeWrapper(wrapper)) return original;
        SectionAccess access = SECTION_ACCESS.get(source.getClass());
        if (access.pos() == null) return original;
        try {
            int detail = ((Number) access.detail().invoke(source)).intValue();
            if (detail < 0 || detail >= 6) return original;
            long pos = (long) access.pos().invoke(source);
            int x = (int) access.minX().invoke(null, pos);
            int z = (int) access.minZ().invoke(null, pos);
            int span = 64 << detail; // DH stores 64 columns on each side of a render section.
            if (x >= 528 || z >= 528 || (long) x + span <= -512 || (long) z + span <= -512) return original;
            int floor = detail <= 1 ? 16 : detail <= 3 ? 12 : 8;
            if (REPORTED_COLUMNS.compareAndSet(false, true)) {
                LOGGER.info("Distant Horizons: preserving first-layer shelves (vertical budget {} → {})",
                        original, Math.max(original, floor));
            }
            return Math.max(original, floor);
        } catch (ReflectiveOperationException e) {
            return original;
        }
    }

    public static int caveCullHeight(int height, Object level) {
        if (!isEdgeClientLevel(level)) return height;
        if (REPORTED_FACES.compareAndSet(false, true)) {
            LOGGER.info("Distant Horizons: keeping first-layer rock faces below the cave culling height");
        }
        return Integer.MIN_VALUE; // ColumnBox's sentinel for disabled cave culling.
    }

    private static boolean matches(Object level, boolean unwrap) {
        if (level == null) return false;
        LevelMatch cached = LAST_LEVEL.get();
        if (cached != null && cached.level().get() == level && cached.unwrap() == unwrap) return cached.edge();

        boolean edge = false;
        try {
            Object wrapper = unwrap ? level.getClass().getMethod("getLevelWrapper").invoke(level) : level;
            edge = wrapper != null && EDGE.equals(wrapper.getClass().getMethod("getDimensionName").invoke(wrapper));
        } catch (ReflectiveOperationException ignored) {
            // Unsupported DH version or wrapper: keep its defaults.
        }
        LAST_LEVEL.set(new LevelMatch(new WeakReference<>(level), unwrap, edge));
        return edge;
    }

    private record LevelMatch(WeakReference<Object> level, boolean unwrap, boolean edge) {}
    private record SectionAccess(Method pos, Method detail, Method minX, Method minZ) {}
}
