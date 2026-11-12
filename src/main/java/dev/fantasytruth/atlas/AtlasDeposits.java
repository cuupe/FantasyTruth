package dev.fantasytruth.atlas;

import dev.fantasytruth.terrain.AtlasField;
import java.util.SplittableRandom;
import net.minecraft.world.level.chunk.LevelChunkSection;

final class AtlasDeposits {
    static void place(AtlasContent content, AtlasField terrain, AtlasField.Column[] columns, LevelChunkSection section,
                      int originX, long originY, int originZ) {
        for (var rule : content.deposits()) {
            if (originY + 15 < rule.minY() || originY > rule.maxY() || rule.attempts() == 0 || rule.chance() == 0) continue;
            int radius = rule.radius();
            int verticalReach = (int) Math.ceil(Math.max(1.1, radius * 0.43)) - 1;
            long seed = AtlasField.hash(terrain.seed(), rule.id().hashCode(), 0, 137);
            for (long gy = Math.floorDiv(originY - verticalReach, 16); gy <= Math.floorDiv(originY + 15 + verticalReach, 16); gy++) {
                for (int gz = (originZ - radius) >> 4; gz <= (originZ + 15 + radius) >> 4; gz++) {
                    for (int gx = (originX - radius) >> 4; gx <= (originX + 15 + radius) >> 4; gx++) {
                        var random = new SplittableRandom(AtlasField.hash(seed, gx, gy, gz));
                        for (int i = 0; i < rule.attempts(); i++) {
                            int centerX = gx * 16 + random.nextInt(16);
                            long centerY = gy * 16 + random.nextInt(16);
                            int centerZ = gz * 16 + random.nextInt(16);
                            double angle = random.nextDouble(Math.PI);
                            double roll = random.nextDouble();
                            if (roll >= rule.chance() || centerY < rule.minY() || centerY > rule.maxY()
                                    || centerY < originY - verticalReach || centerY > originY + 15 + verticalReach
                                    || centerX <= originX - radius || centerX >= originX + 15 + radius
                                    || centerZ <= originZ - radius || centerZ >= originZ + 15 + radius) continue;
                            vein(rule, columns, section, originX, originY, originZ, centerX, centerY, centerZ, angle);
                        }
                    }
                }
            }
        }
    }

    private static void vein(AtlasContent.Deposit rule, AtlasField.Column[] columns, LevelChunkSection section,
                             int originX, long originY, int originZ, int centerX, long centerY, int centerZ, double angle) {
        int radius = rule.radius();
        int x0 = centerX - originX;
        int y0 = (int) (centerY - originY);
        int z0 = centerZ - originZ;
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double width = Math.max(1.25, radius * 0.54);
        double height = Math.max(1.1, radius * 0.43);
        for (int z = Math.max(0, z0 - radius); z <= Math.min(15, z0 + radius); z++) {
            for (int x = Math.max(0, x0 - radius); x <= Math.min(15, x0 + radius); x++) {
                double major = ((x - x0) * cos + (z - z0) * sin) / radius;
                double minor = (-(x - x0) * sin + (z - z0) * cos) / width;
                double horizontal = major * major + minor * minor;
                if (horizontal >= 1) continue;
                long highest = (long) columns[x + 16 * z].height() - rule.minimumCover();
                for (int y = Math.max(0, y0 - radius); y <= Math.min(15, y0 + radius); y++) {
                    long absoluteY = originY + y;
                    if (absoluteY < rule.minY() || absoluteY > rule.maxY() || absoluteY > highest) continue;
                    double vertical = (y - y0) / height;
                    if (horizontal + vertical * vertical >= 1 || !rule.replace().contains(section.getBlockState(x, y, z).getBlock())) continue;
                    var block = absoluteY < 0 ? rule.belowZeroBlock().orElse(rule.block()) : rule.block();
                    section.getStates().getAndSetUnchecked(x, y, z, block.defaultBlockState());
                }
            }
        }
    }

    private AtlasDeposits() {}
}
