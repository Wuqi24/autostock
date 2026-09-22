package dev.autostock.server;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.LinkedHashMap;
import java.util.Map;

/** An explicit borrowed-source phase. No box contents are read until the physical box is in the fake's bag. */
final class SourceBoxFlow {
    private final ServerPlayerEntity player;
    private final BlockPos origin;
    private final int originSlot, temporarySlot;
    private final ItemStack sourceBox;
    private record Part(BlockEntity entity, net.minecraft.block.BlockState state) { }
    private final Map<BlockPos, Part> parts;
    private boolean finished;
    record Origin(int x, int y, int z, int slot, Map<String,String> identities) {
        Origin {
            identities = Map.copyOf(identities);
            if (slot < 0 || slot >= 54 || identities.isEmpty() || identities.size() > 2)
                throw new IllegalArgumentException("来源记录无效，保留现场");
        }
        BlockPos position() { return new BlockPos(x,y,z); }
    }
    Origin origin() {
        var identities = new java.util.TreeMap<String,String>();
        parts.forEach((pos,part) -> identities.put(pos.toShortString(),TaskAttachments.identity(part.entity())+"|"+part.state()));
        return new Origin(origin.getX(),origin.getY(),origin.getZ(),originSlot,identities);
    }
    static SourceBoxFlow restore(ServerPlayerEntity player, Origin origin, int temporarySlot) {
        ready(player); var parts = parts(player,origin.position());
        if (temporarySlot < 0 || temporarySlot >= 36 || parts.size() != origin.identities().size())
            throw new IllegalArgumentException("来源结构或临时槽位改变，保留现场");
        for (var entry : parts.entrySet()) if (!(TaskAttachments.identity(entry.getValue().entity())+"|"+entry.getValue().state()).equals(origin.identities().get(entry.getKey().toShortString())))
            throw new IllegalArgumentException("原来源容器已被替换；源盒保留，等待玩家处理");
        var stack = player.getInventory().getStack(temporarySlot);
        if (!InventoryItems.isBox(stack) || stack.getCount() != 1) throw new IllegalArgumentException("恢复时源盒实际槽位不符");
        return new SourceBoxFlow(player,origin.position(),origin.slot(),temporarySlot,stack,parts);
    }

