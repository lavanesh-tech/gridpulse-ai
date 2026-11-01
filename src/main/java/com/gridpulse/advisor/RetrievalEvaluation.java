package com.gridpulse.advisor;

import java.util.List;

/**
 * Standard retrieval metrics over labelled questions.
 * hit@k = share of questions whose expected document appears in the top k chunks;
 * MRR = mean of 1/rank of the first chunk from the expected document (0 if absent).
 */
public record RetrievalEvaluation(int questions, int k, double hitAtK, double meanReciprocalRank, List<CaseResult> cases) {

    public record CaseResult(String question, String expectedDocument, Integer rank, List<String> retrievedDocuments) {
    }

    public static RetrievalEvaluation of(int k, List<CaseResult> cases) {
        if (cases.isEmpty()) return new RetrievalEvaluation(0, k, 0, 0, cases);
        int hits = 0;
        double rr = 0;
        for (CaseResult c : cases) {
            if (c.rank() != null && c.rank() <= k) hits++;
            if (c.rank() != null) rr += 1.0 / c.rank();
        }
        return new RetrievalEvaluation(cases.size(), k, round((double) hits / cases.size()), round(rr / cases.size()), List.copyOf(cases));
    }

    /** 1-based rank of the first retrieved chunk that belongs to {@code expected}, or null. */
    public static Integer rankOf(String expected, List<String> retrievedDocuments) {
        for (int i = 0; i < retrievedDocuments.size(); i++) {
            if (retrievedDocuments.get(i).equals(expected)) return i + 1;
        }
        return null;
    }

    private static double round(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }
}
