package dev.autostock.core;

public record RegionBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public RegionBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("选区最小坐标超过最大坐标");
        }
        if (minX < -30_000_000 || maxX > 30_000_000 || minZ < -30_000_000
                || maxZ > 30_000_000 || minY < -2048 || maxY > 2048) {
            throw new IllegalArgumentException("选区坐标超出支持范围");
        }
    }

    public static RegionBox between(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new RegionBox(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public long volume() {
        return Math.multiplyExact(Math.multiplyExact((long) maxX - minX + 1,
                (long) maxY - minY + 1), (long) maxZ - minZ + 1);
    }

    public boolean overlaps(RegionBox other) {
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }
}
