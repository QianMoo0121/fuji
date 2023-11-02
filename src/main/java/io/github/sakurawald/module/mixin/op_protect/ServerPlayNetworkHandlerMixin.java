package io.github.sakurawald.module.mixin.op_protect;


import io.github.sakurawald.Fuji;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ServerGamePacketListenerImpl.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;onDisconnect(Lnet/minecraft/network/chat/Component;)V"), method = "onDisconnect")
    private void $disconnect(Component reason, CallbackInfo info) {
        if (Fuji.SERVER.getPlayerList().isOp(player.getGameProfile())) {
            Fuji.LOGGER.info("op protect -> deop " + player.getGameProfile().getName());
            Fuji.SERVER.getPlayerList().deop(player.getGameProfile());
        }
    }
}
