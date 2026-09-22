package dev.autostock.client;

import dev.autostock.core.DraftCodec;
import dev.autostock.core.PlanDraft;
import dev.autostock.core.RegionBox;
import dev.autostock.core.RegionRole;
import dev.autostock.net.DraftRequest;
import dev.autostock.net.DraftResponse;
import dev.autostock.net.ScanRequest;
import dev.autostock.net.ScanResponse;
import dev.autostock.net.CancelScan;
import dev.autostock.core.ScanReport;
import com.google.gson.Gson;
import fi.dy.masa.litematica.data.DataManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class ClientDraft {
    private final Map<RegionRole, List<RegionBox>> regions = new EnumMap<>(RegionRole.class);
    private Map<String, Long> demand = Map.of();
    private String dimension;
    private String schematicName;
    private String status = "先读取三区域，再导入原理图总材料";
    private UUID pendingRequest;
    private long pendingSince;
    private boolean scanning;
    private boolean containersBound;
    private ScanReport report;
    private int multiplier = 1;
    private boolean frozen, bindingFailed, remoteConflict;
    private PlanDraft submittedSnapshot, frozenSnapshot;
    private UUID frozenId;
    private dev.autostock.net.TaskStatus taskStatus;
    public dev.autostock.net.TaskStatus taskStatus() { return taskStatus; }
    private List<ScanReport.Mark> failureMarks = List.of();

    public void clear() {
        if(taskStatus!=null&&(taskStatus.state().equals("RUNNING")||taskStatus.state().equals("PAUSED")))
            throw new IllegalArgumentException("请先取消当前任务再新建草稿，避免丢失当前任务的控制入口");
        cancelScan();
        discard();
    }

    public void discard() {
        containersBound=false;
        regions.clear();
        demand = Map.of();
        dimension = null;
        schematicName = null;
        pendingRequest = null;
        scanning = false;
        report = null;
        multiplier = 1;
        frozen = bindingFailed = remoteConflict = false;regionRequest=null;regionBinding=null;failedRegionScan=Map.of();
        submittedSnapshot = frozenSnapshot = null;
        frozenId = null;
        taskStatus = null;
        failureMarks = List.of();
        LitematicaResult.clear();
        status = "草稿已清空；选区快照仅保留在本次连接中";
    }

    public void tick() {
        var world = Minecraft.getInstance().level;
        if (dimension != null && (world == null || !dimension.equals(world.dimension().identifier().toString()))) {
            cancelScan();
            discard();
            status = "世界或维度已改变，请重新读取选区与总材料";
        }
        if (pendingRequest != null && System.nanoTime() - pendingSince > (scanning ? 90_000_000_000L : 10_000_000_000L)) {
            cancelScan();
            pendingRequest = null;
            scanning = false;
            status = "服务端回复超时；未启动任何备货任务，可重新提交";
        }
    }

    private void requireWorld() {
        tick();
        var world = Minecraft.getInstance().level;
        if (world == null) throw new IllegalArgumentException("请先进入单人存档或服务器");
        if (dimension == null) dimension = world.dimension().identifier().toString();
    }

    public void clearRegion(RegionRole role) {requireEditable();regions.remove(role);invalidate(role.label()+"已清除");}
    public void capture(RegionRole role) {capture(role,false);}
    public void capture(RegionRole role,boolean append) {
        requireWorld();
        requireEditable();
        var selection = DataManager.getSelectionManager().getCurrentSelection();
        if (selection == null || selection.getAllSubRegionBoxes().isEmpty()) {
            throw new IllegalArgumentException("请先在 Litematica 中建立区域选区");
        }
        var boxes = new ArrayList<RegionBox>(append?regions.getOrDefault(role,List.of()):List.of());
        long volume = boxes.stream().mapToLong(RegionBox::volume).sum();
        for (var box : selection.getAllSubRegionBoxes()) {
            var a = box.getPos1();
            var b = box.getPos2();
            if (a == null || b == null) throw new IllegalArgumentException("存在尚未设置两个角点的子选区");
            var region = RegionBox.between(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
            volume = Math.addExact(volume, region.volume());
            if (volume > PlanDraft.MAX_REGION_VOLUME || boxes.size() >= 32) {
                throw new IllegalArgumentException("每个区域最多 32 个子选区，体积合计最多 262144 方块");
            }
            boxes.add(region);
        }
        regions.put(role, List.copyOf(boxes));
        invalidate(role.label() + "已读取：" + boxes.size() + " 个子选区；尚未绑定实际容器");
        if (hasConflict()) status = "区域冲突：空盒区与备货区重叠，请调整后重新保存";
    }

    public void importTotal() {
        requireWorld();
        if(frozen) {status="当前需求已冻结；刷新库存请用备货计划页，要更换材料请复制为新需求";return;}
        requireEditable();
        var snapshot = LitematicaResult.latest();
        demand = snapshot.counts();
        schematicName = snapshot.name();
        multiplier = snapshot.multiplier();
        invalidate("已读取 Litematica 当前表格结果，总量列 × " + multiplier + "；创建时冻结");
    }
    public void prepareRegionEdit(){
        if(!frozen)return;
        if(taskStatus!=null&&(taskStatus.state().equals("RUNNING")||taskStatus.state().equals("PAUSED")))throw new IllegalArgumentException("请先取消任务，再保存新的绑定区域");
        cancelScan();frozen=false;frozenSnapshot=null;frozenId=null;submittedSnapshot=null;taskStatus=null;
    }
    public void copyAsDraft() {
        requireWorld();
        if(taskStatus!=null && (taskStatus.state().equals("RUNNING")||taskStatus.state().equals("PAUSED")))
            throw new IllegalArgumentException("请先取消当前任务，再复制为新需求；已领取物品仍保留");
        var latest=LitematicaResult.latest();
        cancelScan();frozen=false;frozenSnapshot=null;frozenId=null;submittedSnapshot=null;taskStatus=null;
        demand=latest.counts();schematicName=latest.name();multiplier=latest.multiplier();
        invalidate("已从最新材料列表创建新需求草稿；原冻结任务仍保存在服务器");
    }
    public void submit() {
        requireWorld();
        if (!ClientPlayNetworking.canSend(DraftRequest.ID)) {
            throw new IllegalArgumentException("服务端未提供自动备货协议，请安装相同版本本 Mod");
        }
        var draft = proposal();
        String json = DraftCodec.encode(draft);
        cancelScan();
        scanning = false;
        pendingRequest = UUID.randomUUID();
        pendingSince = System.nanoTime();
        ClientPlayNetworking.send(new DraftRequest(pendingRequest, json));
        status = "已提交，等待服务端校验（不会启动假人）";
    }

    public void scan() {ClientSettings.get().sync();
        requireWorld();
        if (!ClientPlayNetworking.canSend(ScanRequest.ID)) throw new IllegalArgumentException("服务端未安装阶段 2 自动备货 Mod");
        var draft = proposal();
        String json = DraftCodec.encode(draft);
        cancelScan();
        report = null;
        pendingRequest = UUID.randomUUID();
        pendingSince = System.nanoTime();
        scanning = true;
        ClientPlayNetworking.send(new ScanRequest(pendingRequest, json));
        status = "正在扫描；新备货容器须为空，成功后保存绑定（不会取放物品）";
    }

    public void receiveScan(ScanResponse response) {
        if (!response.requestId().equals(pendingRequest)) return;
        try {
            report = new Gson().fromJson(response.json(), ScanReport.class);
            if (report == null || report.plan() == null || !report.dimension().equals(dimension)) throw new IllegalArgumentException();
            status = "扫描完成 · 材料容器 " + report.materialContainers() + " / 空盒 " + report.emptyBoxes()
                    + " / 备货空槽 " + report.outputSlots() + "；仅生成计划，尚不执行取放";
            containersBound=true;
            bindingFailed = remoteConflict = false;failedRegionScan=Map.of();
            failureMarks = List.of();
            if (response.frozen()) {
                if (submittedSnapshot == null) throw new IllegalArgumentException("缺少冻结快照");
                frozenSnapshot = submittedSnapshot; frozen = true;
                frozenId = response.requestId();
                status = "服务端已保存冻结需求：" + response.requestId() + "；尚未启动假人";
            }
        } catch (RuntimeException error) {
            report = null;
            status = "无法读取服务端扫描报告，请核对两端版本";
        }
        pendingRequest = null;
        scanning = false;
        // Non-bot feedback stays in the screen status area.
    }

    private void cancelScan() {
        if (scanning && pendingRequest != null && Minecraft.getInstance().getConnection() != null
                && ClientPlayNetworking.canSend(CancelScan.ID)) {
            ClientPlayNetworking.send(new CancelScan(pendingRequest));
        }
    }

    public ScanReport report() { return report; }

    public void receive(DraftResponse response) {
        // Ignore a response for a draft that has since changed, timed out or disconnected.
        if (!response.requestId().equals(pendingRequest)) return;
        pendingRequest = null;
        scanning = false;
        status = (response.accepted() ? "通过：" : "未通过：") + response.message();
        if (!response.accepted()) {
            bindingFailed = response.issue().equals("OUTPUT_NOT_EMPTY");
            remoteConflict = response.issue().equals("REGION_CONFLICT");
            failureMarks = response.marks();
        }
        // Non-bot feedback stays in the screen status area.
    }

    private void invalidate(String message) {
        containersBound=false;
        cancelScan();
        pendingRequest = null;
        scanning = false;
        report = null;
        bindingFailed = remoteConflict = false;failedRegionScan=Map.of();
        failureMarks = List.of();
        status = message;
    }

    public void perform(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | ArithmeticException error) {
            status = "操作未完成：" + error.getMessage();
        }
        // Non-bot feedback stays in the screen status area.
    }

    private void notifyFake(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) {var settings=ClientSettings.get();player.sendSystemMessage(Component.literal(settings.notificationPrefix + message));if(settings.notificationSound)player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,.3F,1F);}
    }

    public String status() { return status; }
    public int multiplier() { return multiplier; }
    public boolean frozen() { return frozen; }
    private void requireEditable() { if (frozen) throw new IllegalArgumentException("需求已冻结，不能修改当前任务草稿"); }
    public PlanDraft proposal() {
        if (frozenSnapshot != null) return frozenSnapshot;
        return new PlanDraft(PlanDraft.PROTOCOL, dimension, schematicName, regions, demand, multiplier, ClientSettings.get().quickShulker);
    }
    public void freezeDemand() {ClientSettings.get().sync();
        requireWorld(); requireEditable();
        if (!ClientPlayNetworking.canSend(dev.autostock.net.FreezeRequest.ID)) throw new IllegalArgumentException("服务端没有冻结需求接口，请更新两端版本");
        var snapshot = proposal(); cancelScan(); report = null;
        submittedSnapshot = snapshot; pendingRequest = UUID.randomUUID(); pendingSince = System.nanoTime(); scanning = true;
        ClientPlayNetworking.send(new dev.autostock.net.FreezeRequest(pendingRequest, DraftCodec.encode(snapshot)));
        status = "正在重新扫描并冻结需求；完成后保存到服务端，不启动假人";
    }
    public void taskToggle() {ClientSettings.get().sync(); requestTask(false); }
    public void taskCancel() { requestTask(true); }
    private void requestTask(boolean cancel) {
        requireWorld();
        if(frozenId==null)throw new IllegalArgumentException("请先冻结任务需求，或加载已保存任务");
        if(!ClientPlayNetworking.canSend(dev.autostock.net.TaskRequest.ID))throw new IllegalArgumentException("服务端版本不支持自动执行任务");
        ClientPlayNetworking.send(new dev.autostock.net.TaskRequest(frozenId,cancel));
        status=cancel?"正在取消，等待服务器保存并下线":"正在请求启动 / 暂停，等待服务器确认";
    }
    public void receiveTask(dev.autostock.net.TaskStatus response) {
        if(!response.taskId().equals(frozenId))return;
        boolean loopDone=response.detail().startsWith("循环补货")&&(taskStatus==null||!taskStatus.detail().startsWith("循环补货"));
        boolean shortage=response.detail().contains("材料不足")||response.detail().startsWith("缺货时自动触发");
        boolean shortageStarted=shortage&&(taskStatus==null||!(taskStatus.detail().contains("材料不足")||taskStatus.detail().startsWith("缺货时自动触发")));
        boolean changed=taskStatus==null || !taskStatus.state().equals(response.state())||loopDone||shortageStarted;
        if(response.bindingsVerified())containersBound=true;else if(response.state().equals("ERROR"))containersBound=false;
        taskStatus=response;status=response.detail();if(changed&&(!(response.state().equals("COMPLETED")||loopDone)||ClientSettings.get().notifyComplete)&&(!shortage||ClientSettings.get().notifyShortage))notifyFake((response.state().equals("COMPLETED")||loopDone)?ClientSettings.get().completionMessage:status);
    }
    private UUID regionRequest;dev.autostock.core.RegionBinding regionBinding;private Map<RegionRole,List<RegionBox>> failedRegionScan=Map.of();
    public void scanRegions(){requireWorld();ClientSettings.get().sync();if(!ClientPlayNetworking.canSend(dev.autostock.net.RegionScan.ID))throw new IllegalArgumentException("服务端不支持独立区域绑定");failedRegionScan=Map.of();regionBinding=null;regionRequest=UUID.randomUUID();ClientPlayNetworking.send(new dev.autostock.net.RegionScan(new Gson().toJson(new dev.autostock.core.RegionBinding(regionRequest,dimension,regions(),null,0,0,""))));status="正在扫描区域";}
    public void receiveBinding(dev.autostock.net.RegionBound payload){var result=new Gson().fromJson(payload.json(),dev.autostock.core.RegionBinding.class);if(result==null||!result.request().equals(regionRequest))return;regionRequest=null;if(!result.regions().equals(regions()))return;if(result.error()!=null&&!result.error().isEmpty()){regionBinding=null;containersBound=false;failedRegionScan=regions();status=result.error();return;}failedRegionScan=Map.of();regionBinding=result;status="区域绑定成功";}
    public String bindingLabel(RegionRole role) {
        if(!saved(role))return "未设置";
        if(hasConflict())return "区域冲突";
        if(role==RegionRole.OUTPUT&&bindingFailed)return "绑定失败";
        if(scanning||regionRequest!=null)return "校验中";if(failedRegionScan.equals(regions()))return "绑定失败";
        if(regionBinding!=null&&regionBinding.regions().equals(regions())&&regionBinding.containers().containsKey(role))return "已绑定";
        if(!containersBound)return "已设置 · 待校验";
        return "已绑定";
    }
    public boolean saved(RegionRole role) { return regions.containsKey(role); }
    public boolean bindingFailed() { return bindingFailed; }
    public List<ScanReport.Mark> failureMarks() { return failureMarks; }
    public Map<RegionRole, List<RegionBox>> regions() { return Map.copyOf(regions); }
    public boolean hasConflict() {
        if (remoteConflict) return true;
        for (var a : regions.getOrDefault(RegionRole.EMPTY_BOX, List.of()))
            for (var b : regions.getOrDefault(RegionRole.OUTPUT, List.of())) if (a.overlaps(b)) return true;
        return false;
    }
    public boolean regionsReady() { return regions.size() == 3 && !hasConflict() && !bindingFailed; }
    public UUID frozenId() { return frozenId; }
    public void loadFrozen(UUID task) {
        requireWorld();
        if (!ClientPlayNetworking.canSend(dev.autostock.net.LoadFrozenRequest.ID)) throw new IllegalArgumentException("服务端不支持读取冻结需求");
        cancelScan(); scanning = false; pendingRequest = UUID.randomUUID(); pendingSince = System.nanoTime();
        ClientPlayNetworking.send(new dev.autostock.net.LoadFrozenRequest(pendingRequest, task));
        status = "读取已保存需求；不会恢复或启动假人动作";
    }
    public void receiveFrozen(dev.autostock.net.FrozenResponse response) {
        if (!response.requestId().equals(pendingRequest)) return;
        try {
            var snapshot = DraftCodec.decode(response.json());
            if (!snapshot.dimension().equals(dimension)) throw new IllegalArgumentException("当前维度与冻结需求不符");
            containersBound=false;
        regions.clear(); regions.putAll(snapshot.regions()); demand = snapshot.demand(); schematicName = snapshot.schematicName();
            multiplier = snapshot.multiplier(); frozenSnapshot = snapshot; frozenId = response.taskId(); frozen = true;
            report = null; bindingFailed = remoteConflict = false; failureMarks = List.of();
            status = "已加载冻结需求：" + frozenId + "；需重新扫描世界，尚未启动假人";
        } catch (RuntimeException error) { status = "读取冻结需求失败：" + error.getMessage(); }
        pendingRequest = null; scanning = false;
    }
    public String schematicName() { return schematicName == null ? "未导入" : schematicName; }
    public List<Map.Entry<String, Long>> materials() { return new ArrayList<>(new TreeMap<>(demand).entrySet()); }
    public long totalItems() { return demand.values().stream().mapToLong(Long::longValue).sum(); }
    public String regionSummary(RegionRole role) {
        var boxes = regions.get(role);
        return boxes == null ? "未读取" : boxes.size() + " 子区 / " + boxes.stream().mapToLong(RegionBox::volume).sum() + " 格";
    }
}






