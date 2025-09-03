package com.verum.omnis.ai;

import android.content.Context;

import com.verum.omnis.core.RulesProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Verum Omnis Rules-Only Engine (v5.1.1-derived)
 * Deterministic, no-ML scorer using template rules:
 *  - Keyword/entity scanning
 *  - Contradiction heuristics
 *  - Omission/evasion markers
 *  - Concealment patterns
 *  - Financial irregularity flags
 *
 * Rule lists are loaded from assets via RulesProvider if available;
 * otherwise fall back to hardcoded defaults.
 */
public class RulesEngine {

    public static class Result {
        public double riskScore;
        public String[] topLiabilities;
        public JSONObject diagnostics;
    }

    // Hardcoded fallback lists
    private static final List<String> KEYWORDS_FALLBACK = Arrays.asList(
            "admit","deny","forged","access","delete","refuse","invoice","profit",
            "unauthorized","breach","hack","seizure","shareholder","oppression","contract","cash"
    );
    private static final List<String> ENTITIES_FALLBACK = Arrays.asList(
            "RAKEZ","SAPS","Article 84","Greensky","UAE","EU","South Africa"
    );
    private static final List<String> EVASION_FALLBACK = Arrays.asList(
            "i don't recall","can't remember","not sure","later","stop asking","leave me alone"
    );
    private static final List<String> CONTRADICT_FALLBACK = Arrays.asList(
            "never happened","i never said","you forged","fake","that is not true","i paid","no deal","we had a deal"
    );
    private static final List<String> CONCEAL_FALLBACK = Arrays.asList(
            "delete this","use my other phone","no email","don't write","keep it off the record","use cash"
    );
    private static final List<String> FINANCIAL_FALLBACK = Arrays.asList(
            "invoice","wire","transfer","swift","bank","cash","under the table","kickback"
    );

    // Dynamic lists (overwritten if JSON asset loads)
    private static List<String> KEYWORDS = new ArrayList<>(KEYWORDS_FALLBACK);
    private static List<String> ENTITIES = new ArrayList<>(ENTITIES_FALLBACK);
    private static List<String> EVASION = new ArrayList<>(EVASION_FALLBACK);
    private static List<String> CONTRADICT = new ArrayList<>(CONTRADICT_FALLBACK);
    private static List<String> CONCEAL = new ArrayList<>(CONCEAL_FALLBACK);
    private static List<String> FINANCIAL = new ArrayList<>(FINANCIAL_FALLBACK);

    private static boolean loadedFromAssets = false;

    /**
     * Attempt to load detection rules JSON from assets (once per process).
     */
    private static void ensureRulesLoaded(Context ctx) {
        if (loadedFromAssets) return;
        try {
            String jsonText = RulesProvider.getDetectionRules(ctx);
            JSONObject obj = new JSONObject(jsonText);

            KEYWORDS = toList(obj.optJSONArray("keywords"), KEYWORDS_FALLBACK);
            ENTITIES = toList(obj.optJSONArray("entities"), ENTITIES_FALLBACK);
            EVASION = toList(obj.optJSONArray("evasion"), EVASION_FALLBACK);
            CONTRADICT = toList(obj.optJSONArray("contradictions"), CONTRADICT_FALLBACK);
            CONCEAL = toList(obj.optJSONArray("concealment"), CONCEAL_FALLBACK);
            FINANCIAL = toList(obj.optJSONArray("financial"), FINANCIAL_FALLBACK);

            loadedFromAssets = true;
            System.out.println("RulesEngine: Loaded detection rules from assets.");
        } catch (Exception e) {
            // fallback silently
            loadedFromAssets = true;
            System.out.println("RulesEngine: Using fallback hardcoded rules.");
        }
    }

    public static Result analyzeFile(Context ctx, File file) {
        ensureRulesLoaded(ctx);
        Result r = new Result();
        try {
            String text = readAll(file).toLowerCase(Locale.ROOT);

            int kw = countMatches(text, KEYWORDS);
            int ent = countMatches(text, ENTITIES);
            int ev = countMatches(text, EVASION);
            int con = countMatches(text, CONTRADICT);
            int hid = countMatches(text, CONCEAL);
            int fin = countMatches(text, FINANCIAL);

            // Heuristic scoring
            double score = (kw*0.05 + ent*0.04 + ev*0.08 + con*0.1 + hid*0.12 + fin*0.06);
            score = Math.min(1.0, score);

            List<String> liab = new ArrayList<>();
            if (con >= 2) liab.add("Contradictions in statements");
            if (hid >= 1) liab.add("Patterns of concealment");
            if (ev  >= 2) liab.add("Evasion/Gaslighting indicators");
            if (fin >= 2) liab.add("Financial irregularity signals");
            if (kw  >= 3 && ent >= 1) liab.add("Legal subject flags present");

            if (liab.isEmpty()) liab.add("General risk");

            r.riskScore = score;
            r.topLiabilities = liab.toArray(new String[0]);

            JSONObject d = new JSONObject();
            d.put("keywords", kw);
            d.put("entities", ent);
            d.put("evasion", ev);
            d.put("contradictions", con);
            d.put("concealment", hid);
            d.put("financial", fin);
            r.diagnostics = d;

            return r;
        } catch (Exception e) {
            r.riskScore = 0.0;
            r.topLiabilities = new String[]{"Rules engine error: " + e.getMessage()};
            r.diagnostics = new JSONObject();
            return r;
        }
    }

    private static int countMatches(String text, List<String> needles) {
        int total = 0;
        for (String n : needles) {
            int idx = 0;
            while (true) {
                idx = text.indexOf(n.toLowerCase(Locale.ROOT), idx);
                if (idx == -1) break;
                total++; idx += n.length();
            }
        }
        return total;
    }

    private static String readAll(File f) throws Exception {
        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            bytes = bos.toByteArray();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<String> toList(JSONArray arr, List<String> fallback) {
        if (arr == null) return fallback;
        List<String> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.optString(i));
        }
        return list.isEmpty() ? fallback : list;
    }
}
