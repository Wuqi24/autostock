package dev.autostock.server;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.Entity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Set;

/** Keeps small Minecraft API signature changes out of the task state machines. */
public final class MinecraftCompat {
    private MinecraftCompat() { }

    public static ServerWorld world(ServerPlayerEntity player) {
        //? if >1.21.8 {
        /*return player.getEntityWorld();
        *///?} else {
        return (ServerWorld) player.getWorld();
        //?}
    }

    public static World world(Entity entity) {
        //? if >1.21.8 {
        /*return entity.getEntityWorld();
        *///?} else {
        return entity.getWorld();
        //?}
    }

    public static MinecraftServer server(ServerPlayerEntity player) {
        //? if >1.21.8 {
        /*return player.getEntityWorld().getServer();
        *///?} else {
        return player.getServer();
        //?}
    }

    public static Vec3d position(Entity entity) {
        //? if >1.21.8 {
        /*return entity.getEntityPos();
        *///?} else {
        return entity.getPos();
        //?}
    }

    public static boolean isHost(MinecraftServer server, ServerPlayerEntity player) {
        //? if >1.21.8 {
        /*return server.isHost(new net.minecraft.server.PlayerConfigEntry(player.getGameProfile()));
        *///?} else {
        return server.isHost(player.getGameProfile());
        //?}
    }

    public static boolean hasPermission(ServerPlayerEntity player, int level) {
        //? if <=1.21.10 {
        return player.hasPermissionLevel(level);
        //?} else {
        /*return player.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(
                net.minecraft.command.permission.PermissionLevel.fromLevel(level)));
        *///?}
    }

    public static boolean hasPermission(net.minecraft.server.command.ServerCommandSource source, int level) {
        //? if <=1.21.10 {
        return source.hasPermissionLevel(level);
        //?} else {
        /*return source.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(
                net.minecraft.command.permission.PermissionLevel.fromLevel(level)));
        *///?}
    }

    static RegistryEntry<EntityAttribute> movementSpeed() {
        //? if <=1.21.1 {
        /*return EntityAttributes.GENERIC_MOVEMENT_SPEED;
        *///?} else {
        return EntityAttributes.MOVEMENT_SPEED;
        //?}
    }

    static void teleport(ServerPlayerEntity player, ServerWorld world, Vec3d position) {
        //? if <=1.21.1 {
        /*player.teleport(world, position.x, position.y, position.z, Set.of(), 0, 0);
        *///?} else {
        player.teleport(world, position.x, position.y, position.z, Set.of(), 0, 0, true);
        //?}
    }
}
