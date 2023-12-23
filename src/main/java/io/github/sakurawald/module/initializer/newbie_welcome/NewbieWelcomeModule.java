package io.github.sakurawald.module.initializer.newbie_welcome;

import io.github.sakurawald.module.initializer.ModuleInitializer;
import io.github.sakurawald.module.initializer.newbie_welcome.random_teleport.RandomTeleport;
import io.github.sakurawald.util.MessageUtil;
import net.minecraft.server.level.ServerPlayer;


public class NewbieWelcomeModule extends ModuleInitializer {

    public void welcomeNewbiePlayer(ServerPlayer player) {
        /* welcome message */
        MessageUtil.sendBroadcast("newbie_welcome.welcome_message", player.getGameProfile().getName());

        /* random teleport */
        RandomTeleport.randomTeleport(player, player.serverLevel(), true);
    }

}
