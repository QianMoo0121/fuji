package io.github.sakurawald.module.initializer.functional.enchantment;

import io.github.sakurawald.command.annotation.Command;
import io.github.sakurawald.command.annotation.CommandSource;
import io.github.sakurawald.module.initializer.ModuleInitializer;
import io.github.sakurawald.module.initializer.functional.enchantment.gui.MyEnchantmentScreenHandler;
import io.github.sakurawald.util.minecraft.CommandHelper;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class EnchantmentInitializer extends ModuleInitializer {

    @Command("enchantment")
    private int $enchantment(@CommandSource ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((i, inventory, p) -> new MyEnchantmentScreenHandler(i, inventory, ScreenHandlerContext.create(p.getWorld(), p.getBlockPos())) {
        }, Text.translatable("container.enchant")));
        return CommandHelper.Return.SUCCESS;
    }
}
