package io.github.sakurawald.module.initializer.home;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.sakurawald.config.Configs;
import io.github.sakurawald.config.handler.ConfigHandler;
import io.github.sakurawald.config.handler.ObjectConfigHandler;
import io.github.sakurawald.config.model.HomeModel;
import io.github.sakurawald.module.initializer.ModuleInitializer;
import io.github.sakurawald.module.initializer.teleport_warmup.Position;
import io.github.sakurawald.util.CommandUtil;
import io.github.sakurawald.util.MessageUtil;
import io.github.sakurawald.util.ScheduleUtil;
import lombok.Getter;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@SuppressWarnings("LombokGetterMayBeUsed")

public class HomeModule extends ModuleInitializer {


    @Getter
    private final ConfigHandler<HomeModel> data = new ObjectConfigHandler<>("home.json", HomeModel.class);

    public void onInitialize() {
        data.loadFromDisk();
        data.autoSave(ScheduleUtil.CRON_EVERY_MINUTE);
    }

    @Override
    public void onReload() {
        data.loadFromDisk();
        data.autoSave(ScheduleUtil.CRON_EVERY_MINUTE);
    }

    @SuppressWarnings({"UnusedReturnValue", "unused"})
    @Override
    public void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(
                Commands.literal("home")
                        .then(Commands.literal("set").then(myHomesArgument().executes(ctx -> $set(ctx, false)).then(literal("override").executes(ctx -> $set(ctx, true)))))
                        .then(Commands.literal("tp").then(myHomesArgument().executes(this::$tp)))
                        .then(Commands.literal("unset").then(myHomesArgument().executes(this::$unset)))
                        .then(Commands.literal("list").executes(this::$list))
        );
    }

    private Map<String, Position> getHomes(ServerPlayer player) {
        String playerName = player.getGameProfile().getName();
        Map<String, Map<String, Position>> homes = data.model().homes;
        homes.computeIfAbsent(playerName, k -> new HashMap<>());
        return homes.get(playerName);
    }

    private int $tp(CommandContext<CommandSourceStack> ctx) {
        return CommandUtil.playerOnlyCommand(ctx, player -> {
            Map<String, Position> name2position = getHomes(player);
            String homeName = StringArgumentType.getString(ctx, "name");
            if (!name2position.containsKey(homeName)) {
                MessageUtil.sendMessage(player, "home.no_found", homeName);
                return 0;
            }

            Position position = name2position.get(homeName);
            position.teleport(player);
            return Command.SINGLE_SUCCESS;
        });
    }

    private int $unset(CommandContext<CommandSourceStack> ctx) {
        return CommandUtil.playerOnlyCommand(ctx, player -> {
            Map<String, Position> name2position = getHomes(player);
            String homeName = StringArgumentType.getString(ctx, "name");
            if (!name2position.containsKey(homeName)) {
                MessageUtil.sendMessage(player, "home.no_found", homeName);
                return 0;
            }

            name2position.remove(homeName);
            MessageUtil.sendMessage(player, "home.unset.success", homeName);
            return Command.SINGLE_SUCCESS;
        });
    }

    private int $set(CommandContext<CommandSourceStack> ctx, boolean override) {
        return CommandUtil.playerOnlyCommand(ctx, player -> {
            Map<String, Position> name2position = getHomes(player);
            String homeName = StringArgumentType.getString(ctx, "name");
            if (name2position.containsKey(homeName)) {
                if (!override) {
                    MessageUtil.sendMessage(player, "home.set.fail.need_override", homeName);
                    return Command.SINGLE_SUCCESS;
                }
            } else if (name2position.size() >= Configs.configHandler.model().modules.home.max_homes) {
                MessageUtil.sendMessage(player, "home.set.fail.limit");
                return Command.SINGLE_SUCCESS;
            }

            name2position.put(homeName, Position.of(player));
            MessageUtil.sendMessage(player, "home.set.success", homeName);
            return Command.SINGLE_SUCCESS;
        });
    }

    private int $list(CommandContext<CommandSourceStack> ctx) {
        return CommandUtil.playerOnlyCommand(ctx, player -> {
            MessageUtil.sendMessage(player, "home.list", getHomes(player).keySet());
            return Command.SINGLE_SUCCESS;
        });
    }

    public RequiredArgumentBuilder<CommandSourceStack, String> myHomesArgument() {
        return argument("name", StringArgumentType.string())
                .suggests((context, builder) -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player == null) return builder.buildFuture();

                            Map<String, Position> name2position = getHomes(player);
                            name2position.keySet().forEach(builder::suggest);
                            return builder.buildFuture();
                        }
                );
    }
}
