package dev.fantasytruth.abyss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

final class AbyssDevices {
    static Block block(String id) { return ForgeRegistries.BLOCKS.getValue(AbyssGear.key(id)); }

    static Block create(String id, BlockBehaviour.Properties props) {
        return switch (id) {
            case "whistle_stand", "value_token_block", "orth_crate", "camp_supply_crate", "relic_fuel_generator", "orth_signboard" ->
                    new AbyssStores.StorageBlock(id, props);
            case "orth_street_lamp" -> new Lamp(props);
            case "forcefield_generator" -> new ForceField(props);
            case "fortitude_ash" -> new Ash(props.randomTicks());
            case "camp_tent" -> new Block(props.noCollission());
            case "camp_flag", "camp_bedroll", "ropeladder", "orth_rope_coil" -> new Device(id, props.noCollission());
            case "depth_meter", "compass_pedestal", "sun_clock", "curse_beacon", "relic_table", "camp_stove",
                 "orth_bell", "orth_waterwheel", "curse_box_block",
                 "hoist", "elevator_shaft", "gondola", "value_scale", "market_stall" -> new Device(id, props);
            default -> new Block(props);
        };
    }

    static InteractionResult hint(Player player, String key) {
        player.displayClientMessage(Component.translatable("message.stratacubes." + key), true);
        return InteractionResult.CONSUME;
    }

