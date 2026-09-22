package dev.autostock.client;

import dev.autostock.core.RegionBox;
import dev.autostock.core.RegionRole;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.List;
import java.util.Map;

public class RegionGuiGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client->ClientSettings.resetDefaults());
        try (var world = context.worldBuilder().create()) {
            context.waitForScreen(null);
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_P);
            context.waitForScreen(DraftScreen.class);
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_P);
            context.waitForScreen(null);
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_N);
            context.waitForScreen(null);
            context.waitFor(client->RegionSetupScreen.active(),100);
            var beforeMove=context.computeOnClient(client->client.player.getPos());
            context.getInput().holdKey(org.lwjgl.glfw.GLFW.GLFW_KEY_W);context.waitTicks(12);
            context.getInput().releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_W);
            context.runOnClient(client->{if(client.player.getPos().squaredDistanceTo(beforeMove)<.1)throw new AssertionError("选区 HUD 阻止玩家移动");RegionSetupScreen.select(RegionRole.MATERIAL);});
            context.getInput().holdKey(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT);context.getInput().scroll(1);
            context.runOnClient(client->{if(RegionSetupScreen.currentRole()!=RegionRole.OUTPUT)throw new AssertionError("向前滚没有选上一个区域");});
            context.getInput().scroll(-1);context.getInput().releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT);
            context.runOnClient(client->{if(RegionSetupScreen.currentRole()!=RegionRole.MATERIAL)throw new AssertionError("向后滚没有选下一个区域");});
            ClientDraft draft = context.computeOnClient(client -> {
                var initializer = net.fabricmc.loader.api.FabricLoader.getInstance().getEntrypoints("client", net.fabricmc.api.ClientModInitializer.class)
                        .stream().filter(AutoStockClient.class::isInstance).findFirst().orElseThrow();
                var field = AutoStockClient.class.getDeclaredField("draft"); field.setAccessible(true);
                return (ClientDraft) field.get(initializer);
            });
            var base = context.computeOnClient(client -> client.player.getBlockPos().add(-3, 0, 4));
            world.getServer().runOnServer(server -> {
                for (int i = 0; i < 3; i++) server.getOverworld().setBlockState(base.add(i * 3, 0, 0), net.minecraft.block.Blocks.BARREL.getDefaultState());
                ((net.minecraft.block.entity.BarrelBlockEntity) server.getOverworld().getBlockEntity(base)).setStack(0, new ItemStack(Items.STONE, 64));
            });
            context.runOnClient(client -> { client.player.setYaw(0); client.player.setPitch(5); });
            context.runOnClient(client->RegionSetupScreen.open(draft)); context.waitTicks(5);
            context.takeScreenshot("autostock-region-unset");
            context.runOnClient(client->{
                var manager=fi.dy.masa.litematica.data.DataManager.getSelectionManager();
                if(manager.getSelectionMode()!=fi.dy.masa.litematica.selection.SelectionMode.SIMPLE)manager.switchSelectionMode();
                var selection=manager.getSimpleSelection();var box=selection.getAllSubRegionBoxes().getFirst();box.setPos1(base);box.setPos2(base);
                RegionSetupScreen.select(RegionRole.MATERIAL);
            });
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_R);
            context.waitFor(client->draft.bindingLabel(RegionRole.MATERIAL).equals("已绑定"),400);
            context.runOnClient(client->{if(RegionSetupScreen.currentRole()!=RegionRole.MATERIAL||!draft.saved(RegionRole.MATERIAL))throw new AssertionError("R 没有保存当前区域");});
            context.takeScreenshot("autostock-r-save-selected-region");
            context.runOnClient(client -> {
                var field = ClientDraft.class.getDeclaredField("regions"); field.setAccessible(true);
                @SuppressWarnings("unchecked") var regions = (Map<RegionRole, List<RegionBox>>) field.get(draft);
                for (var role : RegionRole.values()) {
                    var p = base.add(role.ordinal() * 3, 0, 0);
                    regions.put(role, List.of(new RegionBox(p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ())));
                }
            });
            context.waitTick(); context.takeScreenshot("autostock-region-saved");
            var regionBackup=context.computeOnClient(client->draft.regions());
            context.runOnClient(client->{
                var f=ClientDraft.class.getDeclaredField("regions");f.setAccessible(true);
                @SuppressWarnings("unchecked") var regions=(Map<RegionRole,List<RegionBox>>)f.get(draft);
                regions.remove(RegionRole.EMPTY_BOX);regions.remove(RegionRole.OUTPUT);
                draft.scanRegions();
            });
            context.waitFor(client->draft.regionBinding!=null,400);
            world.getServer().runOnServer(server->{((net.minecraft.block.entity.BarrelBlockEntity)server.getOverworld().getBlockEntity(base)).clear();server.getOverworld().setBlockState(base,net.minecraft.block.Blocks.AIR.getDefaultState());});
            context.runOnClient(client->draft.scanRegions());
            context.waitFor(client->draft.bindingLabel(RegionRole.MATERIAL).equals("绑定失败"),400);
            context.runOnClient(client->{if(draft.regionBinding!=null)throw new AssertionError("扫描失败仍保留旧绑定");});
            world.getServer().runOnServer(server->{server.getOverworld().setBlockState(base,net.minecraft.block.Blocks.BARREL.getDefaultState());((net.minecraft.block.entity.BarrelBlockEntity)server.getOverworld().getBlockEntity(base)).setStack(0,new ItemStack(Items.STONE,64));});
            context.runOnClient(client->draft.scanRegions());
            context.waitFor(client->draft.regionBinding!=null,400);
            context.runOnClient(client->{
                if(!draft.materials().isEmpty()||!draft.bindingLabel(RegionRole.MATERIAL).equals("已绑定"))throw new AssertionError("无材料清单时独立绑定失败");
                var f=ClientDraft.class.getDeclaredField("regions");f.setAccessible(true);
                @SuppressWarnings("unchecked") var regions=(Map<RegionRole,List<RegionBox>>)f.get(draft);
                regions.clear();regions.putAll(regionBackup);
            });
            context.runOnClient(client -> {
                var field = ClientDraft.class.getDeclaredField("regions"); field.setAccessible(true);
                @SuppressWarnings("unchecked") var regions = (Map<RegionRole, List<RegionBox>>) field.get(draft);
                regions.put(RegionRole.OUTPUT, regions.get(RegionRole.EMPTY_BOX));
                if (!draft.hasConflict() || draft.regionsReady()) throw new AssertionError("区域冲突仍可继续");
            });
            context.waitTick(); context.takeScreenshot("autostock-region-conflict");
            context.runOnClient(client -> {
                var list = new MaterialListBase() {
                    public String getName() { return "实际表格倍率测试"; }
                    public String getTitle() { return getName(); }
                    public void reCreateMaterialList() { }
                };
                list.setMaterialListEntries(List.of(new MaterialListEntry(new ItemStack(Items.STONE), 64, 64, 0, 0),
                        new MaterialListEntry(new ItemStack(Items.DIRT), 10, 10, 0, 0)));
                list.setMultiplier(3); list.ignoreEntry(list.getMaterialsAll().get(1));
                client.setScreen(new GuiMaterialList(list));
            });
            context.waitTicks(3);
            context.takeScreenshot("litematica-bottom-stock-button");
            context.runOnClient(client->{
                var gui=(GuiMaterialList)client.currentScreen;
                var field=fi.dy.masa.malilib.gui.GuiBase.class.getDeclaredField("buttons");field.setAccessible(true);
                var label=fi.dy.masa.malilib.gui.button.ButtonBase.class.getDeclaredField("displayString");label.setAccessible(true);
                @SuppressWarnings("unchecked") var buttons=(java.util.List<fi.dy.masa.malilib.gui.button.ButtonBase>)field.get(gui);
                var stock=buttons.stream().filter(b->{try{return label.get(b).equals("假人备货");}catch(Exception e){throw new AssertionError(e);}}).findFirst().orElseThrow();
                if(stock.getY()!=gui.getScreenHeight()-36||stock.getHeight()!=20)throw new AssertionError("假人备货未对齐底栏");
                var regionsField=ClientDraft.class.getDeclaredField("regions");regionsField.setAccessible(true);
                @SuppressWarnings("unchecked") var regions=(Map<RegionRole,List<RegionBox>>)regionsField.get(draft);
                var saved=regions.remove(RegionRole.MATERIAL);
                gui.mouseClicked(stock.getX()+2,stock.getY()+2,0);
                if(client.currentScreen!=gui||draft.frozen())throw new AssertionError("缺少区域时仍跳转或冻结");
                regions.put(RegionRole.MATERIAL,saved);
            });
            context.setScreen(() -> new DraftScreen(draft));
            context.runOnClient(client -> {
                var snapshot = LitematicaResult.latest();
                if (snapshot.multiplier() != 3 || !snapshot.counts().equals(Map.of("minecraft:stone", 192L)))
                    throw new AssertionError("未读取真实过滤表格和倍率：" + snapshot);
                draft.importTotal();
                var field = ClientDraft.class.getDeclaredField("regions"); field.setAccessible(true);
                @SuppressWarnings("unchecked") var regions = (Map<RegionRole, List<RegionBox>>) field.get(draft);
                var p = base.add(6,0,0);
                regions.put(RegionRole.OUTPUT, List.of(new RegionBox(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ())));
            });
            context.waitTick(); context.takeScreenshot("autostock-config");
            world.getServer().runOnServer(server -> ((net.minecraft.block.entity.BarrelBlockEntity)
                    server.getOverworld().getBlockEntity(base.add(6,0,0))).setStack(0, new ItemStack(Items.STONE)));
            context.runOnClient(client -> draft.scan());
            context.waitFor(client -> draft.bindingFailed(), 400);
            context.runOnClient(client->RegionSetupScreen.open(draft));
            context.getInput().holdKey(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT);
            context.getInput().scroll(1); context.getInput().scroll(1);
            context.getInput().releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT);
            context.runOnClient(client->RegionSetupScreen.select(RegionRole.OUTPUT));context.waitTick(); context.takeScreenshot("autostock-region-binding-failed");
            world.getServer().runOnServer(server -> ((net.minecraft.block.entity.BarrelBlockEntity)
                    server.getOverworld().getBlockEntity(base.add(6,0,0))).setStack(0, ItemStack.EMPTY));
            // Exercise the actual client/server scan and freeze payload, not just the in-memory record.
            context.runOnClient(client -> draft.freezeDemand());
            context.waitFor(client -> draft.frozen(), 400);
            context.runOnClient(client->{for(var role:RegionRole.values())if(!draft.bindingLabel(role).equals("已绑定"))throw new AssertionError("服务端确认后没有显示已绑定");RegionSetupScreen.open(draft);});
            context.waitTick();context.takeScreenshot("autostock-bound-hud");
            context.runOnClient(client -> {
                draft.importTotal();
                if (draft.proposal().demand().get("minecraft:stone") != 192L || draft.status().startsWith("操作未完成")) throw new AssertionError("冻结需求被修改或仍显示笼统失败");
                var id = draft.frozenId(); draft.clear(); draft.loadFrozen(id);
            });
            context.waitFor(client -> draft.frozen(), 400);
            context.runOnClient(client -> { if (draft.report() != null || draft.totalItems() != 192) throw new AssertionError("加载需求复用了旧世界报告"); });
            world.getServer().runOnServer(server -> {
                var material=(net.minecraft.block.entity.BarrelBlockEntity)server.getOverworld().getBlockEntity(base);
                for(int slot=0;slot<3;slot++)material.setStack(slot,new ItemStack(Items.STONE,64));
                ((net.minecraft.block.entity.BarrelBlockEntity)server.getOverworld().getBlockEntity(base.add(3,0,0))).setStack(0,new ItemStack(Items.SHULKER_BOX));
            });
            context.setScreen(() -> null);
            context.getInput().holdKey(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL);
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_R);
            context.getInput().releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL);
            context.waitFor(client -> draft.taskStatus()!=null && draft.taskStatus().state().equals("RUNNING"),400);
            context.waitTicks(6);
            context.runOnClient(client -> draft.taskToggle());
            context.waitFor(client -> draft.taskStatus().state().equals("PAUSED"),400);
            context.setScreen(() -> new DraftScreen(draft));context.runOnClient(client->((DraftScreen)client.currentScreen).selectTab(3));context.waitTicks(3);context.takeScreenshot("autostock-task-paused");
            context.setScreen(()->new BotInventoryScreen(new DraftScreen(draft)));
            context.waitFor(client->InventoryView.snapshot!=null&&InventoryView.snapshot.state().equals("ONLINE"),1000);
            world.getServer().runOnServer(server->{
                var fake=server.getPlayerManager().getPlayerList().stream().filter(carpet.patches.EntityPlayerMPFake.class::isInstance).findFirst().orElseThrow();
                if(!fake.getInventory().getStack(35).isEmpty())throw new AssertionError("测试槽位被占用");
                var named=new ItemStack(Items.DIRT,7);named.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,net.minecraft.text.Text.literal("同步测试材料"));fake.getInventory().setStack(35,named);
            });
            context.waitFor(client->InventoryView.snapshot.slots().get(35).getCount()==7,1000);
            context.runOnClient(client->{
                var data=InventoryView.snapshot;if(!data.slots().get(35).getName().getString().equals("同步测试材料"))throw new AssertionError("背包同步丢失组件");
                var stale=new dev.autostock.net.BotInventory(java.util.UUID.randomUUID(),data.bot(),data.revision()+100,"MISSING",data.dimension(),data.position(),0,data.slots(),data.categories());
                InventoryView.receive(stale);if(InventoryView.snapshot!=data)throw new AssertionError("旧订阅数据覆盖当前背包");
            });
            context.takeScreenshot("autostock-live-inventory");
            world.getServer().runOnServer(server->{
                var fake=server.getPlayerManager().getPlayerList().stream().filter(carpet.patches.EntityPlayerMPFake.class::isInstance).findFirst().orElseThrow();fake.getInventory().setStack(35,ItemStack.EMPTY);
            });
            context.waitFor(client->InventoryView.snapshot.slots().get(35).isEmpty(),1000);
            context.setScreen(()->new DraftScreen(draft));context.waitTicks(2);
            context.runOnClient(client->{if(InventoryView.session!=null||InventoryView.snapshot!=null)throw new AssertionError("离开详情页未退订并清理缓存");((DraftScreen)client.currentScreen).selectTab(3);});
            context.waitTicks(6);context.runOnClient(client -> draft.taskToggle());
            context.waitFor(client -> draft.taskStatus().state().equals("COMPLETED") || draft.taskStatus().state().equals("ERROR"),1200);
            context.runOnClient(client -> {
                if(!draft.bindingLabel(RegionRole.OUTPUT).equals("已绑定"))throw new AssertionError("恢复任务校验后 HUD 没有同步绑定状态："+draft.taskStatus());
                if(!draft.taskStatus().state().equals("COMPLETED") || draft.taskStatus().delivered()!=192 || draft.taskStatus().inTransit()!=0)
                    throw new AssertionError("完整客户端任务未正确交付："+draft.taskStatus());
            });
            context.takeScreenshot("autostock-task-completed");
            context.runOnClient(client->{
                var screen=(DraftScreen)client.currentScreen;screen.navigate(0);
                var nf=PreviewScreen.class.getDeclaredField("notification");nf.setAccessible(true);
                var input=(fi.dy.masa.malilib.gui.GuiTextFieldGeneric)nf.get(screen);
                screen.mouseClicked(input.getXWrapper()+4,input.getYWrapper()+4,0);
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_END,0,0);
                for(int k=0;k<256;k++)screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE,0,0);
                screen.charTyped('X',0);
                if(!ClientSettings.get().completionMessage.equals("X"))throw new AssertionError("通知输入栏未直接保存："+input.getTextWrapper()+" / "+ClientSettings.get().completionMessage);
                ClientSettings.get().completionMessage="狗修金撒码，材料准备好了";ClientSettings.get().save();
                screen.navigate(0);screen.mouseClicked(15,Math.max(64,(int)(screen.height*.195))-15,0);screen.charTyped('Q',0);screen.tick();
                var f=PreviewScreen.class.getDeclaredField("lines");f.setAccessible(true);
                if(((java.util.List<?>)f.get(screen)).stream().anyMatch(x->x.toString().contains("自定义通知内容")))throw new AssertionError("搜索未过滤配置行");
                screen.navigate(4);
                if(!((java.util.List<?>)f.get(screen)).stream().anyMatch(x->x.toString().contains("QuickShulker")))throw new AssertionError("切页后搜索未保留");
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE,0,0);screen.tick();
            });
            for(int tab=0;tab<7;tab++) {final int chosen=tab;context.runOnClient(client->((DraftScreen)client.currentScreen).selectTab(chosen));context.waitTick();context.takeScreenshot("autostock-page-"+tab);}
            context.runOnClient(client->{var screen=(DraftScreen)client.currentScreen;screen.navigate(0);screen.mouseClicked(15,Math.max(64,(int)(screen.height*.195))-15,0);screen.tick();});
            context.waitTick();context.takeScreenshot("autostock-native-search-collapsed");
            context.runOnClient(client->{var screen=(DraftScreen)client.currentScreen;screen.selectTab(0);screen.mouseScrolled(100,100,0,-5);});context.waitTick();context.takeScreenshot("autostock-setup-regions");
            context.runOnClient(client->((DraftScreen)client.currentScreen).mouseScrolled(100,100,0,-6));context.waitTick();context.takeScreenshot("autostock-setup-overlap");
            context.runOnClient(client->((DraftScreen)client.currentScreen).navigate(4));
            context.waitTicks(2);
            context.runOnClient(client->click(client.currentScreen,"放置"));
            context.waitFor(client->InventoryView.snapshot!=null&&InventoryView.snapshot.state().equals("ONLINE"),400);
            context.runOnClient(client->click(client.currentScreen,"召回"));
            context.waitFor(client->InventoryView.snapshot!=null&&InventoryView.snapshot.state().equals("OFFLINE"),200);
            context.runOnClient(client->{if(!draft.taskStatus().path().isEmpty()||draft.taskStatus().target()!=null)throw new AssertionError("召回仍残留路径或目标");});
            context.takeScreenshot("autostock-one-click-recall");
            context.setScreen(()->new DraftScreen(draft));
            context.runOnClient(client->{
                var screen=(DraftScreen)client.currentScreen;screen.navigate(0);
                var fields=PreviewScreen.class.getDeclaredField("inputs");fields.setAccessible(true);
                var input=((java.util.List<fi.dy.masa.malilib.gui.GuiTextFieldGeneric>)fields.get(screen)).stream().filter(f->f.getTextWrapper().equals("32")).findFirst().orElseThrow();
                int x=input.getXWrapper(),y=input.getYWrapper();
                screen.mouseClicked(x+4,y+4,0);screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_END,0,0);
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE,0,0);screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE,0,0);
                screen.charTyped('4',0);screen.charTyped('8',0);
                if(ClientSettings.get().scanRange!=48)throw new AssertionError("数值输入未应用");
                click(screen,"≡");screen.mouseClicked(x+input.getWidthWrapper()-2,y+5,0);screen.mouseReleased(x+input.getWidthWrapper()-2,y+5,0);
                if(ClientSettings.get().scanRange<=48)throw new AssertionError("原生滑块未更新数值");
                screen.mouseClicked(x+Math.max(90,(int)(screen.width*.265))+6,y+5,0);
                if(ClientSettings.get().scanRange!=32)throw new AssertionError("单项重置未恢复默认值");
                ClientSettings.get().scanRange=40;ClientSettings.get().save();screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);
                if(ClientSettings.get().scanRange!=40)throw new AssertionError("退出配置未保留设置");ClientSettings.get().scanRange=32;ClientSettings.get().save();
            });
            context.setScreen(()->new DraftScreen(draft));
            context.runOnClient(client->((DraftScreen)client.currentScreen).navigate(5));context.waitTick();context.takeScreenshot("autostock-hotkeys-tab");
            context.runOnClient(client->{
                var originalScreen=client.currentScreen;
                var saved=ClientSettings.get().binding(ClientSettings.Action.CONFIG);click(client.currentScreen,saved.label());
                client.currentScreen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F8,0,0);
                if(client.currentScreen!=originalScreen)throw new AssertionError("修改热键打开了额外页面");
                client.currentScreen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);
                if(ClientSettings.get().binding(ClientSettings.Action.CONFIG).key()!=org.lwjgl.glfw.GLFW.GLFW_KEY_F8)throw new AssertionError("热键页未保存键位");
                var screen=client.currentScreen;screen.mouseClicked(Math.max(110,(int)(screen.width*.265))+Math.max(90,(int)(screen.width*.265))+6,Math.max(64,(int)(screen.height*.195))+5,0);
                if(ClientSettings.get().binding(ClientSettings.Action.CONFIG).key()!=ClientSettings.Action.CONFIG.key)throw new AssertionError("热键恢复默认未生效");
                ClientSettings.get().bind(ClientSettings.Action.CONFIG,saved.key(),saved.mods());
            });
            context.runOnClient(client->{((DraftScreen)client.currentScreen).navigate(5);click(client.currentScreen,ClientSettings.get().binding(ClientSettings.Action.CONFIG).label());});
            context.runOnClient(client->{
                var screen=client.currentScreen;
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_D,0,0);screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_S,0,0);screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_A,0,0);
                screen.mouseClicked(Math.max(110,(int)(screen.width*.265))+5,Math.max(64,(int)(screen.height*.195))+5,0);
            });
            context.waitTick();context.takeScreenshot("autostock-hotkey-multi-mouse");
            context.runOnClient(client->{
                var screen=client.currentScreen;screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);
                if(!ClientSettings.get().binding(ClientSettings.Action.CONFIG).codes().equals(java.util.List.of(68,83,65,-100)))throw new AssertionError("连续按键与鼠标组合未保存："+ClientSettings.get().binding(ClientSettings.Action.CONFIG));
                screen.mouseClicked(Math.max(110,(int)(screen.width*.265))+5,Math.max(64,(int)(screen.height*.195))+5,0);screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);
                if(!ClientSettings.get().binding(ClientSettings.Action.CONFIG).codes().isEmpty())throw new AssertionError("Esc 没有清空绑定为 NONE");
                var serialized=ClientSettings.snapshot();ClientSettings.restore(serialized);
                if(!ClientSettings.get().binding(ClientSettings.Action.CONFIG).codes().isEmpty())throw new AssertionError("NONE 重新加载后丢失");
                ClientSettings.get().bind(ClientSettings.Action.CONFIG,ClientSettings.Action.CONFIG.key,ClientSettings.Action.CONFIG.mods);
                ClientSettings.get().bindKeys(ClientSettings.Action.BOUNDARY,java.util.List.of(68,83,65));
                client.setScreen(null);
            });
            boolean oldBoundary=context.computeOnClient(client->ClientSettings.get().showRegions);
            context.getInput().holdKey(org.lwjgl.glfw.GLFW.GLFW_KEY_D);
            context.getInput().holdKey(org.lwjgl.glfw.GLFW.GLFW_KEY_S);
            context.runOnClient(client->{if(ClientSettings.get().showRegions!=oldBoundary)throw new AssertionError("未按全组合就触发");});
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_A);
            context.runOnClient(client->{if(ClientSettings.get().showRegions==oldBoundary)throw new AssertionError("完整多键组合未实际触发");});
            context.getInput().releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_S);context.getInput().releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_D);
            context.runOnClient(client->{
                ClientSettings.get().bindKeys(ClientSettings.Action.BOUNDARY,java.util.List.of(-97));
                if(!ClientKeys.onKey(client.getWindow().getHandle(),-97,org.lwjgl.glfw.GLFW.GLFW_PRESS,0)||ClientSettings.get().showRegions!=oldBoundary)throw new AssertionError("鼠标绑定未实际触发");
                ClientSettings.get().bind(ClientSettings.Action.BOUNDARY,ClientSettings.Action.BOUNDARY.key,ClientSettings.Action.BOUNDARY.mods);
            });
            context.setScreen(()->new BotInventoryScreen(new DraftScreen(draft)));context.waitTicks(2);
            world.getServer().runOnServer(server -> {
                var owner=server.getPlayerManager().getPlayerList().stream().filter(p->!(p instanceof carpet.patches.EntityPlayerMPFake)).findFirst().orElseThrow();
                server.getCommandManager().executeWithPrefix(owner.getCommandSource(),"autostock-manual");
                var fake=server.getPlayerManager().getPlayerList().stream().filter(carpet.patches.EntityPlayerMPFake.class::isInstance).findFirst().orElseThrow(()->new AssertionError("手动处理命令没有恢复任务假人"));
                if(!fake.getInventory().isEmpty())throw new AssertionError("手动上线重新生成了已交付材料");
                if(fake.getPos().squaredDistanceTo(owner.getPos())>.01)throw new AssertionError("上线位置不是玩家当前坐标");
                ((carpet.patches.EntityPlayerMPFake)fake).kill(net.minecraft.text.Text.literal("手动恢复测试结束"));
            });
            context.waitFor(client->InventoryView.snapshot!=null&&InventoryView.snapshot.state().equals("OFFLINE")&&InventoryView.snapshot.slots().stream().allMatch(ItemStack::isEmpty),1000);
            context.takeScreenshot("autostock-inventory-offline");
            var previousOpacity=context.computeOnClient(client->ClientSettings.get().hudOpacity.clone());
            var previousPositions=context.computeOnClient(client->ClientSettings.get().hudPositions.clone());
            boolean previousRegion=context.computeOnClient(client->ClientSettings.get().showRegionHud);
            context.setScreen(()->new HudPositionScreen(draft,new DraftScreen(draft)));
            context.runOnClient(client->{var screen=client.currentScreen;var r=HudPanels.bounds(0,screen.width,screen.height,ClientSettings.get().hudPositions);double x=r.x()+r.width()-1,y=r.y()+r.height()/2.0;screen.mouseClicked(x,y,0);screen.mouseDragged(x+25,y,0,25,0);screen.mouseReleased(x+25,y,0);var moved=HudPanels.bounds(0,screen.width,screen.height,ClientSettings.get().hudPositions);if(moved.width()!=r.width())throw new AssertionError("HUD 拖动意外改变了尺寸");screen.mouseClicked(moved.x()+10,moved.y()+10,1);});
            context.waitTick();context.takeScreenshot("autostock-hud-right-menu");
            context.runOnClient(client->{var screen=client.currentScreen;var x=HudPositionScreen.class.getDeclaredField("menuX");var y=HudPositionScreen.class.getDeclaredField("menuY");x.setAccessible(true);y.setAccessible(true);screen.mouseClicked(x.getInt(screen)+48,y.getInt(screen)+20,0);screen.mouseReleased(x.getInt(screen)+48,y.getInt(screen)+20,0);screen.mouseClicked(x.getInt(screen)+8,y.getInt(screen)+6,0);screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);if(ClientSettings.get().hudOpacity[0]!=50||ClientSettings.get().hudOpacity[1]!=previousOpacity[1])throw new AssertionError("HUD 透明度没有独立保存");if(ClientSettings.get().showRegionHud==previousRegion)throw new AssertionError("Esc 退出未保存 HUD 开关");});
            context.setScreen(()->new HudPositionScreen(draft,new DraftScreen(draft)));context.waitTick();context.takeScreenshot("autostock-hud-hidden-preview");
            context.runOnClient(client->{var screen=client.currentScreen;var appearance=HudPositionScreen.class.getDeclaredField("opacity");appearance.setAccessible(true);((int[])appearance.get(screen))[0]=5;screen.close();if(ClientSettings.get().hudOpacity[0]!=5)throw new AssertionError("退出未保存 HUD 外观");ClientSettings.get().hudOpacity=previousOpacity;ClientSettings.get().hudPositions=previousPositions;ClientSettings.get().showRegionHud=previousRegion;ClientSettings.get().save();});
            context.setScreen(()->new ColorSettingsScreen(new DraftScreen(draft)));context.waitTick();context.takeScreenshot("autostock-color-settings");
            int oldColor=context.computeOnClient(client->ClientSettings.get().colors[0]);
            context.setScreen(()->ColorEditor.create(new ColorSettingsScreen(new DraftScreen(draft)),ClientSettings.get().colors[0],value->{ClientSettings.get().colors[0]=value;ClientSettings.get().save();}));context.waitTick();context.takeScreenshot("autostock-palette");
            context.runOnClient(client->{
                var field=fi.dy.masa.malilib.gui.GuiColorEditorHSV.class.getDeclaredField("textFieldFullColor");field.setAccessible(true);((fi.dy.masa.malilib.gui.GuiTextFieldGeneric)field.get(client.currentScreen)).setTextWrapper("#80123456");
                client.currentScreen.close();
                if(ClientSettings.get().colors[0]!=0x80123456)throw new AssertionError("调色盘没有应用带透明度的十六进制颜色");
            });
            context.setScreen(()->ColorEditor.create(new ColorSettingsScreen(new DraftScreen(draft)),ClientSettings.get().colors[0],value->{ClientSettings.get().colors[0]=value;ClientSettings.get().save();}));
            context.runOnClient(client->{var field=fi.dy.masa.malilib.gui.GuiColorEditorHSV.class.getDeclaredField("textFieldFullColor");field.setAccessible(true);((fi.dy.masa.malilib.gui.GuiTextFieldGeneric)field.get(client.currentScreen)).setTextWrapper("#ABCDEF");client.currentScreen.close();if(ClientSettings.get().colors[0]!=0xFFABCDEF)throw new AssertionError("关闭调色盘没有直接应用颜色");ClientSettings.get().colors[0]=oldColor;ClientSettings.get().save();});
            context.setScreen(()->new ConfigTransferScreen(new DraftScreen(draft)));context.waitTick();context.takeScreenshot("autostock-config-transfer");
            context.runOnClient(client->{
                ClientSettings.exportConfig();var before=ClientSettings.get();var file=ClientSettings.transferPath();var json=java.nio.file.Files.readString(file);
                java.nio.file.Files.writeString(file,"{\"hudScale\":9}");boolean rejected=false;try{ClientSettings.importConfig();}catch(IllegalArgumentException expected){rejected=true;}if(!rejected||ClientSettings.get()!=before)throw new AssertionError("无效配置覆盖了当前设置");
                java.nio.file.Files.writeString(file,json);ClientSettings.importConfig();if(ClientSettings.get().colors[0]!=oldColor)throw new AssertionError("配置导出导入没有保持颜色");
            });
            context.setScreen(()->new RegionDetailScreen(draft,RegionRole.MATERIAL,new DraftScreen(draft)));context.waitTick();context.takeScreenshot("autostock-region-details");
            context.setScreen(()->new PlanDetailScreen(draft,new DraftScreen(draft)));context.waitTick();context.takeScreenshot("autostock-plan-details");
            context.setScreen(()->new TaskDetailScreen(draft,new DraftScreen(draft)));context.waitTick();context.takeScreenshot("autostock-task-details");
            var originalTask=context.computeOnClient(client->draft.frozenId());
            context.runOnClient(client->{
                var updated=new MaterialListBase() {
                    public String getName(){return "更新后的材料";}public String getTitle(){return getName();}public void reCreateMaterialList(){}
                };
                updated.setMaterialListEntries(List.of(new MaterialListEntry(new ItemStack(Items.DIRT),20,20,0,0)));
                client.setScreen(new GuiMaterialList(updated));
            });
            context.waitTicks(3);context.setScreen(()->new DraftScreen(draft));
            context.runOnClient(client->{draft.copyAsDraft();if(draft.frozen()||draft.frozenId()!=null||draft.totalItems()!=20||!draft.proposal().demand().equals(Map.of("minecraft:dirt",20L)))throw new AssertionError("最新材料没有形成独立可编辑草稿");((DraftScreen)client.currentScreen).selectTab(1);});
            context.waitTick();context.takeScreenshot("autostock-updated-materials");
            world.getServer().runOnServer(server->{
                var owner=server.getPlayerManager().getPlayerList().stream().filter(p->!(p instanceof carpet.patches.EntityPlayerMPFake)).findFirst().orElseThrow();
                try {if(new dev.autostock.server.FrozenTasks(server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)).load(owner.getUuid(),originalTask).totalItems()!=192)throw new AssertionError("复制新需求改写了原冻结任务");}
                catch(java.io.IOException error){throw new AssertionError(error);}
            });
            var fixture=context.computeOnClient(client->{
                var selection=new fi.dy.masa.litematica.selection.AreaSelection();selection.addSubRegionBox(new fi.dy.masa.litematica.selection.Box(net.minecraft.util.math.BlockPos.ORIGIN,new net.minecraft.util.math.BlockPos(1,1,1),"fixture"),true);
                var empty=fi.dy.masa.litematica.schematic.LitematicaSchematic.createEmptySchematic(selection,"AutoStock isolated test");
                var folder=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("autostock-fixtures");java.nio.file.Files.createDirectories(folder);
                if(!empty.writeToFile(folder,"ui-test.litematic",true))throw new AssertionError("测试原理图文件未写入");
                var schematic=fi.dy.masa.litematica.schematic.LitematicaSchematic.createFromFile(folder,"ui-test.litematic");if(schematic==null)throw new AssertionError("无法读取测试原理图");
                fi.dy.masa.litematica.data.SchematicHolder.getInstance().addSchematic(schematic,true);return schematic;
            });
            context.setScreen(()->new SchematicLibraryScreen(new DraftScreen(draft)));context.waitTick();context.takeScreenshot("autostock-schematic-library");
            context.setScreen(()->new SchematicDetailScreen(fixture,new SchematicLibraryScreen(new DraftScreen(draft))));context.waitTick();context.takeScreenshot("autostock-schematic-detail");
            context.runOnClient(client->{Schematics.reload(fixture);if(fixture.getSubRegionCount()!=1)throw new AssertionError("原理图重载损坏子区");Schematics.unload(fixture);if(Schematics.loaded().contains(fixture))throw new AssertionError("原理图未卸载");});
            context.setScreen(()->new BotInventoryScreen(new DraftScreen(draft)));context.waitTicks(2);
            var managementId=new java.util.concurrent.atomic.AtomicReference<java.util.UUID>();
            world.getServer().runOnServer(server->{
                var owner=server.getPlayerManager().getPlayerList().stream().filter(p->!(p instanceof carpet.patches.EntityPlayerMPFake)).findFirst().orElseThrow();
                server.getCommandManager().executeWithPrefix(owner.getCommandSource(),"autostock-spawn");
                var fake=server.getPlayerManager().getPlayerList().stream().filter(carpet.patches.EntityPlayerMPFake.class::isInstance).findFirst().orElseThrow();managementId.set(fake.getUuid());
                if(fake.getPos().squaredDistanceTo(owner.getPos())>.01)throw new AssertionError("放置未使用玩家坐标");
                fake.getInventory().setStack(0,new ItemStack(Items.STONE));
                server.getCommandManager().executeWithPrefix(owner.getCommandSource(),"autostock-delete");if(fake.getInventory().getStack(0).isEmpty())throw new AssertionError("删除没有保护背包物品");
                fake.getInventory().setStack(0,ItemStack.EMPTY);fake.getEnderChestInventory().setStack(0,new ItemStack(Items.DIRT));
                server.getCommandManager().executeWithPrefix(owner.getCommandSource(),"autostock-delete");if(fake.getEnderChestInventory().getStack(0).isEmpty())throw new AssertionError("删除没有保护末影箱");
                fake.getEnderChestInventory().setStack(0,ItemStack.EMPTY);
            });
            context.waitFor(client->InventoryView.snapshot!=null&&InventoryView.snapshot.state().equals("ONLINE"),1000);
            world.getServer().runOnServer(server->{var owner=server.getPlayerManager().getPlayerList().stream().filter(p->!(p instanceof carpet.patches.EntityPlayerMPFake)).findFirst().orElseThrow();server.getCommandManager().executeWithPrefix(owner.getCommandSource(),"autostock-recall");});
            context.waitFor(client->InventoryView.snapshot.state().equals("OFFLINE"),1000);
            world.getServer().runOnServer(server->{var owner=server.getPlayerManager().getPlayerList().stream().filter(p->!(p instanceof carpet.patches.EntityPlayerMPFake)).findFirst().orElseThrow();server.getCommandManager().executeWithPrefix(owner.getCommandSource(),"autostock-delete");});
            context.waitFor(client->InventoryView.snapshot.state().equals("MISSING"),1000);
            world.getServer().runOnServer(server->{if(java.nio.file.Files.exists(server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("playerdata/"+managementId.get()+".dat")))throw new AssertionError("空假人数据文件未删除");});
            context.takeScreenshot("autostock-deleted-bot");
            context.setScreen(() -> null);
        } catch (ReflectiveOperationException | java.io.IOException error) { throw new AssertionError("测试状态设置失败", error); }
    }
    private static void click(net.minecraft.client.gui.screen.Screen screen,String text) throws ReflectiveOperationException {
        var field=fi.dy.masa.malilib.gui.GuiBase.class.getDeclaredField("buttons");field.setAccessible(true);
        var label=fi.dy.masa.malilib.gui.button.ButtonBase.class.getDeclaredField("displayString");label.setAccessible(true);
        for(Object value:(java.util.List<?>)field.get(screen)){var button=(fi.dy.masa.malilib.gui.button.ButtonBase)value;if(label.get(button).equals(text)){screen.mouseClicked(button.getX()+2,button.getY()+2,0);return;}}
        throw new AssertionError("找不到按钮："+text);
    }
}













