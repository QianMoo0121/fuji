package io.github.sakurawald.module.initializer.afk;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.sakurawald.Fuji;
import io.github.sakurawald.config.Configs;
import io.github.sakurawald.module.initializer.ModuleInitializer;
import io.github.sakurawald.util.CommandUtil;
import io.github.sakurawald.util.MessageUtil;
import io.github.sakurawald.util.ScheduleUtil;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;


public class AfkModule extends ModuleInitializer {

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> updateJobs());
    }

    @Override
    public void onReload() {
        updateJobs();
    }

    public void updateJobs() {
        ScheduleUtil.removeJobs(AfkCheckerJob.class.getName());
        ScheduleUtil.addJob(AfkCheckerJob.class, null, null, Configs.configHandler.model().modules.afk.afk_checker.cron, new JobDataMap());
    }

    @Override
    public void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("afk").executes(this::$afk));
    }

    @SuppressWarnings("SameReturnValue")
    private int $afk(CommandContext<CommandSourceStack> ctx) {
        return CommandUtil.playerOnlyCommand(ctx, (player -> {
            // note: issue command will update lastLastActionTime, so it's impossible to use /afk to disable afk
            ((ServerPlayerAccessor_afk) player).fuji$setAfk(true);
            MessageUtil.sendMessage(player, "afk.on");
            return Command.SINGLE_SUCCESS;
        }));
    }

    public static class AfkCheckerJob implements Job {

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            for (ServerPlayer player : Fuji.SERVER.getPlayerList().getPlayers()) {
                ServerPlayerAccessor_afk afk_player = (ServerPlayerAccessor_afk) player;

                // get last action time
                long lastActionTime = player.getLastActionTime();
                long lastLastActionTime = afk_player.fuji$getLastLastActionTime();
                afk_player.fuji$setLastLastActionTime(lastActionTime);

                // diff last action time
                /* note:
                when a player joins the server,
                we'll set lastLastActionTime's initial value to Player#getLastActionTime(),
                but there are a little difference even if you call Player#getLastActionTime() again
                 */
                if (lastActionTime - lastLastActionTime <= 3000) {
                    if (afk_player.fuji$isAfk()) continue;

                    afk_player.fuji$setAfk(true);
                    if (Configs.configHandler.model().modules.afk.afk_checker.kick_player) {
                        player.connection.disconnect(MessageUtil.ofVomponent(player, "afk.kick"));
                    }
                }
            }
        }
    }
}
