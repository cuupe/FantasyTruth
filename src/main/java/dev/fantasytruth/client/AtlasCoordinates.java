package dev.fantasytruth.client;

import dev.fantasytruth.FantasyTruth;
import dev.fantasytruth.atlas.Altitude;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FantasyTruth.ID, value = Dist.CLIENT)
public final class AtlasCoordinates {
    @SubscribeEvent
    public static void debugText(CustomizeGuiOverlayEvent.DebugText event) {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.getCameraEntity();
        if (minecraft.level == null || camera == null) return;
        long origin = origin(minecraft.level.dimension().location());
        if (origin == 0) return;
        for (int i = 0; i < event.getLeft().size(); i++) {
            String line = event.getLeft().get(i);
            if (line == null) continue;
            if (line.startsWith("XYZ: ")) {
                String y = BigDecimal.valueOf(origin).add(BigDecimal.valueOf(camera.getY()))
                        .setScale(5, RoundingMode.HALF_UP).toPlainString();
                event.getLeft().set(i, replaceY(line, 5, " / ", y));
            } else if (line.startsWith("Block: ")) {
                event.getLeft().set(i, replaceY(line, 7, " ", Long.toString(origin + camera.getBlockY())));
            } else if (line.startsWith("Chunk: ")) {
                long section = Math.floorDiv(origin + camera.getBlockY(), 16);
                event.getLeft().set(i, replaceY(line, 7, " ", Long.toString(section)));
            }
        }
    }

    private static long origin(ResourceLocation dimension) {
        String path = dimension.getPath();
        String prefix = "atlas_page/";
        if (!dimension.getNamespace().equals(FantasyTruth.ID) || !path.startsWith(prefix)) return 0;
        String index = path.substring(prefix.length());
        if (!index.startsWith("m_") && !index.startsWith("p_")) return 0;
        try {
            long page = Long.parseLong(index.substring(2));
            if (page <= 0 || page > (Altitude.LIMIT + Altitude.HALF) / Altitude.STRIDE) return 0;
            return (index.charAt(0) == 'm' ? -page : page) * Altitude.STRIDE;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String replaceY(String line, int start, String delimiter, String y) {
        int xEnd = line.indexOf(delimiter, start);
        if (xEnd < 0) return line;
        int yStart = xEnd + delimiter.length();
        int yEnd = line.indexOf(delimiter, yStart);
        return yEnd < 0 ? line : line.substring(0, yStart) + y + line.substring(yEnd);
    }

    private AtlasCoordinates() {}
}
