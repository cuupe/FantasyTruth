package dev.fantasytruth.mixin;

import dev.fantasytruth.atlas.AtlasPages;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.VanillaInventoryCodeHooks;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VanillaInventoryCodeHooks.class, remap = false)
abstract class ForgeInventoryMixin {
    @Inject(method = "getItemHandler(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/core/Direction;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void fantasytruth$findOwner(Level level, double x, double y, double z, Direction side,
                                              CallbackInfoReturnable<Optional<Pair<IItemHandler, Object>>> result) {
        if (!(level instanceof ServerLevel server)) return;
        BlockPos pos = BlockPos.containing(x, y, z);
        if (AtlasPages.ownsBlock(server, pos)) return;
        var target = AtlasPages.neighboringInventory(server, pos);
        result.setReturnValue(target == null ? Optional.empty() : VanillaInventoryCodeHooks.getItemHandler(target.level(),
                x, y + target.pos().getY() - pos.getY(), z, side));
    }
}
