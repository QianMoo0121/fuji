package io.github.sakurawald.module.initializer.deathlog;

import io.github.sakurawald.core.annotation.Document;
import io.github.sakurawald.core.auxiliary.ReflectionUtil;
import io.github.sakurawald.core.auxiliary.minecraft.CommandHelper;
import io.github.sakurawald.core.auxiliary.minecraft.NbtHelper;
import io.github.sakurawald.core.auxiliary.minecraft.TextHelper;
import io.github.sakurawald.core.command.annotation.CommandNode;
import io.github.sakurawald.core.command.annotation.CommandRequirement;
import io.github.sakurawald.core.command.annotation.CommandSource;
import io.github.sakurawald.core.command.argument.wrapper.impl.OfflinePlayerName;
import io.github.sakurawald.core.command.exception.AbortCommandExecutionException;
import io.github.sakurawald.module.initializer.ModuleInitializer;
import lombok.SneakyThrows;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@CommandNode("deathlog")
@CommandRequirement(level = 4)
public class DeathLogInitializer extends ModuleInitializer {

    private static final Path DEATH_DATA_DIR_PATH = ReflectionUtil.computeModuleConfigPath(DeathLogInitializer.class).resolve("death-data");

    /* schema keys */
    private static final String DEATHS = "Deaths";
    private static final String TIME = "time";
    private static final String REASON = "reason";
    private static final String DIMENSION = "dimension";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String REMARK = "remark";
    private static final String ARMOR = "armor";
    private static final String OFFHAND = "offhand";
    private static final String ITEM = "item";
    private static final String SCORE = "score";
    private static final String XP_LEVEL = "xp_level";
    private static final String XP_PROGRESS = "xp_progress";
    private static final String INVENTORY = "inventory";

    @CommandNode("restore")
    @Document("Restore a deathlog.")
    private static int restore(@CommandSource ServerCommandSource source
        , @Document("Where the deathlog from?") String from
        , @Document("The index to select a deathlog.") int index
        , @Document("Where the deathlog is restored to?") ServerPlayerEntity to) {
        /* read from file */
        NbtHelper.withNbtFile(computePath(from), root -> {
            ensureDeathlogNotEmpty(source, root);

            NbtList deathsNode = NbtHelper.withNbtElement(root, DEATHS, new NbtList());

            if (index >= deathsNode.size()) {
                TextHelper.sendMessageByKey(source, "deathlog.index.not_found", index);
                throw new AbortCommandExecutionException();
            }

            // check the player's inventory for safety
            if (!to.getInventory().isEmpty()) {
                TextHelper.sendMessageByKey(source, "deathlog.restore.target_player.inventory_not_empty", to.getGameProfile().getName());
                throw new AbortCommandExecutionException();
            }

            /* restore inventory */
            NbtCompound inventoryNode = deathsNode.getCompound(index).getCompound(INVENTORY);
            List<ItemStack> item = NbtHelper.readSlotsNode((NbtList) inventoryNode.get(ITEM));
            for (int i = 0; i < item.size(); i++) {
                to.getInventory().main.set(i, item.get(i));
            }
            List<ItemStack> armor = NbtHelper.readSlotsNode((NbtList) inventoryNode.get(ARMOR));
            for (int i = 0; i < armor.size(); i++) {
                to.getInventory().armor.set(i, armor.get(i));
            }
            List<ItemStack> offhand = NbtHelper.readSlotsNode((NbtList) inventoryNode.get(OFFHAND));
            for (int i = 0; i < offhand.size(); i++) {
                to.getInventory().offHand.set(i, offhand.get(i));
            }
            to.setScore(inventoryNode.getInt(SCORE));
            to.experienceLevel = inventoryNode.getInt(XP_LEVEL);
            to.experienceProgress = inventoryNode.getFloat(XP_PROGRESS);

            TextHelper.sendMessageByKey(source, "deathlog.restore.success", from, index, to.getGameProfile().getName());
        });

        return CommandHelper.Return.SUCCESS;
    }

    private static @NotNull Path computePath(String playerName) {
        String fileName = Uuids.getOfflinePlayerUuid(playerName) + ".dat";
        return DEATH_DATA_DIR_PATH.resolve(fileName);
    }

    private static void ensureDeathlogNotEmpty(ServerCommandSource source, NbtCompound root) {
        if (root == null || root.isEmpty()) {
            TextHelper.sendMessageByKey(source, "deathlog.empty");
            throw new AbortCommandExecutionException();
        }
    }

