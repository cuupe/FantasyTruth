package dev.fantasytruth.abyss;

import dev.fantasytruth.FantasyTruth;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AbyssItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FantasyTruth.ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FantasyTruth.ID);
    private static final RegistryObject<Item> RELIC_SHARD = ITEMS.register("relic_shard", () -> new Item(new Item.Properties()));
    private static final RegistryObject<Item> SPENT_RELIC = ITEMS.register("spent_relic", () -> new Item(new Item.Properties()));
    private static final RegistryObject<Item> DELVER_PICKAXE = ITEMS.register("delver_pickaxe",
            DelverPickaxe::new);
    private static final RegistryObject<Item> DEPTH_GAUGE = ITEMS.register("depth_gauge",
            () -> new DepthGaugeItem(new Item.Properties().stacksTo(1)));

    static {
        TABS.register("abyss_blocks", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup.stratacubes.abyss_blocks"))
                .icon(() -> new ItemStack(OrthBlocks.RELIC_ORE.get()))
                .displayItems((parameters, output) -> OrthBlocks.items().forEach(item -> output.accept(item.get())))
                .build());
        TABS.register("abyss_relics", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 1)
                .title(Component.translatable("itemGroup.stratacubes.abyss_relics"))
                .icon(() -> new ItemStack(RELIC_SHARD.get()))
                .displayItems((parameters, output) -> {
                    output.accept(RELIC_SHARD.get());
                    output.accept(SPENT_RELIC.get());
                })
                .build());
        TABS.register("abyss_equipment", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 2)
                .title(Component.translatable("itemGroup.stratacubes.abyss_equipment"))
                .icon(() -> new ItemStack(DEPTH_GAUGE.get()))
                .displayItems((parameters, output) -> {
                    output.accept(DELVER_PICKAXE.get());
                    output.accept(DEPTH_GAUGE.get());
                })
                .build());
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }

    private static final class DelverPickaxe extends PickaxeItem {
        DelverPickaxe() { super(Tiers.IRON, 1, -2.8F, new Item.Properties()); }
    }

    private static final class DepthGaugeItem extends Item {
        DepthGaugeItem(Properties properties) { super(properties); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (!level.isClientSide) {
                int y = player.getBlockY();
                Component reading = level.dimensionTypeId().location().equals(AbyssTransit.EDGE_TYPE)
                        ? Component.translatable("item.stratacubes.depth_gauge.depth", Math.max(0, -y))
                        : Component.translatable("item.stratacubes.depth_gauge.altitude", y);
                player.displayClientMessage(reading, true);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
    }

    private AbyssItems() {}
}
