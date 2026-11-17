package dev.fantasytruth.abyss;

import com.mojang.serialization.Codec;
import dev.fantasytruth.FantasyTruth;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Vanilla structure start and locate target for the entrance plaza. */
public final class NaturalAbyssStructure extends Structure {
    private static final Codec<NaturalAbyssStructure> CODEC = simpleCodec(NaturalAbyssStructure::new);
    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, FantasyTruth.ID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, FantasyTruth.ID);
    private static final RegistryObject<StructureType<NaturalAbyssStructure>> TYPE = TYPES.register("abyss", () -> () -> CODEC);
    private static final RegistryObject<StructurePieceType> PIECE = PIECES.register("abyss_lookout",
            () -> (context, tag) -> new Lookout(tag));

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        PIECES.register(bus);
    }

    private NaturalAbyssStructure(StructureSettings settings) { super(settings); }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!NaturalAbyssSite.vanillaGenerator(context.chunkGenerator(), context.heightAccessor())) return Optional.empty();
        var pos = NaturalAbyssSite.lookout(context.chunkPos());
        return Optional.of(new GenerationStub(pos, pieces -> pieces.addPiece(new Lookout(pos))));
    }

    @Override public StructureType<?> type() { return TYPE.get(); }

    private static final class Lookout extends StructurePiece {
        private Lookout(BlockPos pos) {
            super(PIECE.get(), 0, new BoundingBox(pos.getX(), pos.getY() - 16, pos.getZ(),
                    pos.getX(), pos.getY() + 40, pos.getZ()));
        }

        private Lookout(CompoundTag tag) { super(PIECE.get(), tag); }

        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                                BoundingBox bounds, ChunkPos chunk, BlockPos pivot) {
            int x = boundingBox.minX(), z = boundingBox.minZ();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            var base = new BlockPos(x, y, z);
            if (boundingBox.isInside(base.above()) && bounds.isInside(base) && level.isEmptyBlock(base) && level.isEmptyBlock(base.above())) {
                level.setBlock(base, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 2);
                level.setBlock(base.above(), Blocks.LANTERN.defaultBlockState(), 2);
            }
        }
    }
}
