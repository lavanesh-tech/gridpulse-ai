package com.gridpulse.advisor;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, offline "embedding": hashed bag of lower-cased words (stop words removed), L2-normalised.
 * Captures word overlap only — no meaning. Used by tests so the RAG pipeline runs without network or cost.
 * Real deployments use the OpenAI embedding model.
 */
public final class HashingEmbedder {
    public static final int DIMENSIONS = 1536;
    private static final Set<String> STOP = Set.of("the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "is",
            "are", "be", "by", "with", "at", "as", "it", "this", "that", "what", "which", "how", "when", "do", "does",
            "can", "we", "our", "i", "my", "if", "from", "not", "no", "must", "should", "will", "per");

    private HashingEmbedder() {
    }

    public static float[] embed(String text) {
        float[] v = new float[DIMENSIONS];
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.length() < 2 || STOP.contains(raw)) continue;
            String w = stem(raw);
            int h = fnv1a(w);
            v[Math.floorMod(h, DIMENSIONS)] += (h & 0x10000) == 0 ? 1f : -1f;
        }
        double norm = 0;
        for (float x : v) norm += x * x;
        if (norm == 0) return v;
        float inv = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < v.length; i++) v[i] *= inv;
        return v;
    }

    private static String stem(String w) {
        if (w.length() > 4 && w.endsWith("ies")) return w.substring(0, w.length() - 3) + "y";
        if (w.length() > 4 && w.endsWith("ing")) return w.substring(0, w.length() - 3);
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }

    private static int fnv1a(String s) {
        int h = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= b;
            h *= 0x01000193;
        }
        return h;
    }
}
