package com.verum.omnis.core;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class RulesProvider {
    private RulesProvider() {}

    private static String readAsset(Context ctx, String path) throws Exception {
        try (InputStream is = ctx.getAssets().open(path)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toString("UTF-8");
        }
    }


    public static String getConstitution(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/constitution.json");
    }

    public static String getBrains(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/brains.json");
    }

    public static String getDetectionRules(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/detection_rules.json");
    }
}