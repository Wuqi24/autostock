package dev.autostock.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Incremental cardinal A*: actual collision shapes, no teleporting and no forced chunk loads. */
public final class SafePath {
    public record Node(int x, int y16, int z) {
        public double y() { return y16 / 16.0; }
        public Vec3 feet() { return new Vec3(x + .5, y(), z + .5); }
    }
    public record Route(List<Node> nodes, String failure, BlockPos target) {
        public Route(List<Node> nodes,String failure) { this(nodes,failure,null); }
        public boolean found() { return failure == null; }
    }
    private record QueueEntry(Node node, double g, double f) { }
    private final Player player;
    private final List<BlockPos> targets;
    private BlockPos reached;
    private final java.util.function.Predicate<Node> goal;
    private final PriorityQueue<QueueEntry> open = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::f));
    private final Map<Node, Double> costs = new HashMap<>();
    private final Map<Node, Node> parents = new HashMap<>();
    private final Set<String> obstacles = new LinkedHashSet<>();
    private final Node start;
    private int expanded;
    private Route result;

    public SafePath(Player player, BlockPos target, boolean interactionGoal) {
        this(player,List.of(target),interactionGoal);
    }
    public SafePath(Player player,List<BlockPos> targets,boolean interactionGoal) {
        if (targets.isEmpty() || targets.size() > 1024) throw new IllegalArgumentException("路径目标数量无效");
        this.player = player; this.targets = targets.stream().map(BlockPos::immutable).distinct().toList();
        start = new Node(player.getBlockX(), (int) Math.round(player.getY() * 16), player.getBlockZ());
        goal = node -> {
            for (var target : this.targets) if (interactionGoal
                    ? (node.x == target.getX() ^ node.z == target.getZ()) && canInteract(player,node.feet(),target)
                    : node.x == target.getX() && node.z == target.getZ() && Math.abs(node.y()-target.getY()) < .01) {
                reached = target; return true;
            }
            return false;
        };
        if (!clear(body(start.feet()), true, null) || !supported(start)) result = new Route(List.of(), "起点脚下或头部空间无效");
        else { costs.put(start, 0.0); open.add(new QueueEntry(start, 0, heuristic(start))); }
    }
    public Route advance(int budget) {
        if (budget < 1 || budget > 512) throw new IllegalArgumentException("寻路分批预算无效");
        while (result == null && budget-- > 0) {
            if (open.isEmpty() || expanded >= 8192) {
                result = new Route(List.of(), (expanded >= 8192 ? "路径搜索达到 8192 节点上限" : "没有安全可达路线")
                        + (obstacles.isEmpty() ? "" : "；遇到：" + String.join("、", obstacles))); break;
            }
            var entry = open.poll(); var current = entry.node;
            if (entry.g > costs.getOrDefault(current, Double.MAX_VALUE)) continue;
            expanded++;
            if (goal.test(current)) {
                var nodes = new ArrayList<Node>(); for (Node n = current; n != null; n = parents.get(n)) nodes.add(n);
                Collections.reverse(nodes); result = new Route(List.copyOf(nodes), null, reached); break;
            }
            for (int[] offset : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int x = current.x + offset[0], z = current.z + offset[1];
                if (Math.abs(x - start.x) > 128 || Math.abs(z - start.z) > 128) { obstacles.add("路径范围超过 128 格"); continue; }
                for (var next : surfaces(x, z, current.y())) {
                    if (!transition(current, next, null)) continue;
                    double cost = entry.g + 1 + Math.max(0, next.y() - current.y()) * .6;
                    if (cost >= costs.getOrDefault(next, Double.MAX_VALUE)) continue;
                    costs.put(next, cost); parents.put(next, current); open.add(new QueueEntry(next, cost, cost + heuristic(next)));
                }
            }
        }
        return result;
    }
    private double heuristic(Node n) {
        int nearest = Integer.MAX_VALUE;
        for (var target : targets) nearest = Math.min(nearest,Math.abs(n.x-target.getX())+Math.abs(n.z-target.getZ()));
        return Math.max(0,nearest-4);
    }
    private List<Node> surfaces(int x, int z, double fromY) {
        var heights = new TreeSet<Integer>();
        for (int y = (int) Math.floor(fromY) - 2; y <= (int) Math.floor(fromY) + 1; y++) {
            var pos = new BlockPos(x,y,z); var state = state(pos); if (state == null) continue;
            if (!state.getFluidState().isEmpty() || state.is(Blocks.LADDER)) { obstacles.add(!state.getFluidState().isEmpty() ? "水或其他流体" : "梯子"); continue; }
            for (var shape : state.getCollisionShape(MinecraftCompat.world(player), pos, CollisionContext.of(player)).toAabbs()) {
                if (shape.maxX <= .2 || shape.minX >= .8 || shape.maxZ <= .2 || shape.minZ >= .8) continue;
                double top = y + shape.maxY;
                if (top <= fromY + 1.001 && top >= fromY - 1.001) heights.add((int) Math.round(top * 16));
                else if (top < fromY - 1.001) obstacles.add("落差超过 1 格");
            }
        }
        var result = new ArrayList<Node>();
        for (int height : heights) {
            var node = new Node(x,height,z);
            if (clear(body(node.feet()), true, null) && supported(node)) result.add(node);
        }
        return result;
    }
    private boolean supported(Node node) {
        var feet = node.feet(); var probe = new AABB(feet.x - .29,feet.y - .04,feet.z - .29,feet.x + .29,feet.y,feet.z + .29);
        for (var pos : BlockPos.betweenClosed((int)Math.floor(probe.minX),(int)Math.floor(probe.minY),(int)Math.floor(probe.minZ),
                (int)Math.floor(probe.maxX),(int)Math.floor(probe.maxY),(int)Math.floor(probe.maxZ))) {
            var state = state(pos); if (state == null || !state.getFluidState().isEmpty()) return false;
            for (var shape : state.getCollisionShape(MinecraftCompat.world(player),pos,CollisionContext.of(player)).toAabbs())
                if (shape.move(pos).intersects(probe)) return true;
        }
        return false;
    }
    public boolean transition(Node from, Node to, Set<BlockPos> toOpen) {
        if (Math.abs(to.y() - from.y()) > 1.001) { obstacles.add("高度变化超过 1 格"); return false; }
        double high = Math.max(from.y(), to.y());
        // Check head clearance for ascending jumps and the horizontal sweep at the landing height.
        for (int step = 0; step <= 4; step++) {
            double fraction = step / 4.0;
            var point = new Vec3(from.x + .5 + (to.x - from.x) * fraction, high, from.z + .5 + (to.z - from.z) * fraction);
            if (!clear(body(point), true, toOpen)) return false;
        }
        return clear(body(to.feet()), true, toOpen) && supported(to);
    }
    public boolean actualStepClear(Vec3 feet) { return clear(body(feet), false, null); }
    private static AABB body(Vec3 feet) { return new AABB(feet.x - .299,feet.y + .002,feet.z - .299,feet.x + .299,feet.y + 1.8,feet.z + .299); }
    private BlockState state(BlockPos pos) {
        if (!MinecraftCompat.world(player).hasChunkAt(pos) || !MinecraftCompat.world(player).getWorldBorder().isWithinBounds(pos)) { obstacles.add("区块未加载或超出世界边界"); return null; }
        return MinecraftCompat.world(player).getBlockState(pos);
    }
    private boolean clear(AABB body, boolean allowOpen, Set<BlockPos> toOpen) {
        for (var pos : BlockPos.betweenClosed((int)Math.floor(body.minX),(int)Math.floor(body.minY),(int)Math.floor(body.minZ),
                (int)Math.floor(body.maxX),(int)Math.floor(body.maxY),(int)Math.floor(body.maxZ))) {
            var state = state(pos); if (state == null) return false;
            if (!state.getFluidState().isEmpty() || state.is(Blocks.LADDER)) { obstacles.add(!state.getFluidState().isEmpty() ? "水或其他流体" : "梯子"); return false; }
            if (!intersects(state, pos, body)) continue;
            boolean door = state.getBlock() instanceof DoorBlock, trap = state.getBlock() instanceof TrapDoorBlock;
            if (allowOpen && (door || trap) && !state.getValue(door ? DoorBlock.OPEN : TrapDoorBlock.OPEN)) {
                if (state.is(Blocks.IRON_DOOR) || state.is(Blocks.IRON_TRAPDOOR)) { obstacles.add("铁门/铁活板门不能手动打开"); return false; }
                var opened = state.setValue(door ? DoorBlock.OPEN : TrapDoorBlock.OPEN, true);
                if (!intersects(opened, pos, body)) {
                    if (toOpen != null) toOpen.add(door && state.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? pos.below().immutable() : pos.immutable());
                    continue;
                }
            }
            obstacles.add("碰撞或头部空间不足"); return false;
        }
        return true;
    }
    private boolean intersects(BlockState state, BlockPos pos, AABB body) {
        return state.getCollisionShape(MinecraftCompat.world(player),pos,CollisionContext.of(player)).toAabbs().stream().anyMatch(box -> box.move(pos).intersects(body));
    }
    public static boolean canInteract(Player player, Vec3 feet, BlockPos target) {
        if (!MinecraftCompat.world(player).hasChunkAt(target)) return false;
        var eye = feet.add(0,1.62,0); var center = Vec3.atCenterOf(target);
        if (eye.distanceToSqr(center) > 4.25 * 4.25) return false;
        var hit = MinecraftCompat.world(player).clip(new ClipContext(eye,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }
}

