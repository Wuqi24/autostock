package dev.autostock.server;

import dev.autostock.core.PlanDraft;
import dev.autostock.core.RegionBinding;
import dev.autostock.core.RegionRole;
import dev.autostock.net.RegionBound;
import dev.autostock.net.RegionScan;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RegionBindingService {
    private record Job(ServerPlayer player, RegionBinding request, WarehouseIndex index, int started) { }

    private final Map<UUID, Job> jobs = new HashMap<>();
    private final com.google.gson.Gson gson = new com.google.gson.Gson();

    public void register() {
        PayloadTypeRegistry.serverboundPlay().register(RegionScan.ID, RegionScan.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RegionBound.ID, RegionBound.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RegionScan.ID, (payload, context) -> {
            RegionBinding request = null;
            try {
                request = gson.fromJson(payload.json(), RegionBinding.class);
                var player = context.player();
                var world = MinecraftCompat.world(player);
                if (!MinecraftCompat.hasPermission(player, 2) && !MinecraftCompat.isHost(context.server(), player)) {
                    throw new IllegalArgumentException("需要管理员权限");
                }
                if (request == null || request.request() == null
                        || !world.dimension().identifier().toString().equals(request.dimension())
                        || request.regions() == null || request.regions().isEmpty() || request.regions().size() > 3) {
                    throw new IllegalArgumentException("区域请求无效");
                }
                for (var entry : request.regions().entrySet()) {
                    long volume = 0;
                    if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()
                            || entry.getValue().size() > 32) {
                        throw new IllegalArgumentException("区域子选区无效");
                    }
                    for (var box : entry.getValue()) {
                        volume = Math.addExact(volume, box.volume());
                        int range = RuntimeOptions.get(player.getUUID()).scanRange();
                        var position = player.blockPosition();
                        if (!world.getWorldBorder().isWithinBounds(new BlockPos(box.minX(), box.minY(), box.minZ()))
                                || !world.getWorldBorder().isWithinBounds(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))
                                || volume > PlanDraft.MAX_REGION_VOLUME
                                || box.minY() < world.getMinY()
                                || box.maxY() >= world.getMinY() + world.getHeight()
                                || Math.max(Math.abs(box.minX() - position.getX()), Math.abs(box.maxX() - position.getX())) > range
                                || Math.max(Math.abs(box.minZ() - position.getZ()), Math.abs(box.maxZ() - position.getZ())) > range) {
                            throw new IllegalArgumentException("区域超出扫描范围或高度限制");
                        }
                    }
                }
                jobs.put(player.getUUID(), new Job(player, request,
                        new WarehouseIndex(world, request.regions(), Map.of()), context.server().getTickCount()));
            } catch (Exception error) {
                if (request != null) reply(context.player(), request, null, 0, 0, error.getMessage());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var job : List.copyOf(jobs.values())) {
                try {
                    if (server.getPlayerList().getPlayer(job.player().getUUID()) != job.player()) {
                        jobs.remove(job.player().getUUID());
                        continue;
                    }
                    if (server.getTickCount() - job.started() > 1600) throw new IllegalArgumentException("区域扫描超时");
                    if (!job.index().advance()) continue;
                    var counts = new EnumMap<RegionRole, Integer>(RegionRole.class);
                    var keys = new HashSet<String>();
                    long boxes = 0;
                    int slots = 0;
                    var bindings = new OutputBindings(server, job.player().getUUID());
                    var world = MinecraftCompat.world(job.player());
                    for (var container : job.index().containers) {
                        container.verify(world);
                        counts.merge(container.role(), 1, Integer::sum);
                        if (container.role() == RegionRole.OUTPUT) {
                            if (!bindings.contains(job.request().dimension(), container.key()) && !container.inventory().isEmpty()) {
                                throw new IllegalArgumentException("新备货容器必须完全为空");
                            }
                            keys.add(container.key());
                            for (int i = 0; i < container.inventory().getContainerSize(); i++) {
                                if (container.inventory().getItem(i).isEmpty()) slots++;
                            }
                        }
                        if (container.role() == RegionRole.EMPTY_BOX) {
                            for (int i = 0; i < container.inventory().getContainerSize(); i++) {
                                var stack = container.inventory().getItem(i);
                                if (InventoryItems.isBox(stack) && !InventoryItems.contents(stack).iterator().hasNext()) {
                                    boxes += stack.getCount();
                                }
                            }
                        }
                    }
                    for (var role : job.request().regions().keySet()) {
                        if (counts.getOrDefault(role, 0) == 0) {
                            throw new IllegalArgumentException(role.label() + "没有可绑定的容器");
                        }
                    }
                    if (job.request().regions().containsKey(RegionRole.OUTPUT)) {
                        bindings.commit(job.request().dimension(), keys);
                    }
                    reply(job.player(), job.request(), counts, boxes, slots, "");
                    jobs.remove(job.player().getUUID());
                } catch (Exception error) {
                    reply(job.player(), job.request(), null, 0, 0, error.getMessage());
                    jobs.remove(job.player().getUUID());
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> jobs.clear());
    }

    private void reply(ServerPlayer player, RegionBinding request, Map<RegionRole, Integer> counts,
                       long boxes, int slots, String error) {
        if (ServerPlayNetworking.canSend(player, RegionBound.ID)) {
            ServerPlayNetworking.send(player, new RegionBound(gson.toJson(new RegionBinding(request.request(),
                    request.dimension(), request.regions(), counts, boxes, slots, Objects.toString(error, "扫描失败")))));
        }
    }
}
