package dev.fantasytruth.atlas;

import dev.fantasytruth.terrain.AtlasField;
import java.util.SplittableRandom;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

final class AtlasLandmarks {
    private static final int REACH = 8;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState LADDER = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
    record Site(AtlasContent.Landmark rule, int x, long y, int z, long seed) {}

    static void place(AtlasContent content, AtlasField field, ChunkAccess chunk, long page) {
        long origin = page * Altitude.STRIDE;
        boolean natural = origin + chunk.getMaxBuildHeight() >= -100_040 && origin + chunk.getMinBuildHeight() <= 100_040;
        var writer = new AtlasDecorations.Writer(chunk, origin);
        for (var rule : content.landmarks()) {
            if (natural) {
                int spacing = spacing(field, rule);
                for (int gz = Math.floorDiv(writer.z0 - REACH, spacing); gz <= Math.floorDiv(writer.z0 + 15 + REACH, spacing); gz++) {
                    for (int gx = Math.floorDiv(writer.x0 - REACH, spacing); gx <= Math.floorDiv(writer.x0 + 15 + REACH, spacing); gx++) {
                        write(field, writer, page, candidate(field, rule, gx, gz, spacing));
                    }
                }
            }
            for (var site : content.fixedSites()) {
                if (site.landmark().equals(rule.id()) && intersects(writer, site.x(), site.z())) {
                    write(field, writer, page, fixed(field, rule, site));
                }
            }
        }
    }

    static Site nearest(AtlasContent content, AtlasField field, String id, int x, int z) {
        Site best = null;
        for (var rule : content.landmarks()) {
            if (!rule.id().equals(id) && !id.equals("any")) continue;
            int spacing = spacing(field, rule);
            int cx = Math.floorDiv(x, spacing);
            int cz = Math.floorDiv(z, spacing);
            for (int gz = cz - 10; gz <= cz + 10; gz++) {
                for (int gx = cx - 10; gx <= cx + 10; gx++) {
                    best = closer(best, candidate(field, rule, gx, gz, spacing), x, z);
                }
            }
            for (var site : content.fixedSites()) {
                if (site.landmark().equals(rule.id())) best = closer(best, fixed(field, rule, site), x, z);
            }
        }
        return best;
    }

    private static Site closer(Site current, Site next, int x, int z) {
        return next != null && (current == null || distance(next, x, z) < distance(current, x, z)) ? next : current;
    }

    private static long distance(Site site, int x, int z) {
        long dx = site.x - (long) x;
        long dz = site.z - (long) z;
        return dx * dx + dz * dz;
    }

    private static int spacing(AtlasField field, AtlasContent.Landmark rule) {
        if (rule.spacing() != 0) return rule.spacing();
        return rule.kind() == AtlasContent.Kind.HOMESTEAD ? field.profile().settlementSpacing() : 96;
    }

    private static Site candidate(AtlasField field, AtlasContent.Landmark rule, int gx, int gz, int spacing) {
        long seed = AtlasField.hash(field.seed() ^ rule.id().hashCode(), gx, 0, gz);
        var random = new SplittableRandom(seed);
        int x = gx * spacing + 10 + random.nextInt(spacing - 20);
        int z = gz * spacing + 10 + random.nextInt(spacing - 20);
        double chance = rule.chance();
        if (rule.kind() == AtlasContent.Kind.HOMESTEAD) chance *= field.profile().settlementDensity();
        if (random.nextDouble() >= chance || Math.abs(x) > 29_999_950 || Math.abs(z) > 29_999_950) return null;
        var column = field.column(x, z);
        if (column.height() < field.profile().seaLevel() + 3 || column.slope() > rule.maxSlope()) return null;
        int ground = column.height();
        for (int dx : new int[]{-4, 4}) {
            for (int dz : new int[]{-4, 4}) {
                ground = Math.max(ground, field.column(x + dx, z + dz).height());
            }
        }
        return new Site(rule, x, ground + 1, z, seed);
    }

    private static Site fixed(AtlasField field, AtlasContent.Landmark rule, AtlasContent.FixedSite site) {
        long y = site.y().orElseGet(() -> field.column(site.x(), site.z()).height() + 1L);
        return new Site(rule, site.x(), y, site.z(), AtlasField.hash(field.seed(), site.x(), y, site.z()));
    }

    private static boolean intersects(AtlasDecorations.Writer writer, int x, int z) {
        return x >= writer.x0 - REACH && x <= writer.x0 + 15 + REACH
                && z >= writer.z0 - REACH && z <= writer.z0 + 15 + REACH;
    }

