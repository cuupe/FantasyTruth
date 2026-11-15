package dev.fantasytruth.abyss;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

final class AbyssGear {
    static final TagKey<Item> WHISTLES = TagKey.create(Registries.ITEM, key("whistles"));
    private static final String SHELTER_UNTIL = "StrataCubesShelterUntil";

    static ResourceLocation key(String id) { return new ResourceLocation("stratacubes", id); }

    static Item item(String name) { return ForgeRegistries.ITEMS.getValue(key(name)); }

    static boolean is(ItemStack stack, String id) { return stack.is(item(id)); }

    static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    static void spend(Player player, ItemStack stack, int count) {
        if (!player.getAbilities().instabuild) stack.shrink(count);
    }

    static void shelter(Player player, int ticks) {
        ((net.minecraftforge.common.extensions.IForgeEntity) player).getPersistentData().putLong(SHELTER_UNTIL, player.level().getGameTime() + ticks);
    }

    static boolean sheltered(Player player) {
        return ((net.minecraftforge.common.extensions.IForgeEntity) player).getPersistentData().getLong(SHELTER_UNTIL) > player.level().getGameTime();
    }

    static Component bearing(BlockPos pos) {
        int dx = AbyssLayer.CENTER_X - pos.getX(), dz = AbyssLayer.CENTER_Z - pos.getZ();
        if (Math.abs(dx) < 2 && Math.abs(dz) < 2) return Component.translatable("message.stratacubes.compass.center");
        return Component.translatable("message.stratacubes.compass.bearing",
                Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(dx, -dz))), 360),
                Math.round(Math.hypot(dx, dz)));
    }

    static Component depth(Level level, BlockPos pos) {
        var layer = AbyssLayer.of(level.dimension());
        if (level instanceof ServerLevel server && layer == null && AbyssTransit.expedition(server)
                && pos.getY() < 0 && OrthIsland.inShaft(pos.getX(), pos.getZ())) layer = AbyssLayer.EDGE;
        if (layer == null) return Component.translatable("item.stratacubes.depth_gauge.altitude", pos.getY());
        int depth;
        if (layer == AbyssLayer.EDGE) depth = Math.max(0, -pos.getY());
        else if (layer == AbyssLayer.FOREST && level instanceof ServerLevel server
                && server.getChunkSource().getGenerator() instanceof AbyssChunkGenerator generator && generator.openForest()) {
            depth = ForestCoordinates.depth(pos.getY(), generator.exitY());
        } else {
            int[] boundaries = {0, 1350, 2600, 7000, 12000, 13000, 15500, 20000};
            double fraction = Math.max(0, Math.min(1, (layer.entrance - pos.getY()) / (double) (layer.entrance - layer.exit)));
            int index = layer.ordinal();
            depth = boundaries[index] + (int) Math.round(fraction * (boundaries[index + 1] - boundaries[index]));
        }
        return Component.translatable("message.stratacubes.depth.layer",
                Component.translatable("abyss.stratacubes." + layer.id), depth);
    }

    static Item create(String id, Item.Properties props) {
        if (id.startsWith("i_whistle_")) return new Tool(props, Action.WHISTLE);
        if (id.startsWith("i_demonfish_raw")) return new Item(props.food(new FoodProperties.Builder().nutrition(2)
                .saturationMod(0.1F).effect(new MobEffectInstance(MobEffects.POISON, 100), 0.2F).build()));
        return switch (id) {
            case "i_star_compass" -> new Tool(props, Action.COMPASS);
            case "i_bandage" -> new Tool(props, Action.BANDAGE);
            case "i_unheard_bell" -> new Tool(props, Action.BELL);
            case "i_thousand_pins" -> new Tool(props, Action.PINS);
            case "i_crystal_shard" -> new Tool(props, Action.CHIME);
            case "i_curse_box" -> new Tool(props, Action.SHELTER);
            case "i_blaze_reap" -> new PickaxeItem(Tiers.DIAMOND, 3, -2.9F, props) {
                @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity user) {
                    target.setSecondsOnFire(4);
                    return super.hurtEnemy(stack, target, user);
                }
            };
            case "i_sparagmos" -> new SwordItem(Tiers.DIAMOND, 4, -2.2F, props);
            case "i_lantern" -> new PlacementItem(AbyssDevices.block("orth_street_lamp"), props);
            case "i_flag" -> new PlacementItem(AbyssDevices.block("camp_flag"), props);
            case "i_rope" -> new PlacementItem(AbyssDevices.block("ropeladder"), props);
            case "i_life_stone" -> new PlacementItem(AbyssDevices.block("lifesound_stone_block"), props);
            case "mat_ration" -> food(props, 6, 0.8F);
            case "mat_meat", "mat_squid_meat", "i_narehate_flesh" -> new Item(props.food(new FoodProperties.Builder()
                    .nutrition(3).saturationMod(0.24F).effect(new MobEffectInstance(MobEffects.HUNGER, 200), 0.35F).build()));
            case "i_shroom_cap" -> new Item(props.food(new FoodProperties.Builder().nutrition(2).saturationMod(0.25F)
                    .effect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220), 1).build()));
            default -> new Item(props);
        };
    }

    static Item food(Item.Properties props, int nutrition, float saturation) {
        return new Item(props.food(new FoodProperties.Builder().nutrition(nutrition).saturationMod(saturation).build()));
    }

    private static final class PlacementItem extends BlockItem {
        PlacementItem(net.minecraft.world.level.block.Block block, Item.Properties props) { super(block, props); }

        @Override public void registerBlocks(Map<net.minecraft.world.level.block.Block, Item> blockItems, Item item) {
            // The registered block item remains canonical for block drops and pick-block.
        }
    }

    private enum Action { WHISTLE, COMPASS, BANDAGE, BELL, PINS, CHIME, SHELTER }

    private static final class Tool extends Item {
        private final Action action;
        Tool(Properties props, Action action) { super(props); this.action = action; }

        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (action == Action.BANDAGE && player.getHealth() >= player.getMaxHealth()) return InteractionResultHolder.fail(stack);
            if (!level.isClientSide) perform(level, player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        private void perform(Level level, Player player, ItemStack stack) {
            switch (action) {
                case COMPASS -> player.displayClientMessage(bearing(player.blockPosition()), true);
                case WHISTLE -> {
                    float pitch = stack.is(item("i_whistle_bell")) ? 1.48F : 0.94F;
                    level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4F, pitch);
                    player.getCooldowns().addCooldown(this, 24);
                }
                case BANDAGE -> {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120));
                    spend(player, stack, 1);
                    player.getCooldowns().addCooldown(this, 160);
                }
                case BELL -> {
                    for (var mob : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(6))) {
                        if (mob.distanceToSqr(player) <= 36)
                            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 6));
                    }
                    player.getCooldowns().addCooldown(this, 1200);
                    level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.55F);
                }
                case PINS -> {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1));
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400));
                    player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 440, 1));
                    spend(player, stack, 1);
                    player.getCooldowns().addCooldown(this, 520);
                }
                case CHIME -> {
                    level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                            SoundSource.PLAYERS, 0.8F, 0.68F + level.random.nextFloat() * 0.70F);
                    player.getCooldowns().addCooldown(this, 8);
                }
                case SHELTER -> {
                    shelter(player, 260);
                    player.getCooldowns().addCooldown(this, 1200);
                    player.displayClientMessage(Component.translatable("message.stratacubes.sheltered"), true);
                }
            }
        }
    }

    private AbyssGear() {}
}
