package dev.autostock.server;

import carpet.fakes.ServerPlayerInterface;
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.text.Text;

/** Stop input first, preserve the actual cursor, then let vanilla save the fake's playerdata. */
final class TaskStop {
    static void pause(EntityPlayerMPFake player) {
        if (!MinecraftCompat.server(player).isOnThread()) throw new IllegalArgumentException("停止操作必须在服务器线程");
        ((ServerPlayerInterface)player).getActionPack().stopAll();
        TaskAttachments.preserveCursor(player);
        if (player.currentScreenHandler != player.playerScreenHandler) player.closeHandledScreen();
    }
    static void logout(EntityPlayerMPFake player, String reason) {
        pause(player); player.kill(Text.literal(reason));
    }
}
