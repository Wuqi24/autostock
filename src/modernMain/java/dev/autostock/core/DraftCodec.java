package dev.autostock.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class DraftCodec {
    public static final int MAX_JSON_BYTES = 24_000;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private DraftCodec() { }

    public static String encode(PlanDraft draft) {
        String json = GSON.toJson(draft);
        checkSize(json);
        return json;
    }

    public static PlanDraft decode(String json) {
        checkSize(json);
        var draft = GSON.fromJson(json, PlanDraft.class);
        if (draft == null) throw new IllegalArgumentException("计划不能为空");
        return draft;
    }

    private static void checkSize(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("计划超过基础版 24 KB 消息上限，请缩小材料清单或选区数量");
        }
    }

    public static String hash(PlanDraft draft) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(encode(draft).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
