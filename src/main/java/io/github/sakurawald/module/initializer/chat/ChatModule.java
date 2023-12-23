package io.github.sakurawald.module.initializer.chat;

import com.google.common.collect.EvictingQueue;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.sakurawald.Fuji;
import io.github.sakurawald.config.Configs;
import io.github.sakurawald.module.ModuleManager;
import io.github.sakurawald.module.initializer.ModuleInitializer;
import io.github.sakurawald.module.initializer.chat.display.DisplayHelper;
import io.github.sakurawald.module.initializer.chat.mention.MentionPlayersJob;
import io.github.sakurawald.module.initializer.main_stats.MainStats;
import io.github.sakurawald.module.initializer.main_stats.MainStatsModule;
import io.github.sakurawald.util.CommandUtil;
import io.github.sakurawald.util.MessageUtil;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Queue;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ChatModule extends ModuleInitializer {

    private final MiniMessage miniMessage = MiniMessage.builder().build();
    private final MainStatsModule mainStatsModule = ModuleManager.getInitializer(MainStatsModule.class);
    @Getter
    private Queue<Component> chatHistory;

    @Override
    public void onInitialize() {
        Configs.chatHandler.loadFromDisk();

        chatHistory = EvictingQueue.create(Configs.configHandler.model().modules.chat.history.cache_size);
    }


    @Override
    public void onReload() {
        Configs.chatHandler.loadFromDisk();

        EvictingQueue<Component> newQueue = EvictingQueue.create(Configs.configHandler.model().modules.chat.history.cache_size);
        newQueue.addAll(chatHistory);
        chatHistory.clear();
        chatHistory = newQueue;
    }

    @SuppressWarnings("unused")
    @Override
    public void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(
                Commands.literal("chat")
                        .then(literal("format")
                                .then(argument("format", StringArgumentType.greedyString())
                                        .executes(this::$format)
                                )));
    }

    private int $format(CommandContext<CommandSourceStack> ctx) {
        return CommandUtil.playerOnlyCommand(ctx, player -> {
            String name = player.getGameProfile().getName();
            String format = StringArgumentType.getString(ctx, "format");
            Configs.chatHandler.model().format.player2format.put(name, format);
            Configs.chatHandler.saveToDisk();
            return Command.SINGLE_SUCCESS;
        });
    }


    private Component resolvePositionTag(ServerPlayer player, Component component) {
        Component replacement = Component.text("%s (%d %d %d) %s".formatted(player.serverLevel().dimension().location(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ(), player.chunkPosition().toString())).color(NamedTextColor.GOLD);
        return component.replaceText(TextReplacementConfig.builder().match("(?<=^|\\s)pos(?=\\s|$)").replacement(replacement).build());
    }

    private Component resolveItemTag(ServerPlayer player, Component component) {
        String displayUUID = DisplayHelper.createItemDisplay(player);
        Component replacement =
                player.getMainHandItem().getDisplayName().asComponent()
                        .hoverEvent(MessageUtil.ofComponent(player, "display.click.prompt"))
                        .clickEvent(displayCallback(displayUUID));
        return component.replaceText(TextReplacementConfig.builder().match("(?<=^|\\s)item(?=\\s|$)").replacement(replacement).build());
    }

    private Component resolveInvTag(ServerPlayer player, Component component) {
        String displayUUID = DisplayHelper.createInventoryDisplay(player);
        Component replacement =
                MessageUtil.ofComponent(player, "display.inventory.text")
                        .hoverEvent(MessageUtil.ofComponent(player, "display.click.prompt"))
                        .clickEvent(displayCallback(displayUUID));
        return component.replaceText(TextReplacementConfig.builder().match("(?<=^|\\s)inv(?=\\s|$)").replacement(replacement).build());
    }

    private Component resolveEnderTag(ServerPlayer player, Component component) {
        String displayUUID = DisplayHelper.createEnderChestDisplay(player);
        Component replacement =
                MessageUtil.ofComponent(player, "display.ender_chest.text")
                        .hoverEvent(MessageUtil.ofComponent(player, "display.click.prompt"))
                        .clickEvent(displayCallback(displayUUID));
        return component.replaceText(TextReplacementConfig.builder().match("(?<=^|\\s)ender(?=\\s|$)").replacement(replacement).build());
    }

    @NotNull
    private ClickEvent displayCallback(String displayUUID) {
        return ClickEvent.callback(audience -> {
            if (audience instanceof CommandSourceStack css && css.getPlayer() != null) {
                DisplayHelper.viewDisplay(css.getPlayer(), displayUUID);
            }
        }, ClickCallback.Options.builder().lifetime(Duration.of(Configs.configHandler.model().modules.chat.display.expiration_duration_s, ChronoUnit.SECONDS))
                .uses(Integer.MAX_VALUE).build());
    }

    @SuppressWarnings("unused")
    private String resolveMentionTag(ServerPlayer player, String str) {
        /* resolve player tag */
        ArrayList<ServerPlayer> mentionedPlayers = new ArrayList<>();

        String[] playerNames = Fuji.SERVER.getPlayerNames();
        // fix: mention the longest name first
        Arrays.sort(playerNames, Comparator.comparingInt(String::length).reversed());

        for (String playerName : playerNames) {
            // here we must continue so that mentionPlayers will not be added
            if (!str.contains(playerName)) continue;
            str = str.replace(playerName, "<aqua>%s</aqua>".formatted(playerName));
            mentionedPlayers.add(Fuji.SERVER.getPlayerList().getPlayerByName(playerName));
        }

        /* run mention player task */
        if (!mentionedPlayers.isEmpty()) {
            MentionPlayersJob.scheduleJob(mentionedPlayers);
        }
        return str;
    }

    public void broadcastChatMessage(ServerPlayer player, String message) {
        /* resolve format */
        message = Configs.chatHandler.model().format.player2format.getOrDefault(player.getGameProfile().getName(), message)
                .replace("%message%", message);
        message = resolveMentionTag(player, message);
        String format = Configs.configHandler.model().modules.chat.format;
        format = format.replace("%message%", message);
        format = format.replace("%player%", player.getGameProfile().getName());

        /* resolve stats */
        if (mainStatsModule != null) {
            MainStats stats = MainStats.uuid2stats.getOrDefault(player.getUUID().toString(), new MainStats());
            format = stats.update(player).resolve(Fuji.SERVER, format);
        }

        /* resolve tags */
        Component component = miniMessage.deserialize(format, Formatter.date("date", LocalDateTime.now(ZoneId.systemDefault()))).asComponent();
        component = resolveItemTag(player, component);
        component = resolveInvTag(player, component);
        component = resolveEnderTag(player, component);
        component = resolvePositionTag(player, component);
        chatHistory.add(component);
        // info so that it can be seen in the console
        Fuji.LOGGER.info(PlainTextComponentSerializer.plainText().serialize(component));
        for (ServerPlayer receiver : Fuji.SERVER.getPlayerList().getPlayers()) {
            receiver.sendMessage(component);
        }
    }

}
