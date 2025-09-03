package com.verum.omnis.security;

import android.content.Context;
import android.content.res.AssetManager;

import com.verum.omnis.core.HashUtil;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IntegrityChecker {

    private IntegrityChecker() {}

    /** Run checks and return map: assetPath → status message */
    public static Map<String, String> runChecks(Context ctx) {
        Map<String, String> results = new LinkedHashMap<>();
        try {
            AssetManager am = ctx.getAssets();

            Map<String, String> checks = new LinkedHashMap<>();
            checks.put("verum_constitution/constitution.json",
                    ExpectedHashes.VERUM_CONSTITUTION_CONSTITUTION_JSON);
            checks.put("verum_constitution/brains.json",
                    ExpectedHashes.VERUM_CONSTITUTION_BRAINS_JSON);
            checks.put("verum_constitution/detection_rules.json",
                    ExpectedHashes.VERUM_CONSTITUTION_DETECTION_RULES_JSON);
            checks.put("verum_constitution/model_hashes.json",
                    ExpectedHashes.VERUM_CONSTITUTION_MODEL_HASHES_JSON);
            checks.put("verum_constitution/jurisdiction_packs.json",
                    ExpectedHashes.VERUM_CONSTITUTION_JURISDICTION_PACKS_JSON);

            checks.put("docs/Verum_Omnis_Constitution_Core.pdf",
                    ExpectedHashes.TEMPLATES_VERUM_OMNIS_CONSTITUTION_CORE_PDF);

            for (Map.Entry<String, String> e : checks.entrySet()) {
                String assetPath = e.getKey();
                String expected = e.getValue();
                try {
                    byte[] data = readAsset(am, assetPath);
                    String actual = HashUtil.sha512(data);
                    if (expected.equalsIgnoreCase(actual)) {
                        results.put(assetPath, "✔ OK");
                    } else {
                        results.put(assetPath, "❌ Tampered!\nExpected: " +
                                HashUtil.truncate(expected, 12) +
                                "...\nActual: " +
                                HashUtil.truncate(actual, 12) + "...");
                    }
                } catch (Exception ex) {
                    results.put(assetPath, "⚠ Missing or unreadable (" + ex.getMessage() + ")");
                }
            }
        } catch (Exception ex) {
            results.put("GLOBAL", "Integrity check error: " + ex.getMessage());
        }
        return results;
    }

    private static byte[] readAsset(AssetManager am, String path) throws Exception {
        try (InputStream is = am.open(path)) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            while ((n = is.read(tmp)) != -1) buffer.write(tmp, 0, n);
            return buffer.toByteArray();
        }
    }
}
