package io.github.sakurawald.module.initializer.gameplay.carpet.fake_player_manager;

import com.mojang.brigadier.context.CommandContext;
import io.github.sakurawald.core.annotation.Document;
import io.github.sakurawald.core.auxiliary.ChronosUtil;
import io.github.sakurawald.core.auxiliary.minecraft.CommandHelper;
import io.github.sakurawald.core.auxiliary.minecraft.ServerHelper;
import io.github.sakurawald.core.auxiliary.minecraft.TextHelper;
import io.github.sakurawald.core.command.annotation.CommandNode;
import io.github.sakurawald.core.command.annotation.CommandSource;
import io.github.sakurawald.core.config.handler.abst.BaseConfigurationHandler;
import io.github.sakurawald.core.config.handler.impl.ObjectConfigurationHandler;
import io.github.sakurawald.core.event.impl.ServerLifecycleEvents;
import io.github.sakurawald.module.initializer.ModuleInitializer;
import io.github.sakurawald.module.initializer.gameplay.carpet.fake_player_manager.config.model.FakePlayerManagerConfigModel;
import io.github.sakurawald.module.initializer.gameplay.carpet.fake_player_manager.job.ManageFakePlayersJob;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class FakePlayerManagerInitializer extends ModuleInitializer {

    public static final BaseConfigurationHandler<FakePlayerManagerConfigModel> config = new ObjectConfigurationHandler<>(BaseConfigurationHandler.CONFIG_JSON, FakePlayerManagerConfigModel.class);

    private static final Map<String, List<String>> player2fakePlayers = new HashMap<>();
    private static final Map<String, Long> player2expiration = new HashMap<>();

    public static void checkCapsLimit() {
        /* invalid */
        invalidFakePlayers();

        /* update value */
        int capsLimit = computeFakePlayerCapsLimit();
        long currentTimeMs = System.currentTimeMillis();

        player2fakePlayers.entrySet()
            .forEach(e -> {
                String ownerPlayerName = e.getKey();

                /* make new value */
                long expiration = player2expiration.computeIfAbsent(ownerPlayerName, k -> 0L);
                final Integer[] allowFakePlayers = {0};
                List<String> newValue = e.getValue()
                    .stream()
                    .filter(fakePlayerName -> {
                        ServerPlayerEntity fakePlayer = ServerHelper.getPlayerByName(fakePlayerName);
                        if (fakePlayer == null) return false;

                        /* check: expiration */
                        if (currentTimeMs >= expiration) {
                            /* auto-renew the fake players if the owner player is online */
                            ServerPlayerEntity owner = ServerHelper.getPlayerByName(ownerPlayerName);
                            if (owner != null) {
                                renewMyFakePlayers(owner);
                                return true;
                            }

                            /* kill all fake players due to expiration */
                            fakePlayer.kill();
                            TextHelper.sendBroadcastByKey("fake_player_manager.kick_for_expiration", fakePlayer.getGameProfile().getName(), ownerPlayerName);
                            return false;
                        }

                        /* check: caps */
                        if (allowFakePlayers[0] < capsLimit) {
                            allowFakePlayers[0]++;
                            return true;
                        } else {
                            fakePlayer.kill();
                            TextHelper.sendBroadcastByKey("fake_player_manager.kick_for_amount", fakePlayer.getGameProfile().getName(), ownerPlayerName);
                            return false;
                        }

                    }).collect(Collectors.toList());

                /* set new value */
                e.setValue(newValue);
            });

    }

    @CommandNode("player renew")
    @Document("Renew the expiration time of all fake-players spawned by you.")
    private static int $renew(@CommandSource ServerPlayerEntity player) {
        renewMyFakePlayers(player);
        return CommandHelper.Return.SUCCESS;
    }

    @CommandNode("player who")
    @Document("List all fake-players and its owner.")
    private static int $who(@CommandSource CommandContext<ServerCommandSource> context) {
        /* make table */
        StringBuilder body = new StringBuilder();
        player2fakePlayers.forEach((k, v) -> body.append("%s -> %s".formatted(k, v))
            .append("\n"));

        /* make text */
        ServerCommandSource source = context.getSource();
        source.sendMessage(
            TextHelper.getTextByKey(source, "fake_player_manager.who.header")
                .copy()
                .append(Text.literal(body.toString())));
        return CommandHelper.Return.SUCCESS;
    }

    public static void renewMyFakePlayers(@NotNull ServerPlayerEntity player) {
        int renewDuration = config.model().renew_duration_ms;
        long newExpiration = System.currentTimeMillis() + renewDuration;
        player2expiration.put(player.getGameProfile().getName(), newExpiration);

        TextHelper.sendMessageByKey(player, "fake_player_manager.renew.success", ChronosUtil.toStandardDateFormat(newExpiration));
    }

    public static void invalidFakePlayers() {
        player2fakePlayers.values()
            .forEach(value -> value.removeIf(fakePlayerName -> {
                ServerPlayerEntity fakePlayer = ServerHelper.getPlayerByName(fakePlayerName);
                return fakePlayer == null || fakePlayer.isRemoved();
            }));
    }

    public static boolean canSpawnFakePlayer(@NotNull ServerPlayerEntity player) {
        /* check */
        int capsLimit = computeFakePlayerCapsLimit();
        int currentQuantity = player2fakePlayers.computeIfAbsent(player.getGameProfile().getName(), k -> new ArrayList<>()).size();
        return currentQuantity < capsLimit;
    }

    public static void addMyFakePlayer(@NotNull ServerPlayerEntity player, @NotNull String fakePlayer) {
        player2fakePlayers.computeIfAbsent(player.getGameProfile().getName(), k -> new ArrayList<>())
            .add(fakePlayer);
    }

    public static boolean canManipulateFakePlayer(@NotNull CommandContext<ServerCommandSource> ctx, String fakePlayer) {
        // IMPORTANT: disable /player ... shadow command for online-player
        if (ctx.getNodes().get(2).getNode().getName().equals("shadow")) return false;

        // bypass: console
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return true;

        // bypass: op
        if (ctx.getSource().hasPermissionLevel(4)) return true;

        // check
        List<String> myFakePlayers = player2fakePlayers.computeIfAbsent(player.getGameProfile().getName(), k -> new ArrayList<>());
        return myFakePlayers.contains(fakePlayer);
    }

    public static boolean isMyFakePlayer(@NotNull ServerPlayerEntity player, @NotNull ServerPlayerEntity fakePlayer) {
        return player2fakePlayers.computeIfAbsent(player.getGameProfile().getName(), k -> new ArrayList<>())
            .contains(fakePlayer.getGameProfile().getName());
    }

    @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
    public static int computeFakePlayerCapsLimit() {
        int currentDays = LocalDate.now().getDayOfWeek().getValue();
        LocalTime currentTime = LocalTime.now();
        int currentMinutes = currentTime.getHour() * 60 + currentTime.getMinute();

        Optional<List<Integer>> first = config.model().caps_limit_rule
            .stream()
            .filter(it -> currentDays >= it.get(0) && currentMinutes >= it.get(1))
            .findFirst();
        if (first.isPresent()) {
            return first.get().get(2);
        }

        return -1;
    }

    @Override
    protected void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> new ManageFakePlayersJob().schedule());
    }

}
