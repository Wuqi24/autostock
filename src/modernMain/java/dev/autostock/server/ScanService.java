package dev.autostock.server;

import com.google.gson.Gson;
import dev.autostock.AutoStock;
import dev.autostock.core.PlanDraft;
import dev.autostock.core.RegionRole;
import dev.autostock.core.ScanReport;
import dev.autostock.core.StockPlanner;
import dev.autostock.net.DraftResponse;
import dev.autostock.net.ScanRequest;
import dev.autostock.net.ScanResponse;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One bounded, on-demand inventory scan for the server. Never force-loads chunks. */
public final class ScanService {
    private static final Gson GSON = new Gson();
    private static final Comparator<BlockPos> POSITION = Comparator.comparingInt((BlockPos p) -> p.getX())
            .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);
    private Job active;

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (active == null) return;
            try { if (active.advance()) active = null; }
            catch (Exception error) {
                var player = server.getPlayerList().getPlayer(active.owner);
                AutoStock.LOG.warn("Inventory scan failed for {}: {}", active.owner, error.toString());
                if (player != null) reject(player, active.request, error);
                active = null;
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (active != null && active.owner.equals(handler.player.getUUID())) active = null;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> active = null);
    }

    public void cancel(UUID owner, UUID request) {
        if (active != null && active.owner.equals(owner) && active.request.equals(request)) active = null;
    }

    public void start(MinecraftServer server, ServerPlayer player, ScanRequest request, PlanDraft draft) {
        start(server, player, request, draft, false);
    }
    public void start(MinecraftServer server, ServerPlayer player, ScanRequest request, PlanDraft draft, boolean freeze) {
        if (!MinecraftCompat.hasPermission(player, 2) && !MinecraftCompat.isHost(server, player)) {
            reject(player, request.requestId(), "远程库存扫描需要服务器 OP 权限；单人存档所有者可直接使用");
            return;
        }
        if (active != null) {
            reject(player, request.requestId(), "服务器已有扫描进行中，请稍后重试");
            return;
        }
        try { active = new Job(server, player, request.requestId(), draft, freeze); }
        catch (Exception error) { reject(player, request.requestId(), error); }
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.length() > 220 ? "扫描失败，请检查服务端日志" : message;
    }
    private static void reject(ServerPlayer player, UUID request, String message) {
        ServerPlayNetworking.send(player, new DraftResponse(request, false, message));
    }
    private static void reject(ServerPlayer player, UUID request, Exception error) {
        if (error instanceof BindingFailure failure) {
            ServerPlayNetworking.send(player, new DraftResponse(request, false, safeMessage(error), failure.issue,
                    failure.positions.stream().map(p -> new ScanReport.Mark(p.getX(), p.getY(), p.getZ(), RegionRole.OUTPUT)).toList()));
        } else reject(player, request, safeMessage(error));
    }
    private static final class BindingFailure extends IllegalArgumentException {
        final String issue; final List<BlockPos> positions;
        BindingFailure(String issue, List<BlockPos> positions, String message) { super(message); this.issue = issue; this.positions = List.copyOf(positions); }
    }

    private record View(String key, List<BlockPos> positions, List<BlockEntity> entities,
                        List<BlockState> states, Container inventory, EnumSet<RegionRole> roles) { }

    private static final class Job {
        final boolean freeze;
        final MinecraftServer server;
        final ServerLevel world;
        final UUID owner;
        final UUID request;
        final PlanDraft draft;
        final int started;
        final OutputBindings bindings;
        final Iterator<ChunkPos> chunks;
        final Map<String, View> containers = new HashMap<>();
        final Map<InventoryItems.Key, Long> supply = new LinkedHashMap<>();
        final Map<String, Long> stocked = new HashMap<>();
        final Map<String, Set<String>> sources = new HashMap<>();
        final Set<String> outputKeys = new HashSet<>();
        final List<ScanReport.Mark> marks = new ArrayList<>();
        Iterator<View> reading;
        long emptyBoxes;
        int outputSlots, materialContainers, emptyContainers, outputContainers, ignoredOutput, visitedStacks, placedEmptyBoxes;

        Job(MinecraftServer server, ServerPlayer player, UUID request, PlanDraft draft, boolean freeze) throws IOException {
            this.freeze = freeze;
            this.server = server; this.world = MinecraftCompat.world(player); this.owner = player.getUUID();
            this.request = request; this.draft = draft; this.started = server.getTickCount();
            this.bindings = new OutputBindings(server, owner);
            var selectedChunks = new HashSet<ChunkPos>();
            for (var boxes : draft.regions().values()) for (var box : boxes) {
                long chunkCount = ((long) (box.maxX() >> 4) - (box.minX() >> 4) + 1)
                        * ((long) (box.maxZ() >> 4) - (box.minZ() >> 4) + 1);
                if (chunkCount > 512) throw new IllegalArgumentException("选区跨度超过基础版 512 区块上限");
                for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
                    for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                        selectedChunks.add(new ChunkPos(x, z));
                        if (selectedChunks.size() > 512) throw new IllegalArgumentException("三区域合计跨度超过 512 区块");
                    }
                }
            }
            chunks = selectedChunks.stream().sorted(Comparator.comparingInt((ChunkPos p) -> p.x())
                    .thenComparingInt(p -> p.z())).iterator();
        }

        boolean advance() throws IOException {
            var player = server.getPlayerList().getPlayer(owner);
            if (player == null) return true;
            if (MinecraftCompat.world(player) != world) throw new IllegalArgumentException("扫描期间维度发生变化，请重新读取选区");
            if (!MinecraftCompat.hasPermission(player, 2) && !MinecraftCompat.isHost(server, player)) {
                throw new IllegalArgumentException("扫描权限已失效");
            }
            if (server.getTickCount() - started > 1600) throw new IllegalArgumentException("扫描超时，请缩小区域重试");
            // Advance only a requested job: two chunks or two inventories per tick, not whole-region polling.
            if (reading == null) {
                for (int i = 0; i < 2 && chunks.hasNext(); i++) discover(chunks.next());
                if (chunks.hasNext()) return false;
                reading = containers.values().stream().sorted(Comparator.comparing(v -> v.positions().getFirst(), POSITION)).iterator();
                return false;
            }
            for (int i = 0; i < 2 && reading.hasNext(); i++) read(reading.next());
            if (reading.hasNext()) return false;
            if (outputContainers == 0) throw new IllegalArgumentException("备货区没有可绑定的箱子或木桶");
            // Recheck new bindings at commit; an external player may have filled a box mid-scan.
            for (var view : containers.values()) if (outputKeys.contains(view.key())) {
                verify(view);
                if (!bindings.contains(draft.dimension(), view.key()) && !view.inventory().isEmpty()) {
                    throw new BindingFailure("OUTPUT_NOT_EMPTY", view.positions(), "新备货容器必须完全为空：" + view.positions().getFirst().toShortString());
                }
            }
            var groups = new ArrayList<StockPlanner.Supply>();
            int variant = 0;
            for (var entry : supply.entrySet()) {
                var key = entry.getKey();
                groups.add(new StockPlanner.Supply(BuiltInRegistries.ITEM.getKey(key.item()).toString(), "v" + variant++,
                        key.maxCount(), entry.getValue(), !(Block.byItem(key.item()) instanceof ShulkerBoxBlock)));
            }
            var sourceCounts = new HashMap<String, Integer>();
            sources.forEach((item, ids) -> sourceCounts.put(item, ids.size()));
            var plan = StockPlanner.plan(draft.demand(), stocked, groups, sourceCounts);
            var shortageSources=new HashSet<String>();for(var row:plan.rows())if(row.shortage()>0)shortageSources.addAll(sources.getOrDefault(row.item(),Set.of()));
            for(int i=0;i<marks.size();i++){var mark=marks.get(i);boolean missing=mark.role()==RegionRole.MATERIAL&&containers.values().stream().anyMatch(v->shortageSources.contains(v.key())&&v.positions().contains(new BlockPos(mark.x(),mark.y(),mark.z())));if(missing)marks.set(i,new ScanReport.Mark(mark.x(),mark.y(),mark.z(),mark.role(),true));}
            var notes = new ArrayList<String>();
            notes.add("库存为分批读取快照；取货前仍需现场复核。未执行任何取放。");
            notes.add("盒数按实际可用库存与堆叠属性计算；缺料时只是部分装盒计划，补料后需重新扫描。");
            notes.add("已有备货只统计容器内潜影盒内容；散料不抵扣需求。");
            if (placedEmptyBoxes > 0) notes.add("已排除 " + placedEmptyBoxes + " 个地上空盒；无工具方案只领取容器内的空盒物品。");
            if (ignoredOutput > 0) notes.add("备货区忽略了 " + ignoredOutput + " 个非箱子/木桶容器。");
            if (marks.size() >= 256) notes.add("边框最多显示前 256 个容器方块。");
            if (draft.demand().keySet().stream().anyMatch(id -> Block.byItem(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id))) instanceof ShulkerBoxBlock)) {
                notes.add("需求含潜影盒本体：不允许嵌套装盒，后续执行将阻止此类任务。");
            }
            var report = new ScanReport(draft.dimension(), draft.schematicName(), plan, emptyBoxes, outputSlots,
                    materialContainers, emptyContainers, outputContainers, List.copyOf(marks), List.copyOf(notes), server.getTickCount() - started);
            String json = GSON.toJson(report);
            if (json.getBytes(StandardCharsets.UTF_8).length > ScanResponse.MAX_BYTES) throw new IllegalArgumentException("扫描报告过大，请缩小区域");
            bindings.commit(draft.dimension(), outputKeys);
            if (freeze) new FrozenTasks(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).save(owner, request, draft);
            ServerPlayNetworking.send(player, new ScanResponse(request, json, freeze));
            return true;
        }

        LevelChunk loaded(int x, int z) {
            var chunk = world.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (!(chunk instanceof LevelChunk full)) throw new IllegalArgumentException("区块尚未加载，库存未知：" + x + ", " + z);
            return full;
        }

        EnumSet<RegionRole> roles(BlockPos position) {
            var result = EnumSet.noneOf(RegionRole.class);
            for (var entry : draft.regions().entrySet()) for (var box : entry.getValue()) {
                if (position.getX() >= box.minX() && position.getX() <= box.maxX()
                        && position.getY() >= box.minY() && position.getY() <= box.maxY()
                        && position.getZ() >= box.minZ() && position.getZ() <= box.maxZ()) result.add(entry.getKey());
            }
            return result;
        }

        void discover(ChunkPos pos) {
            var chunk = loaded(pos.x(), pos.z());
            if (chunk.getBlockEntities().size() > 4096) throw new IllegalArgumentException("单区块方块实体过多，请缩小扫描范围");
            for (var entity : chunk.getBlockEntities().values()) {
                if (roles(entity.getBlockPos()).isEmpty()) continue;
                if (!(entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity || entity instanceof ShulkerBoxBlockEntity)) continue;
                var positions = new ArrayList<BlockPos>(); positions.add(entity.getBlockPos().immutable());
                var state = chunk.getBlockState(entity.getBlockPos());
                if (entity instanceof ChestBlockEntity && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                    positions.add(entity.getBlockPos().relative(ChestBlock.getConnectedDirection(state)));
                }
                positions.sort(POSITION);
                var entities = new ArrayList<BlockEntity>();
                var states = new ArrayList<BlockState>();
                var roleSet = EnumSet.noneOf(RegionRole.class);
                var key = new StringBuilder();
                for (var part : positions) {
                    var partChunk = loaded(part.getX() >> 4, part.getZ() >> 4);
                    var partEntity = partChunk.getBlockEntities().get(part);
                    if (partEntity == null || (positions.size() == 2 && !(partEntity instanceof ChestBlockEntity))) {
                        throw new IllegalArgumentException("大箱子结构不完整，请检查：" + part.toShortString());
                    }
                    if (partEntity instanceof RandomizableContainerBlockEntity lootable && lootable.getLootTable() != null) {
                        throw new IllegalArgumentException("容器战利品尚未生成，请先手动打开：" + part.toShortString());
                    }
                    var partState = partChunk.getBlockState(part);
                    entities.add(partEntity); states.add(partState); roleSet.addAll(roles(part));
                    key.append(part.getX()).append(',').append(part.getY()).append(',').append(part.getZ())
                            .append(':').append(BuiltInRegistries.BLOCK.getKey(partState.getBlock())).append(';');
                }
                if (roleSet.contains(RegionRole.EMPTY_BOX) && roleSet.contains(RegionRole.OUTPUT)) {
                    throw new BindingFailure("REGION_CONFLICT", positions, "同一逻辑容器跨空盒区和备货区：" + positions.getFirst().toShortString());
                }
                if (containers.containsKey(key.toString())) continue;
                Container inventory = entity instanceof ChestBlockEntity
                        ? ChestBlock.getContainer((ChestBlock) state.getBlock(), state, world, entity.getBlockPos(), true)
                        : (Container) entity;
                if (inventory == null) throw new IllegalArgumentException("容器暂不可读：" + entity.getBlockPos().toShortString());
                if (containers.size() >= 1024) throw new IllegalArgumentException("容器数量超过基础版上限 1024");
                containers.put(key.toString(), new View(key.toString(), List.copyOf(positions), List.copyOf(entities),
                        List.copyOf(states), inventory, roleSet));
            }
        }

        void verify(View view) {
            for (int i = 0; i < view.positions().size(); i++) {
                var pos = view.positions().get(i);
                var chunk = loaded(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk.getBlockEntities().get(pos) != view.entities().get(i)
                        || !chunk.getBlockState(pos).equals(view.states().get(i))) {
                    throw new IllegalArgumentException("扫描期间容器结构改变，请重试：" + pos.toShortString());
                }
            }
        }

        void read(View view) {
            verify(view);
            var roles = view.roles();
            RegionRole role = roles.contains(RegionRole.OUTPUT) ? RegionRole.OUTPUT
                    : roles.contains(RegionRole.EMPTY_BOX) ? RegionRole.EMPTY_BOX : RegionRole.MATERIAL;
            var inventory = view.inventory();
            if (role == RegionRole.OUTPUT) {
                if (!(view.entities().getFirst() instanceof ChestBlockEntity || view.entities().getFirst() instanceof BarrelBlockEntity)) {
                    ignoredOutput++; return;
                }
                if (!bindings.contains(draft.dimension(), view.key()) && !inventory.isEmpty()) {
                    throw new BindingFailure("OUTPUT_NOT_EMPTY", view.positions(), "首次绑定备货容器必须完全为空：" + view.positions().getFirst().toShortString());
                }
                outputKeys.add(view.key()); outputContainers++;
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    var stack = inventory.getItem(slot);
                    if (stack.isEmpty()) outputSlots++;
                    else if (isBox(stack)) for (var content : contents(stack)) {
                        var id = BuiltInRegistries.ITEM.getKey(content.getItem()).toString();
                        if (draft.demand().containsKey(id)) stocked.merge(id, Math.multiplyExact((long) content.getCount(), stack.getCount()), Math::addExact);
                    }
                }
            } else if (role == RegionRole.EMPTY_BOX) {
                emptyContainers++;
                if (view.entities().getFirst() instanceof ShulkerBoxBlockEntity && inventory.isEmpty()) placedEmptyBoxes++;
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    var stack = inventory.getItem(slot);
                    if (isBox(stack) && !contents(stack).iterator().hasNext()) emptyBoxes = Math.addExact(emptyBoxes, stack.getCount());
                }
            } else {
                materialContainers++;
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    var stack = inventory.getItem(slot);
                    if (stack.isEmpty()) continue;
                    if (isBox(stack)) for (var content : contents(stack)) addSupply(content, stack.getCount(), view.key());
                    else addSupply(stack, 1, view.key());
                }
            }
            for (var pos : view.positions()) if (marks.size() < 256) marks.add(new ScanReport.Mark(pos.getX(), pos.getY(), pos.getZ(), role));
        }

        boolean isBox(ItemStack stack) { return InventoryItems.isBox(stack); }
        Iterable<ItemStack> contents(ItemStack stack) {
            if (++visitedStacks > 200_000) throw new IllegalArgumentException("待检查物品过多，请缩小选区");
            return InventoryItems.contents(stack);
        }
        void addSupply(ItemStack stack, int multiplier, String source) {
            if (++visitedStacks > 200_000) throw new IllegalArgumentException("待检查物品过多，请缩小选区");
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!draft.demand().containsKey(id) || stack.isEmpty()) return;
            var key = InventoryItems.key(stack);
            supply.merge(key, Math.multiplyExact((long) stack.getCount(), multiplier), Math::addExact);
            if (supply.size() > 8192) throw new IllegalArgumentException("物品属性变体过多，请缩小选区");
            sources.computeIfAbsent(id, unused -> new HashSet<>()).add(source);
        }
    }
}
