package dev.autostock.server;

import carpet.patches.EntityPlayerMPFake;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** An explicit borrowed-source phase. No box contents are read until the physical box is in the fake's bag. */
final class SourceBoxFlow {
    private final ServerPlayer player;
    private final BlockPos origin;
    private final int originSlot, temporarySlot;
    private final ItemStack sourceBox;
    private record Part(BlockEntity entity, net.minecraft.world.level.block.state.BlockState state) { }
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
    static SourceBoxFlow restore(ServerPlayer player, Origin origin, int temporarySlot) {
        ready(player); var parts = parts(player,origin.position());
        if (temporarySlot < 0 || temporarySlot >= 36 || parts.size() != origin.identities().size())
            throw new IllegalArgumentException("来源结构或临时槽位改变，保留现场");
        for (var entry : parts.entrySet()) if (!(TaskAttachments.identity(entry.getValue().entity())+"|"+entry.getValue().state()).equals(origin.identities().get(entry.getKey().toShortString())))
            throw new IllegalArgumentException("原来源容器已被替换；源盒保留，等待玩家处理");
        var stack = player.getInventory().getItem(temporarySlot);
        if (!InventoryItems.isBox(stack) || stack.getCount() != 1) throw new IllegalArgumentException("恢复时源盒实际槽位不符");
        return new SourceBoxFlow(player,origin.position(),origin.slot(),temporarySlot,stack,parts);
    }

