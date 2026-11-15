package dev.fantasytruth.abyss;

import dev.fantasytruth.FantasyTruth;
import java.util.Collection;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class OrthBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FantasyTruth.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FantasyTruth.ID);

    static final RegistryObject<Block> STONE = block("abyss_stone", Blocks.STONE);
    static final RegistryObject<Block> CHALK = block("l1_chalk", Blocks.CALCITE);
    static final RegistryObject<Block> COBBLE = block("orth_cobble", Blocks.COBBLESTONE);
    static final RegistryObject<Block> PLASTER = block("orth_plaster", Blocks.SANDSTONE);
    static final RegistryObject<Block> TIMBER = block("orth_timber", Blocks.OAK_PLANKS);
    static final RegistryObject<Block> SLATE = block("orth_slate", Blocks.DEEPSLATE_TILES);
    static final RegistryObject<Block> PLANK = block("orth_dock_plank", Blocks.SPRUCE_PLANKS);
    static final RegistryObject<Block> CRATE = block("orth_crate", () -> AbyssDevices.create("orth_crate", BlockBehaviour.Properties.copy(Blocks.BARREL)));
    static final RegistryObject<Block> BOLT = block("orth_iron_bolt", Blocks.IRON_BLOCK);
    static final RegistryObject<Block> PIER = block("pier_platform", Blocks.COPPER_BLOCK);
    static final RegistryObject<Block> CABLE = block("cable", Blocks.IRON_BLOCK);
    static final RegistryObject<Block> SAIL = block("orth_windmill_blade", Blocks.WHITE_WOOL);
    static final RegistryObject<Block> ABYSS_MOSS = block("abyss_moss", Blocks.MOSS_BLOCK);
    static final RegistryObject<Block> GLOW_MOSS = block("glow_moss", Blocks.MOSS_BLOCK, 5);
    static final RegistryObject<Block> AMAGIRI = block("amagiri", AmagiriBlock::new);
    static final RegistryObject<Block> PETRIFIED_LOG = block("giant_log", () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LOG)));
    static final RegistryObject<Block> MILL_STONE = block("l1_windmill_base", Blocks.STONE_BRICKS);
    static final RegistryObject<Block> CAMP_CRATE = block("camp_supply_crate", () -> AbyssDevices.create("camp_supply_crate", BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
    static final RegistryObject<Block> RELIC_ORE = block("relic_ore", Blocks.COPPER_ORE);

    static final RegistryObject<Block> FOSSIL_STUMP = block("fossil_stump", () -> new LowDecoration(
            BlockBehaviour.Properties.copy(Blocks.CALCITE).noOcclusion(), Block.box(2, 0, 2, 14, 11, 14)));
    static final RegistryObject<Block> FOSSIL_ROOT = block("fossil_root",
            () -> new FacingDecoration(BlockBehaviour.Properties.copy(Blocks.TUFF), false));
    static final RegistryObject<Block> FOSSIL_OUTCROP = block("fossil_outcrop",
            () -> new FacingDecoration(BlockBehaviour.Properties.copy(Blocks.TUFF), false));
    static final RegistryObject<Block> SPRING_SEDGE = block("spring_sedge", () -> new LowDecoration(
            BlockBehaviour.Properties.copy(Blocks.FERN).noCollission().noOcclusion(), Block.box(3, 0, 3, 13, 15, 13)));
    static final RegistryObject<Block> SPRING_MOSS = block("spring_moss", () -> new LowDecoration(
            BlockBehaviour.Properties.copy(Blocks.MOSS_CARPET).noCollission().noOcclusion(), Block.box(0, 0, 0, 16, 1, 16)));
    static final RegistryObject<Block> SHELF_FUNGUS = block("shelf_fungus",
            () -> new FacingDecoration(BlockBehaviour.Properties.copy(Blocks.MOSS_BLOCK).noCollission().noOcclusion(), true));

    static {
        AbyssAssetCatalog.blocks(OrthBlocks::catalogBlock);
    }

    private static void catalogBlock(String id, String form, String material, float hardness, int light) {
        if (form.equals("fluid")) {
            AbyssFluids.define(id, light, BLOCKS, ITEMS);
            return;
        }
        block(id, () -> {
            Block template = switch (material) {
                case "wood" -> Blocks.OAK_PLANKS;
                case "metal" -> Blocks.IRON_BLOCK;
                case "glass" -> Blocks.GLASS;
                case "earth" -> Blocks.DIRT;
                case "plant" -> Blocks.MOSS_BLOCK;
                default -> Blocks.STONE;
            };
            var properties = BlockBehaviour.Properties.copy(template).strength(hardness, hardness < 0 ? 3600000F : Math.max(0, hardness * 2))
                    .lightLevel(state -> light);
            if (form.equals("cross") || form.equals("field") || id.equals("ropeladder")) properties.noCollission();
            if (form.equals("field")) properties.noLootTable().sound(SoundType.EMPTY);
            if (!form.equals("cube") && !form.equals("pillar")) properties.noOcclusion();
            if (form.equals("pillar")) return new RotatedPillarBlock(properties);
            return AbyssDevices.create(id, properties);
        });
    }

    private static RegistryObject<Block> block(String id, Block properties) {
        return block(id, properties, 0);
    }

    private static RegistryObject<Block> block(String id, Block properties, int light) {
        return block(id, () -> new Block(BlockBehaviour.Properties.copy(properties).lightLevel(state -> light)));
    }

    private static RegistryObject<Block> block(String id, Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(id, factory);
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus bus) {
        AbyssFluids.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        AbyssStores.register(bus);
    }

    static Collection<RegistryObject<Item>> items() {
        var buckets = AbyssFluids.buckets();
        return ITEMS.getEntries().stream().filter(entry -> !buckets.contains(entry)).toList();
    }

    private static final class LowDecoration extends Block {
        private final VoxelShape shape;

        LowDecoration(Properties properties, VoxelShape shape) {
            super(properties);
            this.shape = shape;
        }

        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shape;
        }
    }

    private static final class FacingDecoration extends HorizontalDirectionalBlock {
        private final boolean shelf;

        FacingDecoration(Properties properties, boolean shelf) {
            super(properties);
            this.shelf = shelf;
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            Direction side = context.getClickedFace();
            return defaultBlockState().setValue(FACING, side.getAxis().isHorizontal() ? side : context.getHorizontalDirection().getOpposite());
        }

        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            if (!shelf) return super.getShape(state, level, pos, context);
            return switch (state.getValue(FACING)) {
                case SOUTH -> Block.box(2, 1, 0, 14, 13, 10);
                case WEST -> Block.box(6, 1, 2, 16, 13, 14);
                case EAST -> Block.box(0, 1, 2, 10, 13, 14);
                default -> Block.box(2, 1, 6, 14, 13, 16);
            };
        }
    }

    private OrthBlocks() {}
}
