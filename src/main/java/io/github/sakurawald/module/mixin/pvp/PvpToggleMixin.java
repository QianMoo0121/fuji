package io.github.sakurawald.module.mixin.pvp;

import com.mojang.authlib.GameProfile;
import io.github.sakurawald.module.ModuleManager;
import io.github.sakurawald.module.initializer.pvp.PvpModule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static io.github.sakurawald.util.MessageUtil.sendMessage;

@Mixin(ServerPlayer.class)
public abstract class PvpToggleMixin extends Player {
    @Unique
    private static final PvpModule module = ModuleManager.getInitializer(PvpModule.class);

    public PvpToggleMixin(Level world, BlockPos pos, float yaw, GameProfile gameProfile) {
        super(world, pos, yaw, gameProfile);
    }

    @Inject(method = "canHarmPlayer", at = @At("HEAD"), cancellable = true)
    public void $canHarmPlayer(Player sourcePlayer, CallbackInfoReturnable<Boolean> cir) {
        if (this == sourcePlayer) return;

        ServerPlayer player = sourcePlayer.createCommandSourceStack().getPlayer();
        if (player == null) return;

        if (!module.contains(sourcePlayer.getGameProfile().getName())) {
            sendMessage(player, "pvp.check.off.me");
            cir.setReturnValue(false);
            return;
        }

        if (!module.contains(this.getGameProfile().getName())) {
            sendMessage(player, "pvp.check.off.others", this.getGameProfile().getName());
            cir.setReturnValue(false);
        }
    }
}
