package io.github.sakurawald.module.mixin.language;

import io.github.sakurawald.module.initializer.teleport_warmup.ServerPlayerAccessor;
import io.github.sakurawald.util.MessageUtil;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ServerPlayer.class)
@Slf4j
public abstract class ServerPlayerMixin implements ServerPlayerAccessor {

    @Inject(method = "updateOptions", at = @At("HEAD"))
    public void updateOptions(ServerboundClientInformationPacket serverboundClientInformationPacket, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        MessageUtil.getPlayer2lang().put(player.getGameProfile().getName(), serverboundClientInformationPacket.language());
    }
}