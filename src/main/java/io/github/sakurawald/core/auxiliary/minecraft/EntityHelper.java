package io.github.sakurawald.core.auxiliary.minecraft;

import com.mojang.authlib.GameProfile;
import lombok.experimental.UtilityClass;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.UserCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@UtilityClass
public class EntityHelper {

    private static final String DIMENSION_NBT_KEY = "Dimension";

    public static boolean isRealPlayer(@NotNull ServerPlayerEntity player) {
        return player.getClass() == ServerPlayerEntity.class;
    }

    public static void setDimension(ServerPlayerEntity player, @Nullable NbtCompound root) {
        if (root == null) return;
        if (root.contains(DIMENSION_NBT_KEY)) {
            String dimensionId = root.getString(DIMENSION_NBT_KEY);
            ServerWorld world = RegistryHelper.ofServerWorld(Identifier.of(dimensionId));
            if (world != null) {
                player.setServerWorld(world);
            }
        }
    }

    public static ServerPlayerEntity loadOfflinePlayer(String playerName) {
        Optional<GameProfile> gameProfile = getGameProfileByName(playerName);
        if (gameProfile.isEmpty()) {
            throw new IllegalArgumentException("Can't find player %s in usercache.json".formatted(playerName));
        }

        ServerPlayerEntity player = ServerHelper.getPlayerManager().createPlayer(gameProfile.get(), SyncedClientOptions.createDefault());

            /*
             the default dimension for ServerPlayerEntity instance if minecraft:overworld.
             in order to keep original dimension, here we should set dimension for the loaded player entity.
             */
        Optional<NbtCompound> playerDataOpt = ServerHelper.getPlayerManager().loadPlayerData(player);
        setDimension(player, playerDataOpt.orElse(null));
        return player;
    }

    private static Optional<GameProfile> getGameProfileByName(String playerName) {
        UserCache userCache = ServerHelper.getDefaultServer().getUserCache();
        if (userCache == null) return Optional.empty();

        return userCache.findByName(playerName);
    }
}
