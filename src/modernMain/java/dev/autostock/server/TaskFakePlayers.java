package dev.autostock.server;

import carpet.patches.EntityPlayerMPFake;
import carpet.patches.FakeClientConnection;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

final class TaskFakePlayers {
    static UUID id(UUID owner) { return UUID.nameUUIDFromBytes(("autostock:fake:" + owner).getBytes(StandardCharsets.UTF_8)); }
    static EntityPlayerMPFake login(MinecraftServer server, ServerLevel world, UUID owner, Vec3 spawn) {
        if (!server.isSameThread()) throw new IllegalArgumentException("假人创建必须在服务器线程");
        TaskService.deletedOwners.remove(owner);
        var id = id(owner); var existing = server.getPlayerList().getPlayer(id);
        if (existing != null) {
            if (!(existing instanceof EntityPlayerMPFake fake)) throw new IllegalArgumentException("专用假人 UUID 被真人占用，停止操作");
            return fake;
        }
        String name = "AS_" + owner.toString().replace("-", "").substring(0,12);
        if (server.getPlayerList().getPlayerByName(name) != null) throw new IllegalArgumentException("专用假人名称已被占用");
        boolean fresh = !Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("playerdata/" + id + ".dat"));
        var profile = new GameProfile(id,name); var options = ClientInformation.createDefault();
        var fake = EntityPlayerMPFake.respawnFake(server,world,profile,options);
        fake.fixStartingPosition = () -> fake.setPos(spawn);
        server.getPlayerList().placeNewPlayer(new FakeClientConnection(PacketFlow.SERVERBOUND),fake,
                new CommonListenerCookie(profile,0,options,false));
        MinecraftCompat.teleport(fake, world, spawn);
        if (fresh) {
            fake.setGameMode(GameType.SURVIVAL);
        }
        // Offline login uses the owner-provided server coordinates after saved data loads; retain inventory and health.
        return fake;
    }
}


