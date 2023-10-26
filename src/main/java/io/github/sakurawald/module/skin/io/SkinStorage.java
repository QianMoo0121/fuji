package io.github.sakurawald.module.skin.io;

import com.mojang.authlib.properties.Property;
import io.github.sakurawald.config.base.ConfigManager;
import lombok.Getter;

import java.util.*;

public class SkinStorage {

    private final Map<UUID, Property> skinMap = new HashMap<>();

    @Getter
    private final SkinIO skinIO;

    public SkinStorage(SkinIO skinIO) {
        this.skinIO = skinIO;
    }

    public Property getRandomSkin(UUID uuid) {
        if (!skinMap.containsKey(uuid)) {
            ArrayList<Property> defaultSkins = ConfigManager.configWrapper.instance().modules.skin.random_skins;
            Property skin = defaultSkins.get(new Random().nextInt(defaultSkins.size()));
            setSkin(uuid, skin);
        }

        return skinMap.get(uuid);
    }

    public Property getDefaultSkin() {
        return ConfigManager.configWrapper.instance().modules.skin.default_skin;
    }

    public Property getSkin(UUID uuid) {
        if (!skinMap.containsKey(uuid)) {
            Property skin = skinIO.loadSkin(uuid);
            setSkin(uuid, skin);
        }

        return skinMap.get(uuid);
    }

    public void removeSkin(UUID uuid) {
        if (skinMap.containsKey(uuid)) {
            skinIO.saveSkin(uuid, skinMap.get(uuid));
        }
    }

    public void setSkin(UUID uuid, Property skin) {
        // if a player has no skin, use default skin.
        if (skin == null)
            skin = this.getDefaultSkin();

        skinMap.put(uuid, skin);
    }
}
