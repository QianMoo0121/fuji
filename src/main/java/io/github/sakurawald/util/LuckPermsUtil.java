package io.github.sakurawald.util;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.util.Tristate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;
import java.util.function.Function;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
@UtilityClass
public class LuckPermsUtil {

    private static LuckPerms luckPerms;

    private static LuckPerms getAPI() {
        if (luckPerms == null) {
            try {
                luckPerms = LuckPermsProvider.get();
            } catch (Exception e) {
                return null;
            }
            return luckPerms;
        }
        return luckPerms;
    }

    public static Tristate checkPermission(ServerPlayerEntity player, String permission) {
        if (getAPI() == null || PlayerUtil.isFakePlayer(player)) {
            return Tristate.UNDEFINED;
        }

        return getAPI().getPlayerAdapter(ServerPlayerEntity.class)
                .getUser(player)
                .getCachedData()
                .getPermissionData().checkPermission(permission);
    }

    public static boolean hasPermission(PlayerEntity player, String permission) {
        return checkPermission((ServerPlayerEntity) player, permission).asBoolean();
    }

    public static boolean hasPermission(ServerPlayerEntity player, String permission) {
        return checkPermission(player, permission).asBoolean();
    }

    public static <T> @NonNull Optional<T> getMeta(ServerPlayerEntity player, String meta, @NonNull Function<String, ? extends T> valueTransformer) {
        if (getAPI() == null || PlayerUtil.isFakePlayer(player)) {
            return Optional.empty();
        }

        return getAPI().getPlayerAdapter(ServerPlayerEntity.class)
                .getMetaData(player)
                .getMetaValue(meta, valueTransformer);

    }

    public static <T> String getPrefix(ServerPlayerEntity player) {
        if (getAPI() == null || PlayerUtil.isFakePlayer(player)) {
            return null;
        }

        return getAPI().getPlayerAdapter(ServerPlayerEntity.class)
                .getMetaData(player)
                .getPrefix();

    }

    public static <T> String getSuffix(ServerPlayerEntity player) {
        if (getAPI() == null) {
            return null;
        }

        return getAPI().getPlayerAdapter(ServerPlayerEntity.class)
                .getMetaData(player)
                .getSuffix();

    }

    public static void saveMeta(ServerPlayerEntity player, String key, String value) {
        User user = getAPI().getUserManager()
                .getUser(player.getGameProfile().getName());

        MetaNode node = MetaNode.builder(key, value).build();
        user.data().clear(NodeType.META.predicate(mn -> mn.getMetaKey().equals(key)));
        user.data().add(node);
        getAPI().getUserManager().saveUser(user);
    }

}
