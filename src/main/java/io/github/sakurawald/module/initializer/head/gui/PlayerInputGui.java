package io.github.sakurawald.module.initializer.head.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import io.github.sakurawald.config.Configs;
import io.github.sakurawald.module.ModuleManager;
import io.github.sakurawald.module.initializer.head.HeadModule;
import io.github.sakurawald.module.initializer.head.gui.HeadGui;
import io.github.sakurawald.util.MessageUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

class PlayerInputGui extends AnvilInputGui {
    final HeadModule module = ModuleManager.getInitializer(HeadModule.class);
    private final HeadGui parentGui;
    private final ItemStack outputStack = Items.PLAYER_HEAD.getDefaultInstance();
    private long apiDebounce = 0;

    public PlayerInputGui(HeadGui parentGui) {
        super(parentGui.player, false);
        this.parentGui = parentGui;
        this.setDefaultInputValue("");
        this.setSlot(1, Items.PLAYER_HEAD.getDefaultInstance());
        this.setSlot(2, outputStack);
        this.setTitle(MessageUtil.ofVomponent(player, "head.category.player"));
    }

    @Override
    public void onTick() {
        if (apiDebounce != 0 && apiDebounce <= System.currentTimeMillis()) {
            apiDebounce = 0;

            CompletableFuture.runAsync(() -> {
                MinecraftServer server = player.server;
                GameProfileCache profileCache = server.getProfileCache();
                if (profileCache == null) {
                    outputStack.removeTagKey("SkullOwner");
                    return;
                }

                Optional<GameProfile> possibleProfile = profileCache.get(this.getInput());
                MinecraftSessionService sessionService = server.getSessionService();
                if (possibleProfile.isEmpty()) {
                    outputStack.removeTagKey("SkullOwner");
                    return;
                }

                GameProfile profile = sessionService.fillProfileProperties(possibleProfile.get(), false);
                Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = sessionService.getTextures(profile, false);
                if (textures.isEmpty()) {
                    outputStack.removeTagKey("SkullOwner");
                    return;
                }

                MinecraftProfileTexture texture = textures.get(MinecraftProfileTexture.Type.SKIN);
                CompoundTag ownerTag = outputStack.getOrCreateTagElement("SkullOwner");
                ownerTag.putUUID("Id", profile.getId());
                ownerTag.putString("Name", profile.getName());

                CompoundTag propertiesTag = new CompoundTag();
                ListTag texturesTag = new ListTag();
                CompoundTag textureValue = new CompoundTag();

                textureValue.putString("Value", new String(Base64.getEncoder().encode(String.format("{\"textures\":{\"SKIN\":{\"url\":\"%s\"}}}", texture.getUrl()).getBytes()), StandardCharsets.UTF_8));

                texturesTag.add(textureValue);
                propertiesTag.put("textures", texturesTag);
                ownerTag.put("Properties", propertiesTag);

                var builder = GuiElementBuilder.from(outputStack);
                if (Configs.headHandler.model().economyType != HeadModule.EconomyType.FREE) {
                    builder.addLoreLine(Component.empty());
                    builder.addLoreLine(MessageUtil.ofVomponent(player, "head.price").copy().append(module.getCost()));
                }

                this.setSlot(2, builder.asStack(), (index, type, action, gui) ->
                        module.tryPurchase(player, 1, () -> {
                            var cursorStack = getPlayer().containerMenu.getCarried();
                            if (player.containerMenu.getCarried().isEmpty()) {
                                player.containerMenu.setCarried(outputStack.copy());
                            } else if (ItemStack.isSameItemSameTags(outputStack, cursorStack) && cursorStack.getCount() < cursorStack.getMaxStackSize()) {
                                cursorStack.grow(1);
                            } else {
                                player.drop(outputStack.copy(), false);
                            }
                        })
                );
            });
        }
    }

    @Override
    public void onInput(String input) {
        super.onInput(input);
        apiDebounce = System.currentTimeMillis() + 500;
    }

    @Override
    public void onClose() {
        parentGui.open();
    }
}