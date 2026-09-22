package dev.autostock.server;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Set;

/** Keeps small Minecraft API signature changes out of the task state machines. */
public final class MinecraftCompat {
    private MinecraftCompat() { }

    public static ServerLevel world(ServerPlayer player) {
        //? if >1.21.8 {
        return player.level();
        //?} else {
        /*return (ServerWorld) player.getWorld();
        *///?}
    }

    public static Level world(Entity entity) {
        //? if >1.21.8 {
        return entity.level();
        //?} else {
        /*return entity.getWorld();
        *///?}
    }

    public static MinecraftServer server(ServerPlayer player) {
        //? if >1.21.8 {
        return player.level().getServer();
        //?} else {
        /*return player.getServer();
        *///?}
    }

    public static Vec3 position(Entity entity) {
        //? if >1.21.8 {
        return entity.position();
        //?} else {
        /*return entity.getPos();
        *///?}
    }

    public static boolean isHost(MinecraftServer server, ServerPlayer player) {
        //? if >1.21.8 {
        return server.isSingleplayerOwner(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
        //?} else {
        /*return server.isHost(player.getGameProfile());
        *///?}
    }

    public static boolean hasPermission(ServerPlayer player, int level) {
        //? if <=1.21.10 {
        /*return player.hasPermissionLevel(level);
        *///?} else {
        return player.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(
                net.minecraft.server.permissions.PermissionLevel.byId(level)));
        //?}
    }

    public static boolean hasPermission(net.minecraft.commands.CommandSourceStack source, int level) {
        //? if <=1.21.10 {
        /*return source.hasPermissionLevel(level);
        *///?} else {
        return source.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(
                net.minecraft.server.permissions.PermissionLevel.byId(level)));
        //?}
    }

    static Holder<Attribute> movementSpeed() {
        //? if <=1.21.1 {
        /*return EntityAttributes.GENERIC_MOVEMENT_SPEED;
        *///?} else {
        return Attributes.MOVEMENT_SPEED;
        //?}
    }

    static void teleport(ServerPlayer player, ServerLevel world, Vec3 position) {
        //? if <=1.21.1 {
        /*player.teleport(world, position.x, position.y, position.z, Set.of(), 0, 0);
        *///?} else {
        player.teleportTo(world, position.x, position.y, position.z, Set.of(), 0, 0, true);
        //?}
    }
}
