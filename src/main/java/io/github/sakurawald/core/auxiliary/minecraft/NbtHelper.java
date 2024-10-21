package io.github.sakurawald.core.auxiliary.minecraft;

import io.github.sakurawald.core.auxiliary.LogUtil;
import io.github.sakurawald.core.command.exception.AbortCommandExecutionException;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@UtilityClass
public class NbtHelper {

    public static <T extends NbtElement> void setPath(@NotNull NbtCompound root, @NotNull String path, T value) {
        /* walk the path */
        String[] nodes = path.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            String node = nodes[i];

            if (!root.contains(node)) {
                root.put(node, new NbtCompound());
            }

            root = root.getCompound(node);
        }

        /* set the value */
        String key = nodes[nodes.length - 1];
        root.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public static <T extends NbtElement> T withNbtElement(@NotNull NbtCompound root, @NotNull String path, T orElse) {
        if (readPath(root, path) == null) {
            setPath(root, path, orElse);
        }

        return (T) readPath(root, path);
    }

    public static @Nullable NbtElement readPath(@NotNull NbtCompound root, @NotNull String path) {
        // search the path
        String[] nodes = path.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            String node = nodes[i];

            if (!root.contains(node)) {
                LogUtil.error("nbt {} don't has path {}", root, path);
            }

            root = root.getCompound(node);
        }

        // get the value
        String key = nodes[nodes.length - 1];
        return root.get(key);
    }

    public static NbtList writeSlotsNode(@NotNull NbtList node, @NotNull List<ItemStack> itemStackList) {
        for (ItemStack item : itemStackList) {
            node.add(item.toNbtAllowEmpty(RegistryHelper.getDefaultWrapperLookup()));
        }
        return node;
    }

    public static @NotNull List<ItemStack> readSlotsNode(@Nullable NbtList node) {
        if (node == null) return new ArrayList<>();

        List<ItemStack> ret = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            ret.add(ItemStack.fromNbtOrEmpty(RegistryHelper.getDefaultWrapperLookup(), node.getCompound(i)));
        }
        return ret;
    }


    public static void withNbtFile(@NotNull Path path, @NotNull Consumer<NbtCompound> function) {
        //discard the return value
        withNbtFileAndGettingReturnValue(path, (root) -> {
            function.accept(root);
            return null;
        });
    }

    @SneakyThrows(IOException.class)
    public static <T> T withNbtFileAndGettingReturnValue(@NotNull Path path, @NotNull Function<NbtCompound, T> function) {
        /* make file if not exists */
        if (Files.notExists(path)) {
            NbtIo.write(new NbtCompound(), path);
        }

        /* read the file */
        NbtCompound read = NbtIo.read(path);
        if (read == null) {
            LogUtil.error("failed to read the nbt file in {}", path);
            throw new AbortCommandExecutionException();
        }

        /* call the consumer */
        T value = function.apply(read);

        /* always write the data back, whether it's a destructive operation or not */
        NbtIo.write(read, path);

        /* return the useful value to outer space */
        return value;
    }
}
