package dev.autostock.server;

import dev.autostock.core.PlanDraft;
import dev.autostock.core.RegionRole;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import java.util.*;

/** Fresh, bounded container index built at task start/recovery. Quantities are always read again before transfer. */
final class WarehouseIndex {
    record Container(BlockPos position, String key, RegionRole role, List<BlockEntity> parts, Inventory inventory) {
        void verify(ServerWorld world) {
            for (var part : parts) if (!world.isChunkLoaded(part.getPos()) || world.getBlockEntity(part.getPos()) != part)
                throw new IllegalArgumentException("容器被替换或区块卸载：" + position.toShortString());
            if (parts.size() == 1 && world.getBlockState(position).getBlock() instanceof ChestBlock
                    && world.getBlockState(position).get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
                throw new IllegalArgumentException("单箱已变为大箱子：" + position.toShortString());
            if (parts.size() == 2) {
                var state = world.getBlockState(position);
                if (!(state.getBlock() instanceof ChestBlock) || state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE
                        || !parts.stream().anyMatch(part -> part.getPos().equals(position.offset(ChestBlock.getFacing(state)))))
                    throw new IllegalArgumentException("大箱子结构改变：" + position.toShortString());
            }
        }
    }
    final ServerWorld world; private final Map<RegionRole,List<dev.autostock.core.RegionBox>> regions;private final Map<String,Long> demand;
    final List<Container> containers = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private final Iterator<ChunkPos> chunks;
    WarehouseIndex(ServerWorld world, PlanDraft draft) {
        this(world,draft.regions(),draft.demand());
    }
    WarehouseIndex(ServerWorld world,Map<RegionRole,List<dev.autostock.core.RegionBox>> regions,Map<String,Long> demand){
        this.world=world;this.regions=regions;this.demand=demand;
        var chunks = new TreeSet<ChunkPos>(Comparator.comparingInt((ChunkPos p) -> p.x).thenComparingInt(p -> p.z));
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
            var chunk = chunks.next(); var loaded = world.getChunkManager().getChunk(chunk.x,chunk.z,ChunkStatus.FULL,false);
            if (!(loaded instanceof WorldChunk full)) throw new IllegalArgumentException("区块未加载，库存未知：" + chunk);
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
        var roleSet = roles(entity.getPos()); if (roleSet.isEmpty()) return;
        if (!(entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity || entity instanceof ShulkerBoxBlockEntity)) return;
        var parts = new ArrayList<BlockEntity>(); parts.add(entity); var state = world.getBlockState(entity.getPos());
        if (entity instanceof ChestBlockEntity && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            var other = entity.getPos().offset(ChestBlock.getFacing(state));
            if (!world.isChunkLoaded(other) || !(world.getBlockEntity(other) instanceof ChestBlockEntity)) throw new IllegalArgumentException("大箱子结构未知");
            parts.add(world.getBlockEntity(other)); roleSet.addAll(roles(other));
        }
        if (roleSet.contains(RegionRole.EMPTY_BOX) && roleSet.contains(RegionRole.OUTPUT)) throw new IllegalArgumentException("逻辑容器跨越互斥职责区域");
        parts.sort(Comparator.comparingInt((BlockEntity e) -> e.getPos().getX()).thenComparingInt(e -> e.getPos().getY()).thenComparingInt(e -> e.getPos().getZ()));
        String key = parts.stream().map(e -> e.getPos().getX() + "," + e.getPos().getY() + "," + e.getPos().getZ() + ":" + Registries.BLOCK.getId(e.getCachedState().getBlock()) + ";").collect(java.util.stream.Collectors.joining());
        if (!seen.add(key)) return;
        for (var part : parts) if (part instanceof LootableContainerBlockEntity loot && loot.getLootTable() != null) throw new IllegalArgumentException("容器战利品未解析");
        var role = roleSet.contains(RegionRole.OUTPUT) ? RegionRole.OUTPUT : roleSet.contains(RegionRole.EMPTY_BOX) ? RegionRole.EMPTY_BOX : RegionRole.MATERIAL;
        if (role == RegionRole.OUTPUT && entity instanceof ShulkerBoxBlockEntity) return;
        var pos = parts.getFirst().getPos().toImmutable(); state = world.getBlockState(pos);
        Inventory inventory = entity instanceof ChestBlockEntity ? ChestBlock.getInventory((ChestBlock)state.getBlock(),state,world,pos,true) : (Inventory)entity;
        if (inventory == null) throw new IllegalArgumentException("容器无法读取");
        if (containers.size() >= 1024) throw new IllegalArgumentException("容器超过 1024 个");
        containers.add(new Container(pos,key,role,List.copyOf(parts),inventory));
    }
    Map<String,Long> delivered() {
        var counts = new TreeMap<String,Long>();
        for (var container : containers) if (container.role == RegionRole.OUTPUT) {
            container.verify(world);
            for (int slot = 0; slot < container.inventory.size(); slot++) {
                var stack = container.inventory.getStack(slot);
                if (InventoryItems.isBox(stack)) for (var content : InventoryItems.contents(stack)) {
                    String id = Registries.ITEM.getId(content.getItem()).toString();
                    if (demand.containsKey(id)) counts.merge(id,Math.multiplyExact((long)content.getCount(),stack.getCount()),Math::addExact);
                }
            }
        }
        return Map.copyOf(counts);
    }
    long outputSlots() {
        long count = 0; for (var container : containers) if (container.role == RegionRole.OUTPUT) {
            container.verify(world); for (int i = 0; i < container.inventory.size(); i++) if (container.inventory.getStack(i).isEmpty()) count++;
        } return count;
    }
}
