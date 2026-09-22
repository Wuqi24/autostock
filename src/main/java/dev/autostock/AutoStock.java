package dev.autostock;

import dev.autostock.core.DraftCodec;
import dev.autostock.core.PlanDraft;
import dev.autostock.net.DraftRequest;
import dev.autostock.net.DraftResponse;
import dev.autostock.net.ScanRequest;
import dev.autostock.net.ScanResponse;
import dev.autostock.net.CancelScan;
import dev.autostock.server.ScanService;
import dev.autostock.server.MinecraftCompat;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AutoStock implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("autostock");
    // Main-server-thread state, cleared on disconnect and server shutdown.
    private final Map<UUID, Long> lastRequestNanos = new HashMap<>();
    private final Map<UUID, PlanDraft> drafts = new HashMap<>();

    @Override public void onInitialize() {
        dev.autostock.server.TaskAttachments.register();dev.autostock.server.RuntimeOptions.register();new dev.autostock.server.RegionBindingService().register();
        PayloadTypeRegistry.playC2S().register(dev.autostock.net.InventoryWatch.ID, dev.autostock.net.InventoryWatch.CODEC);
        PayloadTypeRegistry.playS2C().register(dev.autostock.net.BotInventory.ID, dev.autostock.net.BotInventory.CODEC);
        // Payload codecs are needed by clients even when the optional fake-player backend is absent.
        PayloadTypeRegistry.playC2S().register(dev.autostock.net.TaskRequest.ID, dev.autostock.net.TaskRequest.CODEC);
        PayloadTypeRegistry.playS2C().register(dev.autostock.net.TaskStatus.ID, dev.autostock.net.TaskStatus.CODEC);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("carpet")) {
            new dev.autostock.server.TaskService().register();
            new dev.autostock.server.InventorySync().register();
            //? if >1.21 {
            dev.autostock.server.QuickShulkerDiagnostics.register();
            //?}
        }
        PayloadTypeRegistry.playC2S().register(DraftRequest.ID, DraftRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(dev.autostock.net.FreezeRequest.ID, dev.autostock.net.FreezeRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(dev.autostock.net.LoadFrozenRequest.ID, dev.autostock.net.LoadFrozenRequest.CODEC);
        PayloadTypeRegistry.playS2C().register(dev.autostock.net.FrozenResponse.ID, dev.autostock.net.FrozenResponse.CODEC);
        PayloadTypeRegistry.playS2C().register(DraftResponse.ID, DraftResponse.CODEC);
        PayloadTypeRegistry.playC2S().register(ScanRequest.ID, ScanRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(CancelScan.ID, CancelScan.CODEC);
        PayloadTypeRegistry.playS2C().register(ScanResponse.ID, ScanResponse.CODEC);
        var scanning = new ScanService();
        scanning.register();
        ServerPlayNetworking.registerGlobalReceiver(dev.autostock.net.LoadFrozenRequest.ID, (payload, context) -> {
            try {
                var player = context.player();
                long now = System.nanoTime(); var previous = lastRequestNanos.put(player.getUuid(), now);
                if (previous != null && now - previous < 1_000_000_000L) throw new IllegalArgumentException("读取过快，请稍后重试");
                var draft = new dev.autostock.server.FrozenTasks(context.server().getSavePath(net.minecraft.util.WorldSavePath.ROOT)).load(player.getUuid(), payload.taskId());
                String json = DraftCodec.encode(draft); validate(player, json);
                ServerPlayNetworking.send(player, new dev.autostock.net.FrozenResponse(payload.requestId(), payload.taskId(), json));
            } catch (java.io.IOException | RuntimeException error) {
                String detail = error instanceof java.io.IOException ? "冻结需求不存在或损坏，请核对任务 ID 与所属存档" : error.getMessage();
                ServerPlayNetworking.send(context.player(), new DraftResponse(payload.requestId(), false, detail == null || detail.length() > 180 ? "读取冻结需求失败" : detail));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(dev.autostock.net.FreezeRequest.ID, (payload, context) -> {
            try {
                var draft = validate(context.player(), payload.json());
                scanning.start(context.server(), context.player(), new ScanRequest(payload.requestId(), payload.json()), draft, true);
            } catch (RuntimeException error) {
                String detail = error.getMessage() == null || error.getMessage().length() > 180 ? "冻结需求无效" : error.getMessage();
                ServerPlayNetworking.send(context.player(), new DraftResponse(payload.requestId(), false, detail));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(CancelScan.ID,
                (payload, context) -> scanning.cancel(context.player().getUuid(), payload.requestId()));
        ServerPlayNetworking.registerGlobalReceiver(ScanRequest.ID, (payload, context) -> {
            try {
                var draft = validate(context.player(), payload.json());
                scanning.start(context.server(), context.player(), payload, draft);
            } catch (RuntimeException error) {
                String message = error instanceof IllegalArgumentException && error.getMessage() != null && error.getMessage().length() <= 180
                        ? error.getMessage() : "选区或需求无效，请先执行草稿校验";
                ServerPlayNetworking.send(context.player(), new DraftResponse(payload.requestId(), false, message));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(DraftRequest.ID, (payload, context) -> {
            var player = context.player();
            var owner = player.getUuid();
            long now = System.nanoTime();
            Long previous = lastRequestNanos.get(owner);
            if (previous != null && now - previous < 1_000_000_000L) {
                reply(player, payload, false, "提交过快，请稍后重试");
                return;
            }
            lastRequestNanos.put(owner, now);
            try {
                var draft = validate(player, payload.json());
                drafts.put(owner, draft);
                reply(player, payload, true, "草稿校验通过 · " + draft.demand().size() + " 种 / "
                        + draft.totalItems() + " 件 · " + DraftCodec.hash(draft).substring(0, 12)
                        + "（尚未检查库存或绑定容器）");
            } catch (RuntimeException error) {
                LOG.debug("Rejected draft from {}", owner, error);
                String detail = error instanceof IllegalArgumentException ? error.getMessage() : "计划格式无效";
                if (detail == null || detail.length() > 180) detail = "计划格式或数值无效";
                reply(player, payload, false, detail);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            lastRequestNanos.remove(handler.player.getUuid());
            drafts.remove(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            lastRequestNanos.clear();
            drafts.clear();
        });
        LOG.info("AutoStock initialized: protocol {}, frozen-demand fake-player task executor", PlanDraft.PROTOCOL);
    }

    private static PlanDraft validate(net.minecraft.server.network.ServerPlayerEntity player, String json) {
        var draft = DraftCodec.decode(json);
        var world = MinecraftCompat.world(player);
        if (!world.getRegistryKey().getValue().toString().equals(draft.dimension())) {
            throw new IllegalArgumentException("选区维度与玩家当前维度不同，请重新读取选区");
        }
        for (var boxes : draft.regions().values()) for (var box : boxes) {
            int range=dev.autostock.server.RuntimeOptions.get(player.getUuid()).scanRange();var pos=player.getBlockPos();
            if(Math.max(Math.abs(box.minX()-pos.getX()),Math.abs(box.maxX()-pos.getX()))>range||Math.max(Math.abs(box.minZ()-pos.getZ()),Math.abs(box.maxZ()-pos.getZ()))>range)throw new IllegalArgumentException("选区超出扫描范围："+range+" 格");
            if (box.minY() < world.getBottomY() || box.maxY() >= world.getBottomY() + world.getHeight()
                    || !world.getWorldBorder().contains(new BlockPos(box.minX(), box.minY(), box.minZ()))
                    || !world.getWorldBorder().contains(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
                throw new IllegalArgumentException("选区超出当前维度高度或世界边界");
            }
        }
        for (String item : draft.demand().keySet()) {
            var id = Identifier.of(item);
            if (!Registries.ITEM.containsId(id) || Registries.ITEM.get(id) == Items.AIR) {
                throw new IllegalArgumentException("清单含服务端不支持的物品");
            }
            dev.autostock.server.BuildingMaterials.requireSupported(Registries.ITEM.get(id));
        }
        return draft;
    }

    private static void reply(net.minecraft.server.network.ServerPlayerEntity player, DraftRequest request,
                              boolean accepted, String text) {
        ServerPlayNetworking.send(player, new DraftResponse(request.requestId(), accepted, text));
    }
}