    @CommandNode("view")
    @Document("List all deathlog of a player.")
    private static int view(@CommandSource ServerPlayerEntity player, OfflinePlayerName from) {
        String $from = from.getValue();

        NbtHelper.withNbtFile(computePath($from), root -> {
            ensureDeathlogNotEmpty(player.getCommandSource(), root);

            NbtList deaths = NbtHelper.withNbtElement(root, DEATHS, new NbtList());

            MutableText deathlogViewText = Text.empty();
            String to = player.getGameProfile().getName();
            for (int i = 0; i < deaths.size(); i++) {
                deathlogViewText.append(asViewText(player, deaths.getCompound(i), $from, i, to));
            }

            player.sendMessage(deathlogViewText);
        });

        return CommandHelper.Return.SUCCESS;
    }

    private static @NotNull Text asViewText(Object audience, @NotNull NbtCompound node, String from, int index, String to) {
        NbtCompound remarkTag = node.getCompound(REMARK);

        MutableText hoverText = Text.empty()
            .append(TextHelper.getTextByKey(audience, "deathlog.view.time", remarkTag.getString(TIME)))
            .append(TextHelper.TEXT_NEWLINE)
            .append(TextHelper.getTextByKey(audience, "deathlog.view.reason", remarkTag.getString(REASON)))
            .append(TextHelper.TEXT_NEWLINE)
            .append(TextHelper.getTextByKey(audience, "deathlog.view.dimension", remarkTag.getString(DIMENSION)))
            .append(TextHelper.TEXT_NEWLINE)
            .append(TextHelper.getTextByKey(audience, "deathlog.view.coordinate", remarkTag.getDouble(X), remarkTag.getDouble(Y), remarkTag.getDouble(Z)));

        return Text
            .literal(String.valueOf(index))
            .fillStyle(Style.EMPTY
                .withFormatting(Formatting.RED)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/deathlog restore %s %d %s".formatted(from, index, to)))
            )
            .append(TextHelper.TEXT_SPACE);
    }

    public static void store(@NotNull ServerPlayerEntity player) {
        /* verify */
        if (player.getInventory().isEmpty()) return;

        /* primary */
        NbtHelper.withNbtFile(computePath(player.getGameProfile().getName()), root -> {
            NbtList deathsNode = NbtHelper.withNbtElement(root, DEATHS, new NbtList());
            deathsNode.add(makeDeathNode(player));
        });
    }

    private static @NotNull NbtCompound makeDeathNode(@NotNull ServerPlayerEntity player) {
        NbtCompound node = new NbtCompound();
        writeInventoryNode(node, player);
        writeRemarkNode(node, player);
        return node;
    }

    private static void writeRemarkNode(@NotNull NbtCompound node, @NotNull ServerPlayerEntity player) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String reason = player.getDamageTracker().getDeathMessage().getString();
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        Vec3d position = player.getPos();

        NbtCompound remarkTag = new NbtCompound();
        remarkTag.putString(TIME, time);
        remarkTag.putString(REASON, reason);
        remarkTag.putString(DIMENSION, dimension);
        remarkTag.putDouble(X, position.x);
        remarkTag.putDouble(Y, position.y);
        remarkTag.putDouble(Z, position.z);
        node.put(REMARK, remarkTag);
    }

    private static void writeInventoryNode(@NotNull NbtCompound node, @NotNull ServerPlayerEntity player) {
        NbtCompound inventoryTag = new NbtCompound();
        PlayerInventory inventory = player.getInventory();
        inventoryTag.put(ARMOR, NbtHelper.writeSlotsNode(new NbtList(), inventory.armor));
        inventoryTag.put(OFFHAND, NbtHelper.writeSlotsNode(new NbtList(), inventory.offHand));
        inventoryTag.put(ITEM, NbtHelper.writeSlotsNode(new NbtList(), inventory.main));
        inventoryTag.putInt(SCORE, player.getScore());
        inventoryTag.putInt(XP_LEVEL, player.experienceLevel);
        inventoryTag.putFloat(XP_PROGRESS, player.experienceProgress);
        node.put(INVENTORY, inventoryTag);
    }

    @SneakyThrows(IOException.class)
    @Override
    protected void onInitialize() {
        Files.createDirectories(DEATH_DATA_DIR_PATH);
    }

}
