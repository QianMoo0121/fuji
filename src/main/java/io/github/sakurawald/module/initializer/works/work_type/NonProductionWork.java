package io.github.sakurawald.module.initializer.works.work_type;

import eu.pb4.sgui.api.gui.SimpleGui;
import io.github.sakurawald.util.MessageUtil;
import lombok.NoArgsConstructor;
import net.minecraft.server.level.ServerPlayer;

@NoArgsConstructor
public class NonProductionWork extends Work {
    public NonProductionWork(ServerPlayer player, String name) {
        super(player, name);
    }

    @Override
    protected String getType() {
        return WorkTypeAdapter.WorkType.NonProductionWork.name();
    }

    @Override
    protected String getDefaultIcon() {
        return "minecraft:gunpowder";
    }

    @Override
    public void openSpecializedSettingsGui(ServerPlayer player, SimpleGui parentGui) {
        MessageUtil.sendActionBar(player, "works.non_production_work.specialized_settings.not_found");
    }
}
