package dev.fantasytruth.abyss;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Seeds households when their streets first become active, then leaves them to vanilla village AI. */
public final class OrthResidents {
    private static final String DATA = "stratacubes_orth_residents";
    private static final String RESIDENT = "stratacubes:orth_resident";
    private static final int DISTANCE = 2;

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD || level.getGameTime() % 20 != 0 || level.players().isEmpty()) return;
        var chunks = level.getChunkSource();
        var generator = chunks.getGenerator();
        if (!OrthIsland.enabled(generator)) return;
        boolean terraced = OrthIsland.terraced(generator), vertical = OrthIsland.vertical(generator);
        var data = level.getDataStorage().computeIfAbsent(Population::load, Population::new, DATA);
        int remaining = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) ? 4 : 0;
        for (var player : level.players()) {
            if (!player.isAlive() || Math.abs(OrthIsland.localX(player.getBlockX())) > 1220
                    || Math.abs(OrthIsland.localZ(player.getBlockZ())) > 1220 || player.getBlockY() < 32) continue;
            var center = player.chunkPosition();
            for (int z = center.z - DISTANCE; z <= center.z + DISTANCE; z++) {
                for (int x = center.x - DISTANCE; x <= center.x + DISTANCE; x++) {
                    var loaded = chunks.getChunkNow(x, z);
                    if (loaded == null || !OrthCity.developed(loaded.getPos(), terraced, vertical)) continue;
                    if (data.indexed.add(loaded.getPos().toLong())) indexVillageBlocks(level, loaded);
                    if (remaining == 0) continue;
                    for (var house : OrthCity.residences(loaded.getPos(), terraced, vertical)) {
                        if (Math.abs(house.y() - player.getBlockY()) > 72) continue;
                        BlockPos origin = house.at(0, 0, 0);
                        long id = origin.asLong();
                        int spacing = house.floors() == 1 ? 2 : 3;
                        long roll = Mth.getSeed(origin.getX(), origin.getY(), origin.getZ()) ^ level.getSeed();
                        if (Math.floorMod(roll, spacing) != 0 || data.seeded.contains(id)) continue;
                        if (settle(level, house, id)) {
                            data.seeded.add(id);
                            data.setDirty();
                            if (--remaining == 0) break;
                        }
                    }
                }
            }
        }
    }

    private static void indexVillageBlocks(ServerLevel level, LevelChunk chunk) {
        // Orth writes blocks to ChunkAccess directly, so the block-change POI hook does not run.
        var manager = level.getPoiManager();
        var sections = chunk.getSections();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            var section = sections[sectionIndex];
            if (section.hasOnlyAir() || !section.maybeHas(state -> PoiTypes.forState(state).isPresent())) continue;
            int y0 = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                var expected = PoiTypes.forState(section.getBlockState(x, y, z));
                if (expected.isEmpty()) continue;
                var pos = new BlockPos(x0 + x, y0 + y, z0 + z);
                var existing = manager.getType(pos);
                if (existing.equals(expected)) continue;
                if (existing.isPresent()) manager.remove(pos);
                manager.add(pos, expected.orElseThrow());
            }
        }
    }

    private static boolean settle(ServerLevel level, OrthCity.Residence house, long id) {
        int bedroom = (house.floors() - 1) * 5;
        BlockPos feet = house.at(0, bedroom + 1, 3);
        if (!level.isPositionEntityTicking(feet) || !level.getBlockState(feet.below()).is(OrthBlocks.PLANK.get())
                || !level.getFluidState(feet).isEmpty()) return false;

        int bedZ = house.floors() == 1 ? 1 - house.depth() : house.depth() - 1;
        BlockPos head = house.at(house.width() - 1, bedroom + 1, bedZ);
        if (!level.hasChunkAt(head)) return false;
        var bed = level.getBlockState(head);
        if (!(bed.getBlock() instanceof BedBlock) || bed.getValue(BedBlock.PART) != BedPart.HEAD) return false;
        BlockPos foot = head.relative(bed.getValue(BedBlock.FACING).getOpposite());
        if (!level.hasChunkAt(foot) || !level.getBlockState(foot).is(bed.getBlock())) return false;

        var corner = house.at(1 - house.width(), 1, 1 - house.depth());
        var opposite = house.at(house.width() - 1, bedroom + 4, house.depth() - 1);
        var inside = new AABB(corner, opposite).expandTowards(1, 1, 1);
        if (!level.getEntitiesOfClass(Mob.class, inside, mob -> mob.isAlive()
                && (mob instanceof Villager || mob.getTags().contains(RESIDENT))).isEmpty()) return true;

        var uuid = UUID.nameUUIDFromBytes((DATA + "/" + level.getSeed() + "/" + id).getBytes(StandardCharsets.UTF_8));
        if (level.getEntity(uuid) != null) return true;
        Mob resident = createResident(level);
        if (resident == null) return false;
        resident.setUUID(uuid);
        resident.addTag(RESIDENT);
        resident.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, house.facing().toYRot(), 0);
        if (!level.noCollision(resident, resident.getBoundingBox()) || !resident.checkSpawnObstruction(level)) return false;
        resident.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), MobSpawnType.STRUCTURE, null, null);
        resident.setPersistenceRequired();
        return level.addFreshEntity(resident);
    }

    private static Mob createResident(ServerLevel level) {
        return EntityType.VILLAGER.create(level);
    }

    private static final class Population extends SavedData {
        private final HashSet<Long> seeded = new HashSet<>();
        private final HashSet<Long> indexed = new HashSet<>();

        static Population load(CompoundTag tag) {
            var data = new Population();
            for (long id : tag.getLongArray("Households")) data.seeded.add(id);
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLongArray("Households", seeded.stream().mapToLong(Long::longValue).sorted().toArray());
            return tag;
        }
    }

    private OrthResidents() {}
}
