package io.github.sakurawald.module.initializer.scheduler;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sakurawald.Fuji;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Random;


public class SpecializedCommand {

    // TODO: a language parser is needed here (supports some expressions solver)

    private static final String RANDOM_PLAYER = "!random_player!";
    private static final String ALL_PLAYER = "!all_player!";

    public static void runSpecializedCommands(MinecraftServer server, List<String> commands) {

        /* context */
        String randomPlayer = null;
        String[] onlinePlayers = server.getPlayerNames();

        /* resolve */
        for (String command : commands) {
            /* resolve random player */
            if (command.contains(RANDOM_PLAYER)) {
                if (randomPlayer == null) {
                    randomPlayer = onlinePlayers[new Random().nextInt(onlinePlayers.length)];
                }
                command = command.replace(RANDOM_PLAYER, randomPlayer);
            }

            /* resolve all players */
            if (command.contains(ALL_PLAYER)) {
                for (String onlinePlayer : onlinePlayers) {
                    executeCommand(server, command.replace(ALL_PLAYER, onlinePlayer));
                }
            } else {
                executeCommand(server, command);
            }
        }
    }

    public static void executeCommand(MinecraftServer server, String command) {
        try {
            server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
        } catch (CommandSyntaxException e) {
            Fuji.LOGGER.error(e.toString());
        }
    }

    public static void executeCommands(ServerPlayer player, List<String> commands) {
        commands.forEach(command -> executeCommand(player, command));
    }

    public static void executeCommand(ServerPlayer player, String command) {
        try {
            Fuji.SERVER.getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
        } catch (CommandSyntaxException e) {
            player.sendMessage(Component.text(e.getMessage()).color(NamedTextColor.RED));
        }
    }

}
