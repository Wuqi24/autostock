//? if >1.21 {
package dev.autostock.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Explicit OP-only diagnostic; does not spawn players or transfer items. */
public final class QuickShulkerDiagnostics {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                literal("autostock-quickcheck").requires(source -> MinecraftCompat.hasPermission(source, 2))
                        .then(argument("fakeplayer", EntityArgument.player())
                                .then(argument("slot", IntegerArgumentType.integer(0, 35)).executes(context -> {
                                    var source = context.getSource();
                                    if (!FabricLoader.getInstance().isModLoaded("quickshulker")
                                            || !FabricLoader.getInstance().isModLoaded("carpet")) {
                                        source.sendFailure(Component.literal("服务端需安装当前 Minecraft 版本对应的 QuickShulker 3.1.0 与 Carpet"));
                                        return 0;
                                    }
                                    var player = EntityArgument.getPlayer(context, "fakeplayer");
                                    int slot = IntegerArgumentType.getInteger(context, "slot");
                                    ItemStack before = player.getInventory().getItem(slot).copy();
                                    try {
                                        var session = QuickShulkerSession.open(player, slot);
                                        session.closeVerified();
                                        if (!ItemStack.matches(before, player.getInventory().getItem(slot))) {
                                            throw new IllegalArgumentException("开关盒后完整物品数据改变，请人工检查");
                                        }
                                        source.sendSuccess(() -> Component.literal("QuickShulker 假人开关盒核对通过；没有执行材料转移"), false);
                                        return 1;
                                    } catch (RuntimeException | LinkageError error) {
                                        dev.autostock.AutoStock.LOG.warn("QuickShulker diagnostic failed", error);
                                        source.sendFailure(Component.literal("开盒诊断失败，保留现场：" + (error.getMessage() == null ? "请查看服务端日志" : error.getMessage())));
                                        return 0;
                                    }
                                })))));
    }
}
//?}
