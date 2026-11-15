package dev.fantasytruth.abyss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class AmagiriBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHOOT = Block.box(3, 0, 3, 13, 8, 13);

    AmagiriBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.FERN));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    static BlockState towardCenter(int x, int z) {
        int dx = AbyssLayer.CENTER_X - x, dz = AbyssLayer.CENTER_Z - z;
        Direction facing = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        return OrthBlocks.AMAGIRI.get().defaultBlockState().setValue(FACING, facing);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var pos = context.getClickedPos();
        return towardCenter(pos.getX(), pos.getZ());
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> states) {
        states.add(FACING);
    }

    @Override public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                         BlockPos pos, CollisionContext collision) {
        return SHOOT;
    }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
                                           net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos other) {
        return side == Direction.DOWN && !canSurvive(state, level, pos)
                ? Blocks.AIR.defaultBlockState() : super.updateShape(state, side, neighbor, level, pos, other);
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty() || AbyssLayer.of(level.dimension()) != AbyssLayer.FOREST) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            String key = "message.stratacubes.amagiri." + state.getValue(FACING).getName();
            player.displayClientMessage(Component.translatable(key), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
