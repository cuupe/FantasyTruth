package dev.fantasytruth.abyss;

import dev.fantasytruth.FantasyTruth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AbyssStores {
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FantasyTruth.ID);
    public static final RegistryObject<BlockEntityType<Data>> TYPE = TYPES.register("abyss_storage",
            () -> BlockEntityType.Builder.of(Data::new, AbyssDevices.block("orth_crate"), AbyssDevices.block("camp_supply_crate"),
                    AbyssDevices.block("whistle_stand"), AbyssDevices.block("value_token_block"),
                    AbyssDevices.block("relic_fuel_generator"), AbyssDevices.block("orth_signboard")).build(null));
    private static final int CAPACITY = 32000, FUEL = 2000, TRANSFER = 160;

    static void register(IEventBus bus) {
        TYPES.register(bus);
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, AbyssStores::attachEnergy);
    }

    private static void attachEnergy(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof Data data)) return;
        LazyOptional<IEnergyStorage> cap = LazyOptional.of(() -> data.output);
        event.addCapability(AbyssGear.key("relic_energy"), new ICapabilityProvider() {
            @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
                return capability == ForgeCapabilities.ENERGY && data.generator() ? cap.cast() : LazyOptional.empty();
            }
        });
        event.addListener(cap::invalidate);
    }

    static final class StorageBlock extends Block implements EntityBlock {
        private final String id;
        StorageBlock(String id, Properties props) { super(props); this.id = id; }

        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Data(pos, state); }

        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                                InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            Data data;
            if (level.getBlockEntity(pos) instanceof Data existing) data = existing;
            else {
                // Structures from older saves may not have a block entity at this position.
                data = new Data(pos, state);
                level.setBlockEntity(data);
            }
            ItemStack held = player.getItemInHand(hand);
            switch (id) {
                case "relic_fuel_generator" -> {
                    if (AbyssGear.is(held, "relic_shard") && data.energy <= CAPACITY - FUEL) {
                        AbyssGear.spend(player, held, 1);
                        data.setEnergy(data.energy + FUEL);
                    }
                    player.displayClientMessage(Component.translatable("message.stratacubes.energy", data.energy, CAPACITY), true);
                }
                case "orth_signboard" -> {
                    var label = data.getItem(0);
                    if (held.is(net.minecraft.world.item.Items.NAME_TAG) && held.hasCustomHoverName() && label.isEmpty()) {
                        data.setItem(0, held.copyWithCount(1));
                        AbyssGear.spend(player, held, 1);
                        player.displayClientMessage(data.getItem(0).getHoverName(), true);
                    } else if (held.isEmpty() && !label.isEmpty() && player.isShiftKeyDown()) {
                        AbyssGear.give(player, label.copy());
                        data.setItem(0, ItemStack.EMPTY);
                    } else if (!label.isEmpty()) player.displayClientMessage(label.getHoverName(), true);
                    else return AbyssDevices.hint(player, "orth_signboard");
                }
                case "whistle_stand", "value_token_block" -> {
                    ItemStack stored = data.getItem(0);
                    if (held.isEmpty() && !stored.isEmpty()) {
                        AbyssGear.give(player, stored.copy());
                        data.setItem(0, ItemStack.EMPTY);
                    } else if (id.equals("whistle_stand") && stored.isEmpty() && held.is(AbyssGear.WHISTLES)) {
                        data.setItem(0, held.copyWithCount(1));
                        AbyssGear.spend(player, held, 1);
                    } else if (id.equals("value_token_block") && AbyssGear.is(held, "i_value_token") && stored.getCount() < 64) {
                        int amount = Math.min(64 - stored.getCount(), held.getCount());
                        data.setItem(0, held.copyWithCount(stored.getCount() + amount));
                        AbyssGear.spend(player, held, amount);
                    } else return AbyssDevices.hint(player, id.equals("whistle_stand") ? "whistle_stand" : "token_slot");
                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                    level.updateNeighbourForOutputSignal(pos, this);
                }
                default -> player.openMenu(data);
            }
            return InteractionResult.CONSUME;
        }

        @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }

        @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
            if (!(level.getBlockEntity(pos) instanceof Data data)) return 0;
            return switch (id) {
                case "relic_fuel_generator" -> data.energy * 15 / CAPACITY;
                case "whistle_stand" -> data.getItem(0).isEmpty() ? 0 : 15;
                case "value_token_block" -> (data.getItem(0).getCount() * 15 + 63) / 64;
                default -> AbstractContainerMenu.getRedstoneSignalFromContainer(data);
            };
        }

        @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
            if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof Data data) {
                Containers.dropContents(level, pos, data);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, next, moving);
        }

        @Override @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return !level.isClientSide && id.equals("relic_fuel_generator") && type == TYPE.get()
                    ? (BlockEntityTicker<T>) (BlockEntityTicker<Data>) Data::pushEnergy : null;
        }
    }

    public static final class Data extends RandomizableContainerBlockEntity implements WorldlyContainer {
        private static final int[] STORAGE = {0, 1, 2, 3, 4, 5, 6, 7, 8}, CLOSED = {};
        private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        private int energy;
        private final IEnergyStorage output = new IEnergyStorage() {
            @Override public int receiveEnergy(int amount, boolean simulate) { return 0; }
            @Override public int extractEnergy(int amount, boolean simulate) {
                int sent = Math.max(0, Math.min(energy, Math.min(amount, TRANSFER)));
                if (!simulate && sent != 0) setEnergy(energy - sent);
                return sent;
            }
            @Override public int getEnergyStored() { return energy; }
            @Override public int getMaxEnergyStored() { return CAPACITY; }
            @Override public boolean canExtract() { return true; }
            @Override public boolean canReceive() { return false; }
        };

        Data(BlockPos pos, BlockState state) { super(TYPE.get(), pos, state); }
        @Override protected NonNullList<ItemStack> getItems() { return items; }
        @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
        @Override public int getContainerSize() { return 9; }
        @Override protected Component getDefaultName() { return getBlockState().getBlock().getName(); }
        @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new ChestMenu(MenuType.GENERIC_9x1, id, inventory, this, 1); }

        private boolean crate() {
            var state = getBlockState();
            return state.is(AbyssDevices.block("orth_crate")) || state.is(AbyssDevices.block("camp_supply_crate"));
        }

        @Override public int[] getSlotsForFace(Direction side) { return crate() ? STORAGE : CLOSED; }
        @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return crate(); }
        @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return crate(); }

        private boolean generator() { return getBlockState().is(AbyssDevices.block("relic_fuel_generator")); }

        @Override public void load(CompoundTag tag) {
            super.load(tag);
            items = NonNullList.withSize(9, ItemStack.EMPTY);
            if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
            energy = Math.max(0, Math.min(CAPACITY, tag.getInt("AbyssEnergy")));
        }

        @Override protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
            if (energy != 0) tag.putInt("AbyssEnergy", energy);
        }

        @Override public CompoundTag getUpdateTag() {
            var tag = new CompoundTag();
            if (getBlockState().is(AbyssDevices.block("whistle_stand"))) ContainerHelper.saveAllItems(tag, items);
            return tag;
        }
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

        private void setEnergy(int value) {
            int previousSignal = energy * 15 / CAPACITY;
            energy = value;
            setChanged();
            if (level != null && energy * 15 / CAPACITY != previousSignal)
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }

        private static void pushEnergy(Level level, BlockPos pos, BlockState state, Data data) {
            if (data.energy == 0) return;
            int remaining = Math.min(data.energy, TRANSFER);
            int before = remaining;
            for (Direction side : Direction.values()) {
                var neighbor = pos.relative(side);
                if (!level.hasChunkAt(neighbor)) continue;
                var block = level.getBlockEntity(neighbor);
                if (!((Object) block instanceof ICapabilityProvider provider)) continue;
                var target = provider.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).orElse(null);
                if (target == null || !target.canReceive()) continue;
                remaining -= target.receiveEnergy(remaining, false);
                if (remaining <= 0) break;
            }
            if (remaining < before) data.setEnergy(data.energy - (before - remaining));
        }
    }

    private AbyssStores() {}
}
