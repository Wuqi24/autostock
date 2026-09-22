package dev.autostock.server;

import dev.autostock.core.PlanDraft;
import dev.autostock.core.RegionRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;

/** Fresh, bounded container index built at task start/recovery. Quantities are always read again before transfer. */
final class WarehouseIndex {
    record Container(BlockPos position, String key, RegionRole role, List<BlockEntity> parts, net.minecraft.world.Container inventory) {
        void verify(ServerLevel world) {
            for (var part : parts) if (!world.hasChunkAt(part.getBlockPos()) || world.getBlockEntity(part.getBlockPos()) != part)
                throw new IllegalArgumentException("容器被替换或区块卸载：" + position.toShortString());
            if (parts.size() == 1 && world.getBlockState(position).getBlock() instanceof ChestBlock
                    && world.getBlockState(position).getValue(ChestBlock.TYPE) != ChestType.SINGLE)
                throw new IllegalArgumentException("单箱已变为大箱子：" + position.toShortString());
            if (parts.size() == 2) {
                var state = world.getBlockState(position);
                if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                        || !parts.stream().anyMatch(part -> part.getBlockPos().equals(position.relative(ChestBlock.getConnectedDirection(state)))))
                    throw new IllegalArgumentException("大箱子结构改变：" + position.toShortString());
            }
        }
    }
    final ServerLevel world; private final Map<RegionRole,List<dev.autostock.core.RegionBox>> regions;private final Map<String,Long> demand;
    final List<Container> containers = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private final Iterator<ChunkPos> chunks;
    WarehouseIndex(ServerLevel world, PlanDraft draft) {
        this(world,draft.regions(),draft.demand());
    }
    WarehouseIndex(ServerLevel world,Map<RegionRole,List<dev.autostock.core.RegionBox>> regions,Map<String,Long> demand){
        this.world=world;this.regions=regions;this.demand=demand;
        var chunks = new TreeSet<ChunkPos>(Comparator.comparingInt((ChunkPos p) -> p.x()).thenComparingInt(p -> p.z()));
        for (var boxes : regions.values()) for (var box : boxes) {
            if (((long)(box.maxX() >> 4) - (box.minX() >> 4) + 1) * ((long)(box.maxZ() >> 4) - (box.minZ() >> 4) + 1) > 512)
                throw new IllegalArgumentException("选区跨度超过 512 区块");
            for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                chunks.add(new ChunkPos(x,z)); if (chunks.size() > 512) throw new IllegalArgumentException("选区跨度超过 512 区块");
            }
        }
        this.chunks = chunks.iterator();
    }
    boolean advance() {
        for (int i = 0; i < 2 && chunks.hasNext(); i++) {
            var chunk = chunks.next(); var loaded = world.getChunkSource().getChunk(chunk.x(),chunk.z(),ChunkStatus.FULL,false);
            if (!(loaded instanceof LevelChunk full)) throw new IllegalArgumentException("区块未加载，库存未知：" + chunk);
            if (full.getBlockEntities().size() > 4096) throw new IllegalArgumentException("区块容器过多");
            for (var entity : full.getBlockEntities().values()) discover(entity);
        }
        if (chunks.hasNext()) return false;
        containers.sort(Comparator.comparingInt((Container c) -> c.position.getX()).thenComparingInt(c -> c.position.getY()).thenComparingInt(c -> c.position.getZ()));
        return true;
    }
    private EnumSet<RegionRole> roles(BlockPos pos) {
        var result = EnumSet.noneOf(RegionRole.class);
        for (var entry : regions.entrySet()) for (var box : entry.getValue())
            if (pos.getX() >= box.minX() && pos.getX() <= box.maxX() && pos.getY() >= box.minY() && pos.getY() <= box.maxY() && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ()) result.add(entry.getKey());
        return result;
    }
    private void discover(BlockEntity entity) {
        var roleSet = roles(entity.getBlockPos()); if (roleSet.isEmpty()) return;
        if (!(entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity || entity instanceof ShulkerBoxBlockEntity)) return;
        var parts = new ArrayList<BlockEntity>(); parts.add(entity); var state = world.getBlockState(entity.getBlockPos());
        if (entity instanceof ChestBlockEntity && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            var other = entity.getBlockPos().relative(ChestBlock.getConnectedDirection(state));
            if (!world.hasChunkAt(other) || !(world.getBlockEntity(other) instanceof ChestBlockEntity)) throw new IllegalArgumentException("大箱子结构未知");
            parts.add(world.getBlockEntity(other)); roleSet.addAll(roles(other));
        }
        if (roleSet.contains(RegionRole.EMPTY_BOX) && roleSet.contains(RegionRole.OUTPUT)) throw new IllegalArgumentException("逻辑容器跨越互斥职责区域");
        parts.sort(Comparator.comparingInt((BlockEntity e) -> e.getBlockPos().getX()).thenComparingInt(e -> e.getBlockPos().getY()).thenComparingInt(e -> e.getBlockPos().getZ()));
        String key = parts.stream().map(e -> e.getBlockPos().getX() + "," + e.getBlockPos().getY() + "," + e.getBlockPos().getZ() + ":" + BuiltInRegistries.BLOCK.getKey(e.getBlockState().getBlock()) + ";").collect(java.util.stream.Collectors.joining());
        if (!seen.add(key)) return;
        for (var part : parts) if (part instanceof RandomizableContainerBlockEntity loot && loot.getLootTable() != null) throw new IllegalArgumentException("容器战利品未解析");
        var role = roleSet.contains(RegionRole.OUTPUT) ? RegionRole.OUTPUT : roleSet.contains(RegionRole.EMPTY_BOX) ? RegionRole.EMPTY_BOX : RegionRole.MATERIAL;
        if (role == RegionRole.OUTPUT && entity instanceof ShulkerBoxBlockEntity) return;
        var pos = parts.getFirst().getBlockPos().immutable(); state = world.getBlockState(pos);
        net.minecraft.world.Container inventory = entity instanceof ChestBlockEntity ? ChestBlock.getContainer((ChestBlock)state.getBlock(),state,world,pos,true) : (net.minecraft.world.Container)entity;
        if (inventory == null) throw new IllegalArgumentException("容器无法读取");
        if (containers.size() >= 1024) throw new IllegalArgumentException("容器超过 1024 个");
        containers.add(new Container(pos,key,role,List.copyOf(parts),inventory));
    }
    Map<String,Long> delivered() {
        var counts = new TreeMap<String,Long>();
        for (var container : containers) if (container.role == RegionRole.OUTPUT) {
            container.verify(world);
            for (int slot = 0; slot < container.inventory.getContainerSize(); slot++) {
                var stack = container.inventory.getItem(slot);
                if (InventoryItems.isBox(stack)) for (var content : InventoryItems.contents(stack)) {
                    String id = BuiltInRegistries.ITEM.getKey(content.getItem()).toString();
                    if (demand.containsKey(id)) counts.merge(id,Math.multiplyExact((long)content.getCount(),stack.getCount()),Math::addExact);
                }
            }
        }
        return Map.copyOf(counts);
    }
    long outputSlots() {
        long count = 0; for (var container : containers) if (container.role == RegionRole.OUTPUT) {
            container.verify(world); for (int i = 0; i < container.inventory.getContainerSize(); i++) if (container.inventory.getItem(i).isEmpty()) count++;
        } return count;
    }
}
