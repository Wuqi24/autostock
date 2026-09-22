package dev.autostock.client;

import dev.autostock.core.RegionRole;
import dev.autostock.net.DraftResponse;
import dev.autostock.net.ScanResponse;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class AutoStockClient implements ClientModInitializer {
    private final ClientDraft draft = new ClientDraft();
    static ClientDraft activeDraft;

    @Override public void onInitializeClient() {
        activeDraft=draft;ClientPlayNetworking.registerGlobalReceiver(dev.autostock.net.RegionBound.ID,(payload,context)->draft.receiveBinding(payload));
        var loader = FabricLoader.getInstance();
        for (String id : new String[]{"litematica", "malilib"}) {
            if (!loader.isModLoaded(id)) throw new IllegalStateException("自动备货客户端需要安装 " + id);
        }
        ClientPlayNetworking.registerGlobalReceiver(dev.autostock.net.BotInventory.ID, (payload, context) -> InventoryView.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(DraftResponse.ID, (payload, context) -> draft.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(ScanResponse.ID, (payload, context) -> draft.receiveScan(payload));
        ClientPlayNetworking.registerGlobalReceiver(dev.autostock.net.FrozenResponse.ID, (payload, context) -> draft.receiveFrozen(payload));
        ClientPlayNetworking.registerGlobalReceiver(dev.autostock.net.TaskStatus.ID, (payload, context) -> draft.receiveTask(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {draft.discard();InventoryView.clear();});
        ClientTickEvents.END_CLIENT_TICK.register(client -> {draft.tick();RegionSetupScreen.tickOverlay();InventoryView.tick(client);});
        ContainerOverlay.register(draft);
        LitematicaResult.register(draft);
        ClientKeys.register(draft);
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((context, counter) -> {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client.world != null && client.currentScreen == null)
                RegionSetupScreen.renderOverlay(context,draft);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("autostock")
                        .executes(context -> {
                            // Defer opening until chat has closed after command execution.
                            context.getSource().getClient().send(() -> context.getSource().getClient().setScreen(new DraftScreen(draft)));
                            return 1;
                        })
                        .then(literal("material").executes(context -> { draft.perform(() -> draft.capture(RegionRole.MATERIAL)); return 1; }))
                        .then(literal("empty").executes(context -> { draft.perform(() -> draft.capture(RegionRole.EMPTY_BOX)); return 1; }))
                        .then(literal("output").executes(context -> { draft.perform(() -> draft.capture(RegionRole.OUTPUT)); return 1; }))
                        .then(literal("import").executes(context -> { draft.perform(draft::importTotal); return 1; }))
                        .then(literal("validate").executes(context -> { draft.perform(draft::submit); return 1; }))
                        .then(literal("scan").executes(context -> { draft.perform(draft::scan); return 1; }))
                        .then(literal("clear").executes(context -> { draft.perform(draft::clear); return 1; }))
                        .then(literal("load").then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument("task", net.minecraft.command.argument.UuidArgumentType.uuid())
                                .executes(context -> { draft.perform(() -> draft.loadFrozen(context.getArgument("task", java.util.UUID.class))); return 1; })))
        ));
    }
}



