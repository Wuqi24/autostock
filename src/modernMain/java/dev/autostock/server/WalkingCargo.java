package dev.autostock.server;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Protects the inventory present before walking; only newly acquired quantities are discarded. */
final class WalkingCargo {
    private final ServerPlayer player;
    private final ItemStack[] before = new ItemStack[36];
    private List<SafePath.Node> route = List.of();

    WalkingCargo(ServerPlayer player) {
        this.player = player;
        for (int i = 0; i < 36; i++) before[i] = player.getInventory().getItem(i).copy();
    }

    void route(List<SafePath.Node> value) { route = List.copyOf(value); }

    void discardNewItems() {
        if (route.isEmpty()) return;
        for (int slot = 0; slot < 36; slot++) {
            var current = player.getInventory().getItem(slot);
            var original = before[slot];
            if (current.isEmpty()) { before[slot] = ItemStack.EMPTY; continue; }
            if (!original.isEmpty() && !ItemStack.isSameItemSameComponents(original, current))
                throw new IllegalArgumentException("行走时原有物品被替换，已保留背包，请玩家检查");
            int extra = current.getCount() - (original.isEmpty() ? 0 : original.getCount());
            if (extra <= 0) { before[slot] = current.copy(); continue; }
            var position = roadsidePosition();
            if (position == null) throw new IllegalArgumentException("捡到物品，但附近没有路径外的安全放置点；已保留，请玩家帮助");
            var entity = new ItemEntity(MinecraftCompat.world(player), position.x, position.y, position.z, current.copyWithCount(extra));
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setPickUpDelay(200);
            if (!MinecraftCompat.world(player).addFreshEntity(entity)) throw new IllegalArgumentException("路边物品投放失败，物品仍保留");
            current.shrink(extra);
            if (current.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
    }

    private Vec3 roadsidePosition() {
        var world = MinecraftCompat.world(player);
        var origin = player.blockPosition();
        for (int radius = 2; radius <= 4; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            BlockPos pos = origin.offset(dx, 0, dz);
            if (!world.hasChunkAt(pos) || !world.getWorldBorder().isWithinBounds(pos)
                    || !world.getBlockState(pos).isAir() || !world.getFluidState(pos).isEmpty()
                    || !world.getBlockState(pos.below()).isCollisionShapeFullBlock(world, pos.below())) continue;
            boolean onRoute = route.stream().anyMatch(n -> Math.abs(n.x() - pos.getX()) <= 1 && Math.abs(n.z() - pos.getZ()) <= 1);
            if (onRoute) continue;
            var destination=Vec3.atBottomCenterOf(pos).add(0,.1,0);
            if(world.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(),destination,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.ANY,player)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
            return destination;
        }
        return null;
    }
}
