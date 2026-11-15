package dev.fantasytruth.abyss;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ForestExpedition {
    private static final BlockPos UPPER_CONTROL = new BlockPos(127, ForestTerrain.CAMP_Y + 1, 143);
    private static final BlockPos LOWER_CONTROL = new BlockPos(127, ForestTerrain.CAMP_LANDING + 1, 143);
    private static final Map<ServerPlayer, Ascent> ASCENTS = new WeakHashMap<>();

    private record Ascent(long time, double low, double previous) {}

    static void tick(ServerPlayer player) {
        if (player.isCreative()) return;
        long time = player.serverLevel().getGameTime();
        double y = player.getY();
        var last = ASCENTS.get(player);
        if (last == null || time - last.time != 1 || Math.abs(y - last.previous) > 8) {
            ASCENTS.put(player, new Ascent(time, y, y));
            return;
        }
        double low = Math.min(last.low, y);
        if (AbyssGear.sheltered(player)) {
            ASCENTS.put(player, new Ascent(time, y, y));
            return;
        }
        boolean weakField = weakField(player.blockPosition());
        if (y - low >= (weakField ? 30 : 12)) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, weakField ? 100 : 170));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, weakField ? 60 : 115, weakField ? 0 : 1));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, weakField ? 55 : 95));
            player.displayClientMessage(Component.translatable("message.stratacubes.forest.ascent"), true);
            low = y;
        }
        ASCENTS.put(player, new Ascent(time, low, y));
    }

    static boolean weakField(BlockPos pos) {
        return pos.getY() < ForestTerrain.INVERTED
                && (!AbyssLayer.passage(pos.getX(), pos.getZ(), 126) || pos.closerThan(LOWER_CONTROL, 54));
    }

    static void landmarks(CommandSourceStack source, ForestTerrain terrain) {
        int nest = terrain.column(ForestLandmarks.NEST_X, ForestLandmarks.NEST_Z).shelfTop() + 8;
        mark(source, "weeper_nest", ForestLandmarks.NEST_X, nest, ForestLandmarks.NEST_Z + 9);
        mark(source, "hells_crossing", ForestLandmarks.CROSS_X + 5, ForestLandmarks.CROSS_Y + 12, ForestLandmarks.CROSS_Z + 8);
        int pond = terrain.column(ForestLandmarks.POND_X, ForestLandmarks.POND_Z).floor() + 4;
        mark(source, "terminal_pond", ForestLandmarks.POND_X + 20, pond, ForestLandmarks.POND_Z);
        var spring = terrain.spring;
        mark(source, "forest_spring", spring.x(), spring.water() + 2, spring.z());
        mark(source, "heavens_waterfall", ForestLandmarks.FALL_X, terrain.exitY() + 6, ForestLandmarks.FALL_Z);
        int digX = ForestLandmarks.DIG_X + 1, digZ = ForestLandmarks.DIG_Z + 17;
        mark(source, "joint_dig", digX, terrain.column(digX, digZ).floor() + 1, digZ);
    }

    private static void mark(CommandSourceStack source, String id, int x, int y, int z) {
        source.sendSuccess(() -> Component.translatable("commands.stratacubes.abyss." + id, x, y, z), false);
    }

    static void placeCampStations(ForestGrowth.Writer p) {
        var state = Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR);
        for (BlockPos station : new BlockPos[]{UPPER_CONTROL, LOWER_CONTROL}) {
            p.set(station.getX(), station.getY(), station.getZ(), state);
            p.set(station.getX() - 1, station.getY() - 1, station.getZ() + 1, Blocks.CUT_COPPER.defaultBlockState());
        }
        p.barrel(ForestTerrain.CAMP_X - 10, ForestTerrain.CAMP_Y + 1, ForestTerrain.CAMP_Z - 8, "forest_camp");
        p.journal(ForestTerrain.CAMP_X, ForestTerrain.CAMP_Y + 1, ForestTerrain.CAMP_Z - 9);
        p.set(ForestTerrain.CAMP_X + 5, ForestTerrain.CAMP_Y + 1, ForestTerrain.CAMP_Z - 11,
                ForestGrowth.Palette.block("curse_box_block"));
    }

    @SubscribeEvent
    public static void useCampLift(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        if (AbyssLayer.of(level.dimension()) != AbyssLayer.FOREST || !level.getBlockState(event.getPos()).is(Blocks.STONE_BUTTON)) return;
        var station = event.getPos();
        BlockPos other;
        if (station.equals(LOWER_CONTROL)) other = UPPER_CONTROL;
        else if (station.equals(UPPER_CONTROL)) other = LOWER_CONTROL;
        else return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isPassenger() || player.isSpectator()) return;
        BlockPos deck = other.offset(-1, 0, 1);
        if (!level.getBlockState(other).is(Blocks.STONE_BUTTON) || !level.getBlockState(deck.below()).blocksMotion()
                || level.getBlockState(deck).blocksMotion() || level.getBlockState(deck.above()).blocksMotion()) {
            player.displayClientMessage(Component.translatable("message.stratacubes.forest.lift_blocked"), true);
            return;
        }
        player.teleportTo(player.serverLevel(), deck.getX() + 0.5, deck.getY(), deck.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.fallDistance = 0;
        level.playSound(null, other, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.9F, 0.8F);
        player.displayClientMessage(Component.translatable("message.stratacubes.forest.lift"), true);
    }

    private ForestExpedition() {}
}
