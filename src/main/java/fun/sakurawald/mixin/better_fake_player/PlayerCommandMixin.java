package fun.sakurawald.mixin.better_fake_player;

import carpet.commands.PlayerCommand;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fun.sakurawald.ModMain;
import fun.sakurawald.module.better_fake_player.BetterFakePlayerModule;
import fun.sakurawald.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("DataFlowIssue")
@Mixin(PlayerCommand.class)
public abstract class PlayerCommandMixin {

    @Inject(method = "spawn", at = @At("HEAD"), remap = false, cancellable = true)
    private static void $spawn_head(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return;

        if (!BetterFakePlayerModule.canSpawnFakePlayer(player)) {
            MessageUtil.message(player, "You have reach current fake-player limit.", false);
            cir.setReturnValue(0);
        }

        /* fix fake-player auth network laggy */
        String fakePlayerName = StringArgumentType.getString(context, "player");
        ModMain.SERVER.getProfileCache().add(BetterFakePlayerModule.createOfflineGameProfile(fakePlayerName));
    }

    @Inject(method = "spawn", at = @At("TAIL"), remap = false)
    private static void $spawn_tail(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
        ServerPlayer player = context.getSource().getPlayer();
        String spawnPlayerName = StringArgumentType.getString(context, "player");
        BetterFakePlayerModule.addFakePlayer(player, spawnPlayerName);
    }

    @Inject(method = "cantManipulate", at = @At("HEAD"), remap = false, cancellable = true)
    private static void $cantManipulate(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer sourcePlayer = context.getSource().getPlayer();
        String targetPlayerName = StringArgumentType.getString(context, "player");
        if (!BetterFakePlayerModule.canManipulateFakePlayer(sourcePlayer, targetPlayerName)) {
            MessageUtil.message(sourcePlayer, "You can't manipulate this player", false);
            cir.setReturnValue(true);
        }
    }
}
