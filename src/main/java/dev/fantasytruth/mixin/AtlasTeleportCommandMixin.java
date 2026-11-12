package dev.fantasytruth.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.fantasytruth.atlas.AtlasPages;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TeleportCommand.class)
abstract class AtlasTeleportCommandMixin {
    @ModifyVariable(method = {"m_139025_", "teleportToPos"}, at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static ServerLevel fantasytruth$destination(ServerLevel original, CommandSourceStack source,
                                                      Collection<?> targets, ServerLevel level, Coordinates position)
            throws CommandSyntaxException {
        return AtlasPages.teleportLevel(original, source, position);
    }

    @ModifyVariable(method = {"m_139025_", "teleportToPos"}, at = @At("STORE"), ordinal = 0, remap = false)
    private static Vec3 fantasytruth$localPosition(Vec3 raw, CommandSourceStack source, Collection<?> targets,
                                                  ServerLevel destination, Coordinates position) throws CommandSyntaxException {
        return AtlasPages.teleportLocalPosition(source.getLevel(), raw, position.isYRelative());
    }

    @ModifyVariable(method = {"m_139025_", "teleportToPos"}, at = @At(value = "INVOKE", target = "Ljava/util/Collection;size()I", ordinal = 0),
            ordinal = 0, remap = false)
    private static Vec3 fantasytruth$reportPosition(Vec3 local, CommandSourceStack source, Collection<?> targets,
                                                   ServerLevel destination) {
        return AtlasPages.teleportReportPosition(destination, local);
    }
}
