package dev.autostock.server;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.patches.EntityPlayerMPFake;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Drives Carpet's regular movement input. Never teleports a player to advance a route. */
final class FakeWalker {
    enum State { SEARCHING, WALKING, ARRIVED, FAILED }
    private final ServerPlayer player;
    private final SafePath search;
    private final BlockPos target;
    private final boolean interaction;
    private State state = State.SEARCHING;
    private List<SafePath.Node> route = List.of();
    private int index, stalled, ticks;
    private double lastDistance = Double.MAX_VALUE;
    private String failure;
    private final WalkingCargo cargo;
    FakeWalker(ServerPlayer player, BlockPos target, boolean interaction) {
        if (!(player instanceof EntityPlayerMPFake)) throw new IllegalArgumentException("仅支持 Carpet 假人移动");
        this.player = player; this.target = target.immutable(); this.interaction = interaction;
        cargo = new WalkingCargo(player);
        search = new SafePath(player,target,interaction); actions().stopAll();
    }
    FakeWalker(ServerPlayer player, SafePath.Route planned) {
        this(player,planned.target(),true);
        if (!planned.found() || planned.nodes().isEmpty()) throw new IllegalArgumentException("路径不可执行");
        route = planned.nodes(); state = State.WALKING;
        cargo.route(route);
    }
    private EntityPlayerActionPack actions() { return ((ServerPlayerInterface) player).getActionPack(); }
    State state() { return state; }
    String failure() { return failure; }
    List<SafePath.Node> remainingPath() { return route.subList(Math.min(index,route.size()),route.size()); }
    void stop() { actions().stopAll();if(originalSpeed>=0)player.getAttribute(MinecraftCompat.movementSpeed()).setBaseValue(originalSpeed); }
    private void fail(String reason) { stop(); failure = reason; state = State.FAILED; }
    private double originalSpeed=-1;
    void tick() {
        if (state == State.ARRIVED || state == State.FAILED) return;
        if (!MinecraftCompat.server(player).isSameThread()) throw new IllegalArgumentException("移动必须在服务器线程执行");
        if (!player.isAlive() || player.getHealth() <= player.getMaxHealth() * .5F) { fail("低血量，停止移动并保留物品"); return; }
        try { cargo.discardNewItems(); } catch (IllegalArgumentException problem) { fail(problem.getMessage()); return; }
        var options=RuntimeOptions.get(player.getUUID());var speedAttribute=player.getAttribute(MinecraftCompat.movementSpeed());if(originalSpeed<0)originalSpeed=speedAttribute.getBaseValue();speedAttribute.setBaseValue(originalSpeed*options.moveSpeed()/100.0);
        if (++ticks > 2400/Math.max(.15,options.moveSpeed()/100.0*(options.silent()?.3:1))) { fail("前往目标超过 120 秒"); return; }
        if (player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty()) { fail("移动前容器或光标尚未结束"); return; }
        if (state == State.SEARCHING) {
            var result = search.advance(128); if (result == null) return;
            if (!result.found()) { fail(result.failure()); return; }
            route = result.nodes(); index = 0; state = State.WALKING;
            cargo.route(route);
        }
        if (index >= route.size()) {
            if (interaction && !SafePath.canInteract(player,MinecraftCompat.position(player),target)) { fail("到达后容器交互距离或视线失效"); return; }
            stop(); state = State.ARRIVED; return;
        }
        var next = route.get(index); var point = next.feet();
        double dx = point.x - player.getX(), dz = point.z - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < .17 && Math.abs(point.y - player.getY()) < .16 && player.onGround()) {
            index++; stalled = 0; lastDistance = Double.MAX_VALUE; actions().stopMovement(); return;
        }
        if (player.getY() < point.y - 1.3) { fail("实际落差超过路径允许范围"); return; }
        if (distance < lastDistance - .01) { lastDistance = distance; stalled = 0; }
        else if (++stalled > 100) { fail("假人受阻超过 5 秒，路径已失效"); return; }
        var doors = new LinkedHashSet<BlockPos>();
        var previous = route.get(Math.max(0,index - 1));
        if (!search.transition(previous,next,doors)) { fail("路径碰撞、支撑或区块状态改变"); return; }
        for (var door : doors) if (!openDoor(door)) { fail("无法打开路径上的门或活板门：" + door.toShortString()); return; }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx,dz));
        actions().look(yaw,0).setSneaking(options.silent()).setSprinting(false).setStrafing(0)
                .setForward(distance < .4 ? .35F : 1F);
        if (point.y > player.getY() + .6 && player.onGround())
            actions().start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.once());
    }
    private boolean openDoor(BlockPos pos) {
        var world = MinecraftCompat.world(player); var state = world.getBlockState(pos);
        boolean door = state.getBlock() instanceof DoorBlock;
        if (!door && !(state.getBlock() instanceof TrapDoorBlock)) return false;
        if (state.getValue(door ? DoorBlock.OPEN : TrapDoorBlock.OPEN)) return true;
        var eye = player.getEyePosition();
        var shape = state.getShape(world,pos,net.minecraft.world.phys.shapes.CollisionContext.of(player));
        if (shape.isEmpty()) return false;
        var center = shape.bounds().getCenter().add(Vec3.atLowerCornerOf(pos));
        if (eye.distanceToSqr(center) > 4.5 * 4.5) return false;
        var hit = world.clip(new ClipContext(eye,center,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) return false;
        player.gameMode.useItemOn(player,world,player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
        return world.getBlockState(pos).getValue(door ? DoorBlock.OPEN : TrapDoorBlock.OPEN);
    }
}
