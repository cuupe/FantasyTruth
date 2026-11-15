package dev.fantasytruth.abyss;

import dev.fantasytruth.FantasyTruth;
import java.util.ArrayList;
import java.util.List;
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
    private static final RegistryObject<Item> COOKED_DEMONFISH = ITEMS.register("cooked_demonfish",
            () -> AbyssGear.food(new Item.Properties(), 6, 0.65F));
    private static final List<RegistryObject<Item>> MATERIALS = new ArrayList<>();
    private static final List<RegistryObject<Item>> RELICS = new ArrayList<>();
    private static final List<RegistryObject<Item>> EQUIPMENT = new ArrayList<>();

    static {
        AbyssAssetCatalog.items(AbyssItems::catalogItem);
        catalogItem("forest_egg_relic", "relic", 16);
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
                    RELICS.forEach(item -> output.accept(item.get()));
                })
                .build());
        TABS.register("abyss_equipment", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 2)
                .title(Component.translatable("itemGroup.stratacubes.abyss_equipment"))
                .icon(() -> new ItemStack(DEPTH_GAUGE.get()))
                .displayItems((parameters, output) -> {
                    output.accept(DELVER_PICKAXE.get());
                    output.accept(DEPTH_GAUGE.get());
                    EQUIPMENT.forEach(item -> output.accept(item.get()));
                })
                .build());
        TABS.register("abyss_materials", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 3)
                .title(Component.translatable("itemGroup.stratacubes.abyss_materials"))
                .icon(() -> new ItemStack(MATERIALS.get(0).get()))
                .displayItems((parameters, output) -> {
                    output.accept(COOKED_DEMONFISH.get());
                    MATERIALS.forEach(item -> output.accept(item.get()));
                    AbyssFluids.buckets().forEach(item -> output.accept(item.get()));
                })
                .build());
    }

    private static void catalogItem(String id, String category, int stackSize) {
        var item = ITEMS.register(id, () -> AbyssGear.create(id, new Item.Properties().stacksTo(stackSize)));
        switch (category) {
            case "relic" -> RELICS.add(item);
            case "equipment" -> EQUIPMENT.add(item);
            default -> MATERIALS.add(item);
        }
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
                player.displayClientMessage(AbyssGear.depth(level, player.blockPosition()), true);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
    }

    private AbyssItems() {}
}
