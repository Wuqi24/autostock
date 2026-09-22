package dev.autostock.server;

import carpet.patches.EntityPlayerMPFake;
import carpet.patches.FakeClientConnection;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

final class TaskFakePlayers {
    static UUID id(UUID owner) { return UUID.nameUUIDFromBytes(("autostock:fake:" + owner).getBytes(StandardCharsets.UTF_8)); }
    static EntityPlayerMPFake login(MinecraftServer server, ServerWorld world, UUID owner, Vec3d spawn) {
        if (!server.isOnThread()) throw new IllegalArgumentException("假人创建必须在服务器线程");
        TaskService.deletedOwners.remove(owner);
        var id = id(owner); var existing = server.getPlayerManager().getPlayer(id);
        if (existing != null) {
            if (!(existing instanceof EntityPlayerMPFake fake)) throw new IllegalArgumentException("专用假人 UUID 被真人占用，停止操作");
            return fake;
        }
        String name = "AS_" + owner.toString().replace("-", "").substring(0,12);
        if (server.getPlayerManager().getPlayer(name) != null) throw new IllegalArgumentException("专用假人名称已被占用");
        boolean fresh = !Files.exists(server.getSavePath(WorldSavePath.ROOT).resolve("playerdata/" + id + ".dat"));
        var profile = new GameProfile(id,name); var options = SyncedClientOptions.createDefault();
        var fake = EntityPlayerMPFake.respawnFake(server,world,profile,options);
        fake.fixStartingPosition = () -> fake.setPosition(spawn);
        server.getPlayerManager().onPlayerConnect(new FakeClientConnection(NetworkSide.SERVERBOUND),fake,
                new ConnectedClientData(profile,0,options,false));
        MinecraftCompat.teleport(fake, world, spawn);
        if (fresh) {
            fake.changeGameMode(GameMode.SURVIVAL);
        }
        // Offline login uses the owner-provided server coordinates after saved data loads; retain inventory and health.
        return fake;
    }
}


