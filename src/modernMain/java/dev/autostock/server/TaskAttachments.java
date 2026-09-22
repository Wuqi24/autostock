package dev.autostock.server;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.UUID;

/** Persistent world-owned identity and an emergency cursor slot; neither is a progress-log inventory. */
public final class TaskAttachments {
    private static final AttachmentType<String> CONTAINER_ID = AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath("autostock","container_identity"), Codec.STRING);
    private static final AttachmentType<ItemStack> CURSOR = AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath("autostock","retained_cursor"), ItemStack.CODEC);
    public static void register() { }
    static String identity(BlockEntity entity) { return entity.getAttachedOrCreate(CONTAINER_ID, () -> UUID.randomUUID().toString()); }
    static ItemStack retained(ServerPlayer player) { return player.getAttachedOrElse(CURSOR,ItemStack.EMPTY); }
    static void preserveCursor(ServerPlayer player) {
        var cursor = player.containerMenu.getCarried(); if (cursor.isEmpty()) return;
        if (!retained(player).isEmpty()) throw new IllegalArgumentException("已有保留光标物品，请先处理现场");
        player.setAttached(CURSOR,cursor.copy()); player.containerMenu.setCarried(ItemStack.EMPTY);
    }
    static boolean restoreCursor(ServerPlayer player) {
        var retained = retained(player); if (retained.isEmpty()) return true;
        for (int i = 0; i < 36; i++) if (player.getInventory().getItem(i).isEmpty()) {
            player.getInventory().setItem(i,retained.copy()); player.removeAttached(CURSOR); player.getInventory().setChanged(); return true;
        }
        return false;
    }
}