    private SourceBoxFlow(ServerPlayer player, BlockPos origin, int originSlot, int temporarySlot,
                          ItemStack sourceBox, Map<BlockPos, Part> parts) {
        this.player = player; this.origin = origin; this.originSlot = originSlot;
        this.temporarySlot = temporarySlot; this.sourceBox = sourceBox; this.parts = Map.copyOf(parts);
    }
    static SourceBoxFlow take(ServerPlayer player, BlockPos origin, int containerSlot, int temporarySlot) {
        ready(player);
        if (temporarySlot < 0 || temporarySlot >= 36 || !player.getInventory().getItem(temporarySlot).isEmpty())
            throw new IllegalArgumentException("源盒临时槽位必须为空");
        var parts = parts(player, origin);
        parts.values().forEach(part -> TaskAttachments.identity(part.entity()));
        var handler = openOrigin(player, origin);
        int target = playerSlot(player, handler, temporarySlot);
        if (containerSlot < 0 || containerSlot >= handler.slots.size() || handler.getSlot(containerSlot).container == player.getInventory()) {
            player.closeContainer(); throw new IllegalArgumentException("来源槽位无效");
        }
        var box = handler.getSlot(containerSlot).getItem();
        if (!InventoryItems.isBox(box) || box.getCount() != 1 || !handler.getSlot(containerSlot).mayPickup(player)) {
            player.closeContainer(); throw new IllegalArgumentException("来源槽位必须是一个可领取的潜影盒");
        }
        var expected = box.copy();
        handler.clicked(containerSlot, 0, ContainerInput.PICKUP, player);
        handler.clicked(target, 0, ContainerInput.PICKUP, player);
        if (!handler.getCarried().isEmpty() || !handler.getSlot(containerSlot).getItem().isEmpty()
                || !ItemStack.matches(expected, player.getInventory().getItem(temporarySlot)))
            throw new IllegalArgumentException("领取源盒后核对失败，保留现场");
        player.closeContainer();
        return new SourceBoxFlow(player, origin.immutable(), containerSlot, temporarySlot, player.getInventory().getItem(temporarySlot), parts);
    }
    private void verify() {
        ready(player);
        if (finished || player.getInventory().getItem(temporarySlot) != sourceBox || sourceBox.getCount() != 1)
            throw new IllegalArgumentException("源盒在途身份改变，停止并保留现场");
    }
    void pack(int sourceSlot, int workBoxSlot, int destinationSlot, int transferSlot, int count, boolean quick) {
        verify();
        if (workBoxSlot == temporarySlot || transferSlot == temporarySlot || transferSlot == workBoxSlot
                || transferSlot < 0 || transferSlot >= 36 || !player.getInventory().getItem(transferSlot).isEmpty())
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
            for (int slot = 0; slot < 9; slot++) if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, sourceBox); player.getInventory().setItem(temporarySlot, ItemStack.EMPTY);
                player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); finished = true; return;
            }
            throw new IllegalArgumentException("快捷栏已满，空源盒保留在背包，等待玩家处理");
        }
        if (!parts.equals(parts(player, origin))) throw new IllegalArgumentException("来源容器结构改变，源盒保留在背包");
        var handler = openOrigin(player, origin);
        if (originSlot >= handler.slots.size() || handler.getSlot(originSlot).container == player.getInventory()
                || !handler.getSlot(originSlot).getItem().isEmpty() || !handler.getSlot(originSlot).mayPlace(sourceBox)) {
            player.closeContainer(); throw new IllegalArgumentException("来源原槽位不可用，源盒保留在背包");
        }
        var expected = sourceBox.copy();
        handler.clicked(playerSlot(player, handler, temporarySlot), 0, ContainerInput.PICKUP, player);
        handler.clicked(originSlot, 0, ContainerInput.PICKUP, player);
        if (!handler.getCarried().isEmpty() || !player.getInventory().getItem(temporarySlot).isEmpty()
                || !ItemStack.matches(expected, handler.getSlot(originSlot).getItem()))
            throw new IllegalArgumentException("归还源盒后核对失败，保留现场");
        player.closeContainer(); finished = true;
    }
    private static void ready(ServerPlayer player) {
        if (!(player instanceof EntityPlayerMPFake) || !MinecraftCompat.server(player).isSameThread()) throw new IllegalArgumentException("仅允许服务端假人操作");
        if (!player.isAlive() || player.getHealth() <= player.getMaxHealth() * .5F) throw new IllegalArgumentException("低血量，保留全部物品");
        if (player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty())
            throw new IllegalArgumentException("假人容器会话未结束，保留现场");
    }
    private static Map<BlockPos, Part> parts(ServerPlayer player, BlockPos origin) {
        var world = MinecraftCompat.world(player);
        if (!world.hasChunkAt(origin)) throw new IllegalArgumentException("来源区块未加载");
        var result = new LinkedHashMap<BlockPos, Part>();
        var state = world.getBlockState(origin);
        var positions = new java.util.ArrayList<BlockPos>(); positions.add(origin.immutable());
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
            positions.add(origin.relative(ChestBlock.getConnectedDirection(state)));
        for (var position : positions) {
            if (!world.hasChunkAt(position)) throw new IllegalArgumentException("来源区块未加载");
            var entity = world.getBlockEntity(position);
            if (!(entity instanceof RandomizableContainerBlockEntity container) || container.getLootTable() != null)
                throw new IllegalArgumentException("来源容器无效或战利品尚未生成");
            var structuralState = world.getBlockState(position);
            // Barrel lid animation is not a change of container identity or slot ordering.
            if (structuralState.hasProperty(net.minecraft.world.level.block.BarrelBlock.OPEN)) structuralState = structuralState.setValue(net.minecraft.world.level.block.BarrelBlock.OPEN, false);
            result.put(position, new Part(entity, structuralState));
        }
        return result;
    }
    static AbstractContainerMenu openOrigin(ServerPlayer player, BlockPos pos) {
        var world = MinecraftCompat.world(player); var eye = player.getEyePosition(); var target = Vec3.atCenterOf(pos);
        if (eye.distanceToSqr(target) > 4.5 * 4.5) throw new IllegalArgumentException("来源容器超出交互距离");
        var hit = world.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) throw new IllegalArgumentException("来源容器被遮挡：" + hit.getBlockPos().toShortString() + "（" + hit.getType() + "），目标 " + pos.toShortString());
        var factory = world.getBlockState(pos).getMenuProvider(world, pos);
        if (factory == null || player.openMenu(factory).isEmpty()) throw new IllegalArgumentException("来源容器无法打开或被锁定");
        if (!player.containerMenu.stillValid(player)) throw new IllegalArgumentException("来源容器会话不可用");
        return player.containerMenu;
    }
    private static int playerSlot(ServerPlayer player, AbstractContainerMenu handler, int index) {
        for (int i = 0; i < handler.slots.size(); i++) {
            var slot = handler.getSlot(i);
            if (slot.container == player.getInventory() && slot.getContainerSlot() == index) return i;
        }
        throw new IllegalArgumentException("找不到背包槽位");
    }
}
