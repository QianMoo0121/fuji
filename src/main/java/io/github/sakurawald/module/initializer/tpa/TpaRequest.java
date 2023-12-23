package io.github.sakurawald.module.initializer.tpa;

import io.github.sakurawald.config.Configs;
import io.github.sakurawald.module.ModuleManager;
import io.github.sakurawald.util.MessageUtil;
import lombok.Getter;
import lombok.ToString;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.server.level.ServerPlayer;

import java.util.Timer;
import java.util.TimerTask;

@ToString
public class TpaRequest {

    private static final String CIRCLE = "●";
    private static final String TICK = "[✔]";
    private static final String CROSS = "[❌]";
    private static final TpaModule module = ModuleManager.getInitializer(TpaModule.class);
    @Getter
    private final ServerPlayer sender;
    @Getter
    private final ServerPlayer receiver;
    @Getter
    private final boolean tpahere;
    private Timer timer;

    public TpaRequest(ServerPlayer sender, ServerPlayer receiver, boolean tpahere) {
        this.sender = sender;
        this.receiver = receiver;
        this.tpahere = tpahere;
    }

    boolean similarTo(TpaRequest other) {
        return (this.sender.equals(other.sender) && this.receiver.equals(other.receiver)) ||
                (this.sender.equals(other.receiver) && this.receiver.equals(other.sender));
    }

    public ServerPlayer getTeleportWho() {
        return tpahere ? getReceiver() : getSender();
    }

    public ServerPlayer getTeleportTo() {
        return tpahere ? getSender() : getReceiver();
    }

    public void startTimeout() {
        var that = this;
        timer = new Timer();
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        getSender().sendMessage(asSenderComponent$Cancelled());
                        getReceiver().sendMessage(asReceiverComponent$Cancelled());
                        // don't forget to remove this request
                        module.getRequests().remove(that);
                    }
                },
                Configs.configHandler.model().modules.tpa.timeout * 1000L
        );
    }

    public void cancelTimeout() {
        timer.cancel();
    }

    public Component asSenderComponent$Description() {
        return tpahere ? MessageUtil.ofComponent(getSender(), "tpa.others_to_you", receiver.getGameProfile().getName())
                : MessageUtil.ofComponent(getSender(), "tpa.you_to_others", receiver.getGameProfile().getName());
    }

    public Component asSenderComponent$Sent() {
        TextComponent cancelComponent = Component
                .text(CROSS).color(NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(MessageUtil.ofComponent(getSender(), "cancel")))
                .clickEvent(ClickEvent.runCommand("/tpacancel %s".formatted(getReceiver().getGameProfile().getName())));

        return asSenderComponent$Description()
                .appendSpace()
                .append(cancelComponent);
    }

    public Component asReceiverComponent$Description() {
        return tpahere ? MessageUtil.ofComponent(getReceiver(), "tpa.you_to_others", sender.getGameProfile().getName())
                : MessageUtil.ofComponent(getReceiver(), "tpa.others_to_you", sender.getGameProfile().getName());
    }

    public Component asReceiverComponent$Sent() {
        Component acceptComponent = Component.text(TICK).color(NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(MessageUtil.ofComponent(getReceiver(), "accept")))
                .clickEvent(ClickEvent.runCommand("/tpaaccept %s".formatted(sender.getGameProfile().getName())));
        Component denyComponent = Component.text(CROSS).color(NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(MessageUtil.ofComponent(getReceiver(), "deny")))
                .clickEvent(ClickEvent.runCommand("/tpadeny %s".formatted(sender.getGameProfile().getName())));
        return asReceiverComponent$Description()
                .appendSpace()
                .append(acceptComponent)
                .appendSpace()
                .append(denyComponent);
    }

    public Component asSenderComponent$Accepted() {
        return asSenderComponent$Description().appendSpace().append(Component.text(CIRCLE, NamedTextColor.GREEN));
    }

    public Component asReceiverComponent$Accepted() {
        return asReceiverComponent$Description().appendSpace().append(Component.text(CIRCLE, NamedTextColor.GREEN));
    }

    public Component asSenderComponent$Denied() {
        return asSenderComponent$Description().appendSpace().append(Component.text(CIRCLE, NamedTextColor.RED));
    }

    public Component asReceiverComponent$Denied() {
        return asReceiverComponent$Description().appendSpace().append(Component.text(CIRCLE, NamedTextColor.RED));
    }

    public Component asSenderComponent$Cancelled() {
        return asSenderComponent$Description().decoration(TextDecoration.STRIKETHROUGH, true);
    }

    public Component asReceiverComponent$Cancelled() {
        return asReceiverComponent$Description().decoration(TextDecoration.STRIKETHROUGH, true);
    }
}