    private static void write(AtlasField field, AtlasDecorations.Writer writer, long page, Site site) {
        if (site == null || !intersects(writer, site.x, site.z)
                || site.y + 21L < writer.originY + writer.chunk.getMinBuildHeight()
                || site.y - 32L >= writer.originY + writer.chunk.getMaxBuildHeight()) return;
        var rule = site.rule;
        if (rule.kind() == AtlasContent.Kind.HOMESTEAD) {
            AtlasDecorations.house(field, writer, site.x, site.z, site.y, 4, 4, (site.seed & 1) == 0, rule);
            chest(writer, page, site, 2, 1, 2);
            return;
        }
        BlockState wall = rule.wall().defaultBlockState();
        BlockState floor = rule.floor().defaultBlockState();
        int width = rule.kind() == AtlasContent.Kind.RUIN ? 5 : 3;
        int height = rule.kind() == AtlasContent.Kind.WATCHTOWER ? 15 : 5;
        for (int dx = -width; dx <= width; dx++) {
            for (int dz = -width; dz <= width; dz++) {
                int x = site.x + dx;
                int z = site.z + dz;
                int ground = field.column(x, z).height();
                for (long y = Math.max(site.y - 15, ground + 1L); y < site.y; y++) writer.put(x, y, z, wall, false);
                writer.put(x, site.y, z, floor, false);
                boolean edge = Math.abs(dx) == width || Math.abs(dz) == width;
                long hash = AtlasField.hash(site.seed, dx, 0, dz);
                for (int i = 1; i <= height; i++) {
                    boolean entrance = dz == -width && dx == 0 && i <= 3;
                    boolean slit = (dx == 0 || dz == 0) && i % 4 == 3;
                    boolean battlement = i == height && (dx + dz) % 2 == 0;
                    boolean wallHere = switch (rule.kind()) {
                        case WATCHTOWER -> edge && !entrance && !slit && (i < height || battlement);
                        case RUIN -> edge && !entrance && i <= 1 + Math.floorMod(hash, 5);
                        default -> edge && !entrance && i < height;
                    };
                    BlockState state = wallHere ? wall : AIR;
                    if (rule.kind() == AtlasContent.Kind.MINE && i == height) state = floor;
                    if (rule.kind() == AtlasContent.Kind.WATCHTOWER && i == height - 1 && (dx != -width + 1 || dz != 0)) state = floor;
                    if (rule.kind() == AtlasContent.Kind.WATCHTOWER && dx == -width + 1 && dz == 0 && i < height) state = LADDER;
                    writer.put(x, site.y + i, z, state, false);
                }
            }
        }
        if (rule.kind() == AtlasContent.Kind.MINE) {
            for (long y = site.y; y >= site.y - 18; y--) {
                writer.put(site.x - 2, y, site.z, wall, false);
                writer.put(site.x - 1, y, site.z, LADDER, false);
                writer.put(site.x, y, site.z, AIR, false);
                writer.put(site.x, y, site.z + 1, AIR, false);
            }
            writer.put(site.x, site.y - 19, site.z, floor, false);
        }
        int lootY = rule.kind() == AtlasContent.Kind.WATCHTOWER ? height : 1;
        chest(writer, page, site, 1, lootY, 1);
        writer.put(site.x + 2, site.y + lootY, site.z + 1, Blocks.LANTERN.defaultBlockState(), false);
    }

    private static void chest(AtlasDecorations.Writer writer, long page, Site site, int dx, int dy, int dz) {
        if (site.rule.lootTable().isEmpty()) return;
        int x = site.x + dx;
        long y = site.y + dy;
        int z = site.z + dz;
        long local = y - writer.originY;
        if (x < writer.x0 || x > writer.x0 + 15 || z < writer.z0 || z > writer.z0 + 15
                || local < writer.chunk.getMinBuildHeight() || local >= writer.chunk.getMaxBuildHeight()) return;
        writer.put(x, y, z, Blocks.CHEST.defaultBlockState(), false);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:chest");
        tag.putInt("x", x);
        tag.putInt("y", (int) local);
        tag.putInt("z", z);
        if (y >= -Altitude.LIMIT && y <= Altitude.LIMIT && Altitude.at(y).page() == page) {
            tag.putString("LootTable", site.rule.lootTable().orElseThrow().toString());
            tag.putLong("LootTableSeed", AtlasField.hash(site.seed, x, y, z));
        }
        writer.chunk.setBlockEntityNbt(tag);
    }

    private AtlasLandmarks() {}
}
