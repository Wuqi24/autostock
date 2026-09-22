package dev.autostock.server;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.UUID;

/** Persistent world-owned identity and an emergency cursor slot; neither is a progress-log inventory. */
public final class TaskAttachments {
    private static final AttachmentType<String> CONTAINER_ID = AttachmentRegistry.createPersistent(Identifier.of("autostock","container_identity"), Codec.STRING);
    private static final AttachmentType<ItemStack> CURSOR = AttachmentRegistry.createPersistent(Identifier.of("autostock","retained_cursor"), ItemStack.CODEC);
    public static void register() { }
    static String identity(BlockEntity entity) { return entity.getAttachedOrCreate(CONTAINER_ID, () -> UUID.randomUUID().toString()); }
    static ItemStack retained(ServerPlayerEntity player) { return player.getAttachedOrElse(CURSOR,ItemStack.EMPTY); }
    static void preserveCursor(ServerPlayerEntity player) {
        var cursor = player.currentScreenHandler.getCursorStack(); if (cursor.isEmpty()) return;
        if (!retained(player).isEmpty()) throw new IllegalArgumentException("已有保留光标物品，请先处理现场");
        player.setAttached(CURSOR,cursor.copy()); player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
    }
    static boolean restoreCursor(ServerPlayerEntity player) {
        var retained = retained(player); if (retained.isEmpty()) return true;
        for (int i = 0; i < 36; i++) if (player.getInventory().getStack(i).isEmpty()) {
            player.getInventory().setStack(i,retained.copy()); player.removeAttached(CURSOR); player.getInventory().markDirty(); return true;
        }
        return false;
    }
}
