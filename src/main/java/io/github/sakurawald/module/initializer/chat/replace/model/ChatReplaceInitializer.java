package io.github.sakurawald.module.initializer.chat.replace.model;

import io.github.sakurawald.core.auxiliary.minecraft.TextHelper;
import io.github.sakurawald.core.config.handler.abst.BaseConfigurationHandler;
import io.github.sakurawald.core.config.handler.impl.ObjectConfigurationHandler;
import io.github.sakurawald.core.structure.RegexRewriteNode;
import io.github.sakurawald.module.initializer.ModuleInitializer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class ChatReplaceInitializer extends ModuleInitializer {

    public static final BaseConfigurationHandler<ChatReplaceConfigModel> config = new ObjectConfigurationHandler<>(BaseConfigurationHandler.CONFIG_JSON, ChatReplaceConfigModel.class);

    public static Text rewriteChatText(PlayerEntity player, Text text) {
        MutableText ret = text.copy();

        for (RegexRewriteNode rule : config.model().replace.regex) {
            ret = TextHelper.replaceTextWithRegex(ret, rule.getRegex(), () -> TextHelper.getTextByValue(player, rule.getReplacement()));
        }

        return ret;
    }

}
