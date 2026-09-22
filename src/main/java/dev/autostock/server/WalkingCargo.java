package dev.autostock.server;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.List;

/** Protects the inventory present before walking; only newly acquired quantities are discarded. */
final class WalkingCargo {
    private final ServerPlayerEntity player;
    private final ItemStack[] before = new ItemStack[36];
    private List<SafePath.Node> route = List.of();

    WalkingCargo(ServerPlayerEntity player) {
        this.player = player;
        for (int i = 0; i < 36; i++) before[i] = player.getInventory().getStack(i).copy();
    }

    void route(List<SafePath.Node> value) { route = List.copyOf(value); }

    void discardNewItems() {
        if (route.isEmpty()) return;
        for (int slot = 0; slot < 36; slot++) {
            var current = player.getInventory().getStack(slot);
            var original = before[slot];
            if (current.isEmpty()) { before[slot] = ItemStack.EMPTY; continue; }
            if (!original.isEmpty() && !ItemStack.areItemsAndComponentsEqual(original, current))
                throw new IllegalArgumentException("行走时原有物品被替换，已保留背包，请玩家检查");
            int extra = current.getCount() - (original.isEmpty() ? 0 : original.getCount());
            if (extra <= 0) { before[slot] = current.copy(); continue; }
            var position = roadsidePosition();
            if (position == null) throw new IllegalArgumentException("捡到物品，但附近没有路径外的安全放置点；已保留，请玩家帮助");
            var entity = new ItemEntity(MinecraftCompat.world(player), position.x, position.y, position.z, current.copyWithCount(extra));
            entity.setVelocity(Vec3d.ZERO);
            entity.setPickupDelay(200);
            if (!MinecraftCompat.world(player).spawnEntity(entity)) throw new IllegalArgumentException("路边物品投放失败，物品仍保留");
            current.decrement(extra);
            if (current.isEmpty()) player.getInventory().setStack(slot, ItemStack.EMPTY);
            player.getInventory().markDirty();
            player.playerScreenHandler.sendContentUpdates();
        }
    }

    private Vec3d roadsidePosition() {
        var world = MinecraftCompat.world(player);
        var origin = player.getBlockPos();
        for (int radius = 2; radius <= 4; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            BlockPos pos = origin.add(dx, 0, dz);
            if (!world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos)
                    || !world.getBlockState(pos).isAir() || !world.getFluidState(pos).isEmpty()
                    || !world.getBlockState(pos.down()).isFullCube(world, pos.down())) continue;
            boolean onRoute = route.stream().anyMatch(n -> Math.abs(n.x() - pos.getX()) <= 1 && Math.abs(n.z() - pos.getZ()) <= 1);
            if (onRoute) continue;
            var destination=Vec3d.ofBottomCenter(pos).add(0,.1,0);
            if(world.raycast(new net.minecraft.world.RaycastContext(player.getEyePos(),destination,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.ANY,player)).getType()!=net.minecraft.util.hit.HitResult.Type.MISS)continue;
            return destination;
        }
        return null;
    }
}
