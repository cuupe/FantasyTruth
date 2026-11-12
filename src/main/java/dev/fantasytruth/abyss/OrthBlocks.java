package dev.fantasytruth.abyss;

import dev.fantasytruth.FantasyTruth;
import java.util.Collection;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
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
    static final RegistryObject<Block> CRATE = block("orth_crate", Blocks.BARREL);
    static final RegistryObject<Block> BOLT = block("orth_iron_bolt", Blocks.IRON_BLOCK);
    static final RegistryObject<Block> PIER = block("pier_platform", Blocks.COPPER_BLOCK);
    static final RegistryObject<Block> CABLE = block("cable", Blocks.IRON_BLOCK);
    static final RegistryObject<Block> SAIL = block("orth_windmill_blade", Blocks.WHITE_WOOL);
    static final RegistryObject<Block> ABYSS_MOSS = block("abyss_moss", Blocks.MOSS_BLOCK);
    static final RegistryObject<Block> GLOW_MOSS = block("glow_moss", Blocks.MOSS_BLOCK, 5);
    static final RegistryObject<Block> PETRIFIED_LOG = block("giant_log", () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LOG)));
    static final RegistryObject<Block> MILL_STONE = block("l1_windmill_base", Blocks.STONE_BRICKS);
    static final RegistryObject<Block> CAMP_CRATE = block("camp_supply_crate", Blocks.OAK_PLANKS);
    static final RegistryObject<Block> RELIC_ORE = block("relic_ore", Blocks.COPPER_ORE);

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
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    static Collection<RegistryObject<Item>> items() { return ITEMS.getEntries(); }

    private OrthBlocks() {}
}