    private SourceBoxFlow(ServerPlayerEntity player, BlockPos origin, int originSlot, int temporarySlot,
                          ItemStack sourceBox, Map<BlockPos, Part> parts) {
        this.player = player; this.origin = origin; this.originSlot = originSlot;
        this.temporarySlot = temporarySlot; this.sourceBox = sourceBox; this.parts = Map.copyOf(parts);
    }
    static SourceBoxFlow take(ServerPlayerEntity player, BlockPos origin, int containerSlot, int temporarySlot) {
        ready(player);
        if (temporarySlot < 0 || temporarySlot >= 36 || !player.getInventory().getStack(temporarySlot).isEmpty())
            throw new IllegalArgumentException("源盒临时槽位必须为空");
        var parts = parts(player, origin);
        parts.values().forEach(part -> TaskAttachments.identity(part.entity()));
        var handler = openOrigin(player, origin);
        int target = playerSlot(player, handler, temporarySlot);
        if (containerSlot < 0 || containerSlot >= handler.slots.size() || handler.getSlot(containerSlot).inventory == player.getInventory()) {
            player.closeHandledScreen(); throw new IllegalArgumentException("来源槽位无效");
        }
        var box = handler.getSlot(containerSlot).getStack();
        if (!InventoryItems.isBox(box) || box.getCount() != 1 || !handler.getSlot(containerSlot).canTakeItems(player)) {
            player.closeHandledScreen(); throw new IllegalArgumentException("来源槽位必须是一个可领取的潜影盒");
        }
        var expected = box.copy();
        handler.onSlotClick(containerSlot, 0, SlotActionType.PICKUP, player);
        handler.onSlotClick(target, 0, SlotActionType.PICKUP, player);
        if (!handler.getCursorStack().isEmpty() || !handler.getSlot(containerSlot).getStack().isEmpty()
                || !ItemStack.areEqual(expected, player.getInventory().getStack(temporarySlot)))
            throw new IllegalArgumentException("领取源盒后核对失败，保留现场");
        player.closeHandledScreen();
        return new SourceBoxFlow(player, origin.toImmutable(), containerSlot, temporarySlot, player.getInventory().getStack(temporarySlot), parts);
    }
    private void verify() {
        ready(player);
        if (finished || player.getInventory().getStack(temporarySlot) != sourceBox || sourceBox.getCount() != 1)
            throw new IllegalArgumentException("源盒在途身份改变，停止并保留现场");
    }
    void pack(int sourceSlot, int workBoxSlot, int destinationSlot, int transferSlot, int count, boolean quick) {
        verify();
        if (workBoxSlot == temporarySlot || transferSlot == temporarySlot || transferSlot == workBoxSlot
                || transferSlot < 0 || transferSlot >= 36 || !player.getInventory().getStack(transferSlot).isEmpty())
            throw new IllegalArgumentException("工作盒和散料中转槽位无效");
        // Material inspection/extraction starts here, after take() verified the physical receipt.
        var source = BoxBackend.open(player, temporarySlot, quick);
        source.move(transferSlot, sourceSlot, count, false);
        source.closeVerified();
        var work = BoxBackend.open(player, workBoxSlot, quick);
        work.move(transferSlot, destinationSlot, count, true);
        work.closeVerified();
    }
    /** True means disposed successfully; failures leave the real box in the bag for the player. */
    void finish() {
        verify();
        if (!InventoryItems.contents(sourceBox).iterator().hasNext()) {
            if (temporarySlot < 9) { finished = true; return; }
            for (int slot = 0; slot < 9; slot++) if (player.getInventory().getStack(slot).isEmpty()) {
                player.getInventory().setStack(slot, sourceBox); player.getInventory().setStack(temporarySlot, ItemStack.EMPTY);
                player.getInventory().markDirty(); player.playerScreenHandler.sendContentUpdates(); finished = true; return;
            }
            throw new IllegalArgumentException("快捷栏已满，空源盒保留在背包，等待玩家处理");
        }
        if (!parts.equals(parts(player, origin))) throw new IllegalArgumentException("来源容器结构改变，源盒保留在背包");
        var handler = openOrigin(player, origin);
        if (originSlot >= handler.slots.size() || handler.getSlot(originSlot).inventory == player.getInventory()
                || !handler.getSlot(originSlot).getStack().isEmpty() || !handler.getSlot(originSlot).canInsert(sourceBox)) {
            player.closeHandledScreen(); throw new IllegalArgumentException("来源原槽位不可用，源盒保留在背包");
        }
        var expected = sourceBox.copy();
        handler.onSlotClick(playerSlot(player, handler, temporarySlot), 0, SlotActionType.PICKUP, player);
        handler.onSlotClick(originSlot, 0, SlotActionType.PICKUP, player);
        if (!handler.getCursorStack().isEmpty() || !player.getInventory().getStack(temporarySlot).isEmpty()
                || !ItemStack.areEqual(expected, handler.getSlot(originSlot).getStack()))
            throw new IllegalArgumentException("归还源盒后核对失败，保留现场");
        player.closeHandledScreen(); finished = true;
    }
    private static void ready(ServerPlayerEntity player) {
        if (!(player instanceof EntityPlayerMPFake) || !MinecraftCompat.server(player).isOnThread()) throw new IllegalArgumentException("仅允许服务端假人操作");
        if (!player.isAlive() || player.getHealth() <= player.getMaxHealth() * .5F) throw new IllegalArgumentException("低血量，保留全部物品");
        if (player.currentScreenHandler != player.playerScreenHandler || !player.currentScreenHandler.getCursorStack().isEmpty())
            throw new IllegalArgumentException("假人容器会话未结束，保留现场");
    }
    private static Map<BlockPos, Part> parts(ServerPlayerEntity player, BlockPos origin) {
        var world = MinecraftCompat.world(player);
        if (!world.isChunkLoaded(origin)) throw new IllegalArgumentException("来源区块未加载");
        var result = new LinkedHashMap<BlockPos, Part>();
        var state = world.getBlockState(origin);
        var positions = new java.util.ArrayList<BlockPos>(); positions.add(origin.toImmutable());
        if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
            positions.add(origin.offset(ChestBlock.getFacing(state)));
        for (var position : positions) {
            if (!world.isChunkLoaded(position)) throw new IllegalArgumentException("来源区块未加载");
            var entity = world.getBlockEntity(position);
            if (!(entity instanceof LootableContainerBlockEntity container) || container.getLootTable() != null)
                throw new IllegalArgumentException("来源容器无效或战利品尚未生成");
            var structuralState = world.getBlockState(position);
            // Barrel lid animation is not a change of container identity or slot ordering.
            if (structuralState.contains(net.minecraft.block.BarrelBlock.OPEN)) structuralState = structuralState.with(net.minecraft.block.BarrelBlock.OPEN, false);
            result.put(position, new Part(entity, structuralState));
        }
        return result;
    }
    static ScreenHandler openOrigin(ServerPlayerEntity player, BlockPos pos) {
        var world = MinecraftCompat.world(player); var eye = player.getEyePos(); var target = Vec3d.ofCenter(pos);
        if (eye.squaredDistanceTo(target) > 4.5 * 4.5) throw new IllegalArgumentException("来源容器超出交互距离");
        var hit = world.raycast(new RaycastContext(eye, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) throw new IllegalArgumentException("来源容器被遮挡：" + hit.getBlockPos().toShortString() + "（" + hit.getType() + "），目标 " + pos.toShortString());
        var factory = world.getBlockState(pos).createScreenHandlerFactory(world, pos);
        if (factory == null || player.openHandledScreen(factory).isEmpty()) throw new IllegalArgumentException("来源容器无法打开或被锁定");
        if (!player.currentScreenHandler.canUse(player)) throw new IllegalArgumentException("来源容器会话不可用");
        return player.currentScreenHandler;
    }
    private static int playerSlot(ServerPlayerEntity player, ScreenHandler handler, int index) {
        for (int i = 0; i < handler.slots.size(); i++) {
            var slot = handler.getSlot(i);
            if (slot.inventory == player.getInventory() && slot.getIndex() == index) return i;
        }
        throw new IllegalArgumentException("找不到背包槽位");
    }
}