    private static final class Device extends Block {
        private final String id;
        Device(String id, Properties props) {
            super(props);
            this.id = id;
        }

        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                                InteractionHand hand, BlockHitResult hit) {
            ItemStack held = player.getItemInHand(hand);
            if ((id.equals("ropeladder") || id.equals("orth_rope_coil"))
                    && !held.is(block("ropeladder").asItem()) && !AbyssGear.is(held, "i_rope")) return InteractionResult.PASS;
            if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer explorer)) return InteractionResult.SUCCESS;
            switch (id) {
                case "depth_meter" -> player.displayClientMessage(AbyssGear.depth(level, pos), true);
                case "compass_pedestal" -> player.displayClientMessage(AbyssGear.bearing(pos), true);
                case "curse_beacon" -> player.displayClientMessage(Component.translatable("message.stratacubes.field.signal",
                        getAnalogOutputSignal(state, level, pos), 15), true);
                case "sun_clock" -> {
                    if (!level.canSeeSky(pos.above()) || !level.dimensionType().hasSkyLight()) return hint(player, "sun.no_sky");
                    long time = Math.floorMod(level.getDayTime(), 24000);
                    player.displayClientMessage(Component.translatable("message.stratacubes.sun", (time + 6000) / 1000 % 24,
                            String.format(java.util.Locale.ROOT, "%02d", time % 1000 * 60 / 1000)), true);
                }
                case "orth_waterwheel" -> player.displayClientMessage(
                        Component.translatable("message.stratacubes.wheel", flow(level, pos)), true);
                case "orth_bell" -> level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.8F, 1);
                case "camp_flag" -> player.displayClientMessage(Component.translatable("message.stratacubes.camp_flag",
                        pos.getX(), pos.getY(), pos.getZ()), true);
                case "camp_bedroll" -> { return respawn(explorer, server, pos); }
                case "camp_stove" -> { return cook(explorer, server, pos, hand, held); }
                case "relic_table" -> { return appraise(explorer, server, pos, held); }
                case "curse_box_block" -> {
                    if (!AbyssGear.is(held, "relic_shard") || AbyssGear.sheltered(player)) return hint(player, "shelter.fuel");
                    AbyssGear.spend(player, held, 1);
                    AbyssGear.shelter(player, 320);
                    return hint(player, "sheltered");
                }
                case "ropeladder", "orth_rope_coil" -> { return extendRope(explorer, server, pos, hit.getDirection(), held, id.equals("orth_rope_coil")); }
                case "hoist", "elevator_shaft", "gondola" -> { return transport(explorer, server, pos, id); }
                case "value_scale" -> { return exchange(player, held); }
                case "market_stall" -> {
                    if (!AbyssGear.is(held, "i_value_token") || held.getCount() < 3) return hint(player, "value.price");
                    AbyssGear.spend(player, held, 3);
                    AbyssGear.give(player, new ItemStack(AbyssGear.item("mat_ration"), 2));
                }
                default -> { return InteractionResult.PASS; }
            }
            return InteractionResult.CONSUME;
        }

        @Override public boolean hasAnalogOutputSignal(BlockState state) {
            return switch (id) {
                case "curse_beacon", "sun_clock", "orth_waterwheel" -> true;
                default -> false;
            };
        }

        @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
            return switch (id) {
                case "curse_beacon" -> field(level, pos);
                case "sun_clock" -> level.dimensionType().hasSkyLight() && level.canSeeSky(pos.above())
                        ? Math.max(0, (int) Math.round(15 * Math.cos(level.getSunAngle(1)))) : 0;
                case "orth_waterwheel" -> flow(level, pos);
                default -> 0;
            };
        }

        @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
            super.onPlace(state, level, pos, old, moving);
            if (!level.isClientSide && id.equals("sun_clock")) level.scheduleTick(pos, this, 20);
        }

        @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block source, BlockPos from, boolean moving) {
            if (!level.isClientSide && id.equals("orth_waterwheel")) level.updateNeighbourForOutputSignal(pos, this);
        }

        @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
            if (id.equals("sun_clock")) {
                level.updateNeighbourForOutputSignal(pos, this);
                level.scheduleTick(pos, this, 100);
            }
        }
    }

    private static InteractionResult exchange(Player player, ItemStack input) {
        String unit = "i_value_token", ten = "i_value_token_10", hundred = "i_value_token_100";
        if (AbyssGear.is(input, "relic_shard") || AbyssGear.is(input, "mat_gold_leaf")) {
            AbyssGear.spend(player, input, 1);
            AbyssGear.give(player, new ItemStack(AbyssGear.item(unit)));
        } else if (player.isShiftKeyDown() && (AbyssGear.is(input, ten) || AbyssGear.is(input, hundred))) {
            String change = AbyssGear.is(input, hundred) ? ten : unit;
            AbyssGear.spend(player, input, 1);
            AbyssGear.give(player, new ItemStack(AbyssGear.item(change), 10));
        } else if (input.getCount() >= 10 && (AbyssGear.is(input, unit) || AbyssGear.is(input, ten))) {
            String larger = AbyssGear.is(input, ten) ? hundred : ten;
            AbyssGear.spend(player, input, 10);
            AbyssGear.give(player, new ItemStack(AbyssGear.item(larger)));
        } else return hint(player, "value.offer");
        return InteractionResult.CONSUME;
    }

    private static int field(Level level, BlockPos pos) {
        var layer = AbyssLayer.of(level.dimension());
        if (layer == AbyssLayer.FOREST) return ForestExpedition.weakField(pos) ? 5 : 12;
        if (layer == AbyssLayer.EDGE) return 3;
        return layer == null ? 0 : 15;
    }

    private static int flow(Level level, BlockPos pos) {
        double speed = 0;
        for (var side : Direction.Plane.HORIZONTAL) {
            var adjacent = pos.relative(side);
            if (!level.hasChunkAt(adjacent)) continue;
            var water = level.getFluidState(adjacent);
            if (water.is(FluidTags.WATER)) speed = Math.max(speed, water.getFlow(level, adjacent).length());
        }
        return Math.min(15, (int) Math.ceil(speed * 15));
    }

    private static InteractionResult respawn(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (AbyssLayer.of(level.dimension()) != null && AbyssLayer.of(level.dimension()).ordinal() >= 4)
            return hint(player, "bedroll.deep");
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos target = pos.relative(side);
            if (!landing(level, target, player)) continue;
            player.setRespawnPosition(level.dimension(), target, player.getYRot(), true, false);
            return hint(player, "bedroll.set");
        }
        return hint(player, "bedroll.obstructed");
    }

    private static InteractionResult cook(ServerPlayer player, ServerLevel level, BlockPos pos,
                                          InteractionHand hand, ItemStack held) {
        ItemStack fuel = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (held.isEmpty() || !(fuel.is(Items.COAL) || fuel.is(Items.CHARCOAL) || AbyssGear.is(fuel, "i_oil")))
            return hint(player, "stove.instructions");
        var input = new SimpleContainer(held.copyWithCount(1));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMOKING, input, level);
        if (recipe.isEmpty()) return hint(player, "stove.invalid");
        var output = recipe.get().assemble(input, level.registryAccess());
        if (output.isEmpty()) return hint(player, "stove.invalid");
        boolean bottle = AbyssGear.is(fuel, "i_oil") && !player.getAbilities().instabuild;
        AbyssGear.spend(player, held, 1);
        AbyssGear.spend(player, fuel, 1);
        if (bottle) AbyssGear.give(player, new ItemStack(Items.GLASS_BOTTLE));
        AbyssGear.give(player, output);
        level.playSound(null, pos, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult appraise(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held) {
        if (!AbyssGear.is(held, "relic_shard") || held.getCount() < 9) return hint(player, "appraise.instructions");
        var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player).create(LootContextParamSets.CHEST);
        var relics = level.getServer().getLootData().getLootTable(AbyssGear.key("gameplay/appraisal")).getRandomItems(params);
        if (relics.isEmpty()) return hint(player, "appraise.empty");
        AbyssGear.spend(player, held, 9);
        relics.forEach(relic -> AbyssGear.give(player, relic));
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.78F);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult extendRope(ServerPlayer player, ServerLevel level, BlockPos pos, Direction side,
                                                ItemStack held, boolean fromCoil) {
        Block rope = block("ropeladder");
        BlockPos bottom = fromCoil && side.getAxis().isHorizontal() ? pos.relative(side) : fromCoil ? pos.below() : pos;
        if (level.isOutsideBuildHeight(bottom) || !level.hasChunkAt(bottom)) return hint(player, "rope.blocked");
        int above = 0;
        if (!fromCoil) {
            while (above < 32 && !level.isOutsideBuildHeight(bottom.above()) && level.getBlockState(bottom.above()).is(rope)) {
                bottom = bottom.above();
                above++;
            }
        }
        int length = 0;
        while (length < 32 && !level.isOutsideBuildHeight(bottom) && level.getBlockState(bottom).is(rope)) {
            bottom = bottom.below();
            length++;
        }
        if (length == 32 || level.isOutsideBuildHeight(bottom) || !level.hasChunkAt(bottom)
                || !level.getBlockState(bottom).canBeReplaced() || !level.getFluidState(bottom).isEmpty())
            return hint(player, "rope.blocked");
        if (!level.setBlockAndUpdate(bottom, rope.defaultBlockState())) return hint(player, "rope.blocked");
        AbyssGear.spend(player, held, 1);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult transport(ServerPlayer player, ServerLevel level, BlockPos pos, String id) {
        Block station = block(id), cable = block("cable"), shaft = block("elevator_shaft");
        boolean reverse = player.isShiftKeyDown();
        // Cables can never cross unloaded chunks. Each endpoint must offer standing room.
        Direction first = reverse ? Direction.DOWN : Direction.UP;
        var sides = id.equals("gondola") ? new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}
                : new Direction[]{first, first.getOpposite()};
        for (var dir : sides) {
            for (int i = 1; i <= 64; i++) {
                BlockPos end = pos.relative(dir, i);
                if (level.isOutsideBuildHeight(end) || !level.hasChunkAt(end)) break;
                var test = level.getBlockState(end);
                if (i >= 2 && test.is(station)) {
                    for (Direction banks : Direction.Plane.HORIZONTAL) {
                        BlockPos exit = end.relative(banks);
                        if (!landing(level, exit, player)) continue;
                        player.teleportTo(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5);
                        player.setDeltaMovement(Vec3.ZERO);
                        player.fallDistance = 0;
                        return hint(player, "transport.arrived");
                    }
                    break;
                }
                if (!test.is(cable) && !test.is(shaft)) break;
            }
        }
        return hint(player, "transport.track");
    }

    private static boolean landing(ServerLevel level, BlockPos pos, Player player) {
        return level.hasChunksAt(pos.offset(-1, -1, -1), pos.offset(1, 2, 1))
                && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && level.noCollision(player, player.getBoundingBox().move(
                        pos.getX() + 0.5 - player.getX(), pos.getY() - player.getY(), pos.getZ() + 0.5 - player.getZ()));
    }

    private static final class Ash extends Block {
        Ash(Properties props) { super(props); }

        @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
            if (random.nextInt(8) == 0 && level.isEmptyBlock(pos.above()) && level.getMaxLocalRawBrightness(pos.above()) >= 9)
                level.setBlockAndUpdate(pos.above(), block("eternal_fortune").defaultBlockState());
        }
    }

    private static final class ForceField extends Block {
        private static final net.minecraft.world.level.block.state.properties.IntegerProperty CHARGE =
                net.minecraft.world.level.block.state.properties.IntegerProperty.create("charge", 0, 15);

        ForceField(Properties props) {
            super(props);
            registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(CHARGE);
        }

        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                                InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            ItemStack input = player.getItemInHand(hand);
            if (!level.hasNeighborSignal(pos) || !AbyssGear.is(input, "i_crystal_shard")) return hint(player, "field.fuel");
            if (state.getValue(CHARGE) == 0) {
                AbyssGear.spend(player, input, 1);
                level.setBlock(pos, state.setValue(CHARGE, 15), Block.UPDATE_CLIENTS);
                level.scheduleTick(pos, this, 1);
            }
            return hint(player, "field.active");
        }

        @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
            int charge = state.getValue(CHARGE);
            if (charge == 0) return;
            if (!level.hasNeighborSignal(pos)) {
                level.setBlock(pos, state.setValue(CHARGE, 0), Block.UPDATE_CLIENTS);
                return;
            }
            var zone = new net.minecraft.world.phys.AABB(pos).inflate(4, 5, 4);
            for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, zone,
                    mob -> mob.getDeltaMovement().y > 0.08 && !mob.isSpectator()
                            && (!(mob instanceof Player p) || !p.getAbilities().flying))) {
                entity.setDeltaMovement(entity.getDeltaMovement().multiply(1, 0.2, 1));
                entity.hurtMarked = true;
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
            }
            level.setBlock(pos, state.setValue(CHARGE, charge - 1), Block.UPDATE_CLIENTS);
            if (charge > 1) level.scheduleTick(pos, this, 15);
        }
    }

    private static final class Lamp extends Block {
        Lamp(Properties props) {
            super(props.lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 14 : 0));
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.LIT, true)
                    .setValue(BlockStateProperties.POWERED, false));
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.LIT, BlockStateProperties.POWERED);
        }

        @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block source, BlockPos from, boolean moving) {
            if (level.isClientSide) return;
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(BlockStateProperties.POWERED)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered)
                        .setValue(BlockStateProperties.LIT, powered || state.getValue(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.POWERED)), 3);
            }
        }

        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            ItemStack stack = player.getItemInHand(hand);
            boolean oil = AbyssGear.is(stack, "i_oil");
            if (state.getValue(BlockStateProperties.POWERED) || (!oil && (!stack.isEmpty() || !player.isShiftKeyDown())))
                return InteractionResult.PASS;
            boolean lit = state.getValue(BlockStateProperties.LIT);
            if (oil && lit) return InteractionResult.PASS;
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(BlockStateProperties.LIT, !lit), 3);
                if (oil && !player.getAbilities().instabuild) {
                    stack.shrink(1);
                    AbyssGear.give(player, new ItemStack(Items.GLASS_BOTTLE));
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    private AbyssDevices() {}
}
