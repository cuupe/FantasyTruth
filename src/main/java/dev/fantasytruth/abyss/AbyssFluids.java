package dev.fantasytruth.abyss;

import dev.fantasytruth.FantasyTruth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

final class AbyssFluids {
    private static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, FantasyTruth.ID);
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, FantasyTruth.ID);
    private static final List<RegistryObject<Item>> BUCKETS = new ArrayList<>();

    private AbyssFluids() {}

    static void define(String id, int light, DeferredRegister<Block> blocks, DeferredRegister<Item> items) {
        var type = TYPES.register(id, () -> new AbyssFluidType(id, light));
        var parts = new FluidParts();
        parts.source = FLUIDS.register(id, () -> new ForgeFlowingFluid.Source(parts.properties));
        parts.flowing = FLUIDS.register("flowing_" + id, () -> new ForgeFlowingFluid.Flowing(parts.properties));
        var liquid = blocks.register(id, () -> new AbyssLiquid(parts.source.get(),
                BlockBehaviour.Properties.copy(Blocks.WATER).lightLevel(state -> light)));
        RegistryObject<Item> bucket = items.register(id + "_bucket", () -> new BucketItem(parts.source.get(),
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        parts.properties = new ForgeFlowingFluid.Properties(type, parts.source, parts.flowing)
                .block(liquid).bucket(bucket).tickRate(id.equals("l5_black_sea") ? 5 : 15);
        BUCKETS.add(bucket);
    }

    static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
    }

    static List<RegistryObject<Item>> buckets() { return List.copyOf(BUCKETS); }

    private static final class FluidParts {
        RegistryObject<ForgeFlowingFluid.Source> source;
        RegistryObject<ForgeFlowingFluid.Flowing> flowing;
        ForgeFlowingFluid.Properties properties;
    }

    private static final class AbyssLiquid extends LiquidBlock {
        AbyssLiquid(ForgeFlowingFluid.Source fluid, BlockBehaviour.Properties properties) {
            super(fluid, properties);
        }
    }

    private static final class AbyssFluidType extends FluidType {
        private final ResourceLocation texture;

        AbyssFluidType(String id, int light) {
            super(FluidType.Properties.create()
                    .descriptionId("block." + FantasyTruth.ID + "." + id)
                    .lightLevel(light)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
            texture = new ResourceLocation(FantasyTruth.ID, "block/" + id);
        }

        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override public ResourceLocation getStillTexture() { return texture; }
                @Override public ResourceLocation getFlowingTexture() { return texture; }
            });
        }
    }
}
