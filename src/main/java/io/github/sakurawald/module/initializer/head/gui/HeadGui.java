package io.github.sakurawald.module.initializer.head.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import io.github.sakurawald.module.ModuleManager;
import io.github.sakurawald.module.initializer.head.HeadModule;
import io.github.sakurawald.module.initializer.head.api.Category;
import io.github.sakurawald.util.MessageUtil;
import java.util.ArrayList;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;

public class HeadGui extends SimpleGui {
    protected final ServerPlayerEntity player;
    final HeadModule module = ModuleManager.getInitializer(HeadModule.class);

    public HeadGui(ServerPlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X2, player, false);

        this.player = player;

        int index = 0;
        for (Category category : Category.values()) {
            addCategoryButton(index, category);
            ++index;
        }
        this.setTitle(MessageUtil.ofVomponent(player, "head.title"));
        this.setSlot(this.getSize() - 1, new GuiElementBuilder()
                .setItem(Items.COMPASS)
                .setName(MessageUtil.ofVomponent(player, "search"))
                .setCallback((index1, type1, action) -> new SearchInputGui(this).open()));
        this.setSlot(this.getSize() - 2, new GuiElementBuilder()
                .setItem(Items.PLAYER_HEAD)
                .setName(MessageUtil.ofVomponent(player, "head.category.player"))
                .setCallback((index1, type1, action) -> new PlayerInputGui(this).open()));
    }

    private void addCategoryButton(int index, Category category) {
        this.setSlot(index, category.of(player), (i, type, action, gui) -> {
            var headsGui = new PagedHeadsGui(this, new ArrayList<>(module.heads.get(category)));
            headsGui.setTitle(category.getDisplayName(player));
            headsGui.open();
        });
    }


}
