package dev.autostock.core;

import java.util.function.ToIntFunction;

public final class HudLayout {
    public static final int PANEL_WIDTH = 100;
    public static final int REGION_HEIGHT = 48;
    public static final int TASK_HEIGHT = 70;

    private HudLayout() {
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }

    public record Snap(Rect rect, Integer vertical, Integer horizontal, boolean screenX, boolean screenY) {
    }

    public static Rect panelBounds(int panel, int screenWidth, int screenHeight, float[] saved) {
        if (panel < 0 || panel > 1) {
            throw new IllegalArgumentException("HUD panel index must be 0 or 1");
        }
        int width = Math.min(PANEL_WIDTH, Math.max(0, screenWidth));
        int height = Math.min(panel == 0 ? REGION_HEIGHT : TASK_HEIGHT, Math.max(0, screenHeight));
        int x = panel == 0 ? 8 : 16 + PANEL_WIDTH;
        int y = 8;
        if (x + width > screenWidth - 8) {
            x = 8;
            y = panel == 0 ? 8 : 8 + REGION_HEIGHT + 8;
        }
        int offset = panel * 2;
        if (saved != null && saved.length >= offset + 2) {
            if (Float.isFinite(saved[offset]) && saved[offset] >= 0) {
                x = Math.round(saved[offset] * Math.max(0, screenWidth - width));
            }
            if (Float.isFinite(saved[offset + 1]) && saved[offset + 1] >= 0) {
                y = Math.round(saved[offset + 1] * Math.max(0, screenHeight - height));
            }
        }
        return clamp(new Rect(x, y, width, height), screenWidth, screenHeight);
    }

    public static String ellipsize(String value, int maxWidth, ToIntFunction<String> width) {
        String line = oneLine(value);
        if (maxWidth <= 0) {
            return "";
        }
        if (width.applyAsInt(line) <= maxWidth) {
            return line;
        }
        String ellipsis = "…";
        if (width.applyAsInt(ellipsis) > maxWidth) {
            return "";
        }
        int end = line.length();
        while (end > 0) {
            end = line.offsetByCodePoints(end, -1);
            String shortened = line.substring(0, end) + ellipsis;
            if (width.applyAsInt(shortened) <= maxWidth) {
                return shortened;
            }
        }
        return ellipsis;
    }

    public static String compactKeyLabel(String label) {
        return oneLine(label)
                .replace("LEFT_CONTROL", "Ctrl")
                .replace("RIGHT_CONTROL", "Ctrl")
                .replace("LEFT_SHIFT", "Shift")
                .replace("RIGHT_SHIFT", "Shift")
                .replace("LEFT_ALT", "Alt")
                .replace("RIGHT_ALT", "Alt")
                .replace(" + ", "+");
    }

    public static String regionHint(String cycle, String save, String close) {
        return compactKeyLabel(cycle) + " · " + compactKeyLabel(save) + "保存 · "
                + compactKeyLabel(close) + "关闭";
    }

    public static String coordinates(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public static String taskStateLabel(String state) {
        return "PAUSED".equals(state) ? "暂停" : "运行中";
    }

    private static String oneLine(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return value.replace('\n', ' ').replace('\r', ' ').strip();
    }

    public static Rect clamp(Rect rect, int width, int height) {
        return new Rect(
                Math.max(0, Math.min(rect.x, Math.max(0, width - rect.width))),
                Math.max(0, Math.min(rect.y, Math.max(0, height - rect.height))),
                rect.width,
                rect.height
        );
    }

    public static Snap snap(Rect moving, Rect other, int width, int height, int threshold) {
        int x = moving.x;
        int y = moving.y;
        int dx = threshold + 1;
        int dy = threshold + 1;
        Integer vx = null;
        Integer hy = null;
        boolean sx = false;
        boolean sy = false;
        for (int edge : new int[]{0, width}) {
            for (int offset : new int[]{0, moving.width}) {
                if (Math.abs(edge - (moving.x + offset)) < dx) {
                    dx = Math.abs(edge - (moving.x + offset));
                    x = edge - offset;
                    vx = edge;
                    sx = true;
                }
            }
        }
        for (int edge : new int[]{0, height}) {
            for (int offset : new int[]{0, moving.height}) {
                if (Math.abs(edge - (moving.y + offset)) < dy) {
                    dy = Math.abs(edge - (moving.y + offset));
                    y = edge - offset;
                    hy = edge;
                    sy = true;
                }
            }
        }
        if (other != null) {
            for (int edge : new int[]{other.x, other.x + other.width / 2, other.x + other.width}) {
                for (int offset : new int[]{0, moving.width / 2, moving.width}) {
                    if (Math.abs(edge - (moving.x + offset)) < dx) {
                        dx = Math.abs(edge - (moving.x + offset));
                        x = edge - offset;
                        vx = edge;
                        sx = false;
                    }
                }
            }
            for (int edge : new int[]{other.y, other.y + other.height / 2, other.y + other.height}) {
                for (int offset : new int[]{0, moving.height / 2, moving.height}) {
                    if (Math.abs(edge - (moving.y + offset)) < dy) {
                        dy = Math.abs(edge - (moving.y + offset));
                        y = edge - offset;
                        hy = edge;
                        sy = false;
                    }
                }
            }
        }
        var rect = clamp(new Rect(x, y, moving.width, moving.height), width, height);
        return new Snap(
                rect,
                dx <= threshold && rect.x == x ? vx : null,
                dy <= threshold && rect.y == y ? hy : null,
                sx,
                sy
        );
    }
}
