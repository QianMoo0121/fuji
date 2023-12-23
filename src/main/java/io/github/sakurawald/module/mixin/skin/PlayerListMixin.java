package io.github.sakurawald.module.mixin.skin;

import io.github.sakurawald.config.Configs;
import io.github.sakurawald.module.initializer.skin.SkinRestorer;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

@Mixin(PlayerList.class)
@Slf4j
public abstract class PlayerListMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    public abstract List<ServerPlayer> getPlayers();

    @Inject(method = "remove", at = @At("TAIL"))
    private void remove(ServerPlayer player, CallbackInfo ci) {
        SkinRestorer.getSkinStorage().removeSkin(player.getUUID());
    }

    @Inject(method = "removeAll", at = @At("HEAD"))
    private void disconnectAllPlayers(CallbackInfo ci) {
        getPlayers().forEach(player -> SkinRestorer.getSkinStorage().removeSkin(player.getUUID()));
    }

    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void onPlayerConnected(Connection connection, ServerPlayer player, CallbackInfo ci) {
        // if the player isn't a server player entity, it must be someone's fake player
        if (player.getClass() != ServerPlayer.class
                && Configs.configHandler.model().modules.better_fake_player.use_local_random_skins_for_fake_player) {
            SkinRestorer.setSkinAsync(server, Collections.singleton(player.getGameProfile()), () -> SkinRestorer.getSkinStorage().getRandomSkin(player.getUUID()));
        }
    }
}