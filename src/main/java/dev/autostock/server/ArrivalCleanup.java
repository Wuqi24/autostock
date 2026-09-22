package dev.autostock.server;

import carpet.patches.EntityPlayerMPFake;
import dev.autostock.core.RegionBox;
import dev.autostock.core.RegionRole;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.util.*;

/** A bounded, physical walk-and-transfer pass for inventory found when logging in. */
final class ArrivalCleanup {
    final EntityPlayerMPFake player;
    private final UUID owner;
    private final Set<String> bindingKeys;
    private final Map<RegionRole,List<RegionBox>> regions;
    private WarehouseIndex index;
    private boolean indexed, done;
    private String failure, detail = "上线检查：将非空盒物品存入备货区";
    private FakeWalker walker;
    private WarehouseIndex.Container target;
    private int slot, retries, retryDelay;

    ArrivalCleanup(EntityPlayerMPFake player, UUID owner) throws IOException {
        this.player = player; this.owner = owner;
        RuntimeOptions.attach(player.getUuid(), owner);
        bindingKeys = new OutputBindings(MinecraftCompat.server(player), owner).keys(MinecraftCompat.world(player).getRegistryKey().getValue().toString());
        if (bindingKeys.isEmpty()) throw new IllegalArgumentException("上线背包有物品，请先绑定备货区；物品仍保留");
        var boxes = new ArrayList<RegionBox>();
        for (String key : bindingKeys) for (String part : key.split(";")) {
            var xyz = part.substring(0, part.indexOf(':')).split(",");
            int x = Integer.parseInt(xyz[0]), y = Integer.parseInt(xyz[1]), z = Integer.parseInt(xyz[2]);
            boxes.add(new RegionBox(x,y,z,x,y,z));
        }
        regions = Map.of(RegionRole.OUTPUT, List.copyOf(boxes));
        index = new WarehouseIndex(MinecraftCompat.world(player), regions, Map.of());
    }

    static boolean emptyBox(ItemStack item) { return InventoryItems.isBox(item) && !item.contains(net.minecraft.component.DataComponentTypes.CONTAINER_LOOT) && !InventoryItems.contents(item).iterator().hasNext(); }
    static boolean needed(EntityPlayerMPFake player) {
        for (int i=0;i<36;i++) if (!player.getInventory().getStack(i).isEmpty() && !emptyBox(player.getInventory().getStack(i))) return true;
        return false;
    }
    boolean done() { return done; }
    String failure() { return failure; }
    String detail() { return detail; }
    void stop() { if (walker != null) walker.stop();walker=null;done=true;TaskStop.pause(player); }

    void tick() {
        if (done) return;
        try {
            if (MinecraftCompat.server(player).getPlayerManager().getPlayer(player.getUuid()) != player) throw new IllegalArgumentException("假人已下线");
            if (!player.isAlive() || player.getHealth() <= player.getMaxHealth()*.5F) { TaskStop.logout(player,"低血量：归置停止，物品保留");throw new IllegalArgumentException("低血量，物品保留"); }
            if (retryDelay > 0) { retryDelay--;return; }
            if (!indexed) {
                if (!index.advance()) return;
                for (var c : index.containers) if (!bindingKeys.contains(c.key())) throw new IllegalArgumentException("备货容器结构改变，请重新绑定");
                indexed = true;
            }
            if (walker != null) {
                walker.tick();
                if (walker.state() == FakeWalker.State.FAILED) { retry(walker.failure());return; }
                if (walker.state() != FakeWalker.State.ARRIVED) return;
                walker = null;
                deposit();retries=0;return;
            }
            slot = -1;
            for (int i=0;i<36;i++) { var item=player.getInventory().getStack(i);if(!item.isEmpty()&&!emptyBox(item)){slot=i;break;} }
            if (slot < 0) { done=true;detail="上线背包已归置，空盒保留";return; }
            target = index.containers.stream().filter(c->{c.verify(MinecraftCompat.world(player));for(int i=0;i<c.inventory().size();i++)if(c.inventory().getStack(i).isEmpty())return true;return false;}).findFirst().orElseThrow(()->new IllegalArgumentException("备货区已满，上线物品仍保留在假人背包"));
            if (!RuntimeOptions.get(player.getUuid()).autoPath()) {
                if (!SafePath.canInteract(player,MinecraftCompat.position(player),target.position())) throw new IllegalArgumentException("自动寻路已关闭，无法前往备货区归置物品");
                deposit();return;
            }
            walker = new FakeWalker(player,target.position(),true);detail="上线背包归置：前往备货区";
        } catch (Exception problem) { failure=Objects.toString(problem.getMessage(),"上线归置失败");stop(); }
    }

    private void deposit() throws IOException {
        target.verify(MinecraftCompat.world(player));var item=player.getInventory().getStack(slot);
        if(item.isEmpty()||emptyBox(item))return;
        int into=-1;for(int i=0;i<target.inventory().size();i++)if(target.inventory().getStack(i).isEmpty()){into=i;break;}
        if(into<0)throw new IllegalArgumentException("备货容器已满，物品保留");
        var handler=SourceBoxFlow.openOrigin(player,target.position());
        try { ContainerTransfers.move(player,handler,ContainerTransfers.playerSlot(player,handler,slot),into,item.getCount()); }
        finally { player.closeHandledScreen(); }
        if(slot==9){var journal=new TaskJournal(MinecraftCompat.server(player).getSavePath(WorldSavePath.ROOT),owner);var old=journal.load();if(old!=null&&old.source()!=null)journal.save(new TaskJournal.Entry(old.version(),old.task(),old.state(),old.dimension(),null,"来源盒已按上线归置规则存入备货区"));}
    }

    private void retry(String why) {
        if (walker!=null)walker.stop();walker=null;
        if (why.contains("物品")||retries>=5) { failure="归置寻路失败，请玩家帮助："+why;stop();return; }
        retries++;retryDelay=20;indexed=false;index=new WarehouseIndex(MinecraftCompat.world(player),regions,Map.of());detail="归置路径更新 "+retries+"/5";
    }
}
