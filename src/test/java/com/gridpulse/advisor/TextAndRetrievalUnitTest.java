package com.gridpulse.advisor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextAndRetrievalUnitTest {

    @Test
    void chunksRespectSizeKeepHeadingsAndLoseNothing() {
        StringBuilder sb = new StringBuilder("# Long section\n\n");
        for (int i = 1; i <= 40; i++) {
            sb.append("Sentence number ").append(i).append(" talks about charger ").append(i).append(" and its grid limit. ");
        }
        sb.append("\n\nShort closing paragraph.");
        List<String> chunks = new TextChunker(400, 80).chunk(sb.toString());

        assertThat(chunks).hasSizeGreaterThan(5);
        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.length()).isLessThanOrEqualTo(400);
            assertThat(c).startsWith("Long section: ");
        });
        String all = String.join(" ", chunks);
        for (int i = 1; i <= 40; i++) assertThat(all).contains("Sentence number " + i + " ");
        assertThat(chunks.get(chunks.size() - 1)).endsWith("Short closing paragraph.");
    }

    @Test
    void consecutiveChunksOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 60; i++) sb.append("Fact ").append(i).append(" is recorded here. ");
        List<String> chunks = new TextChunker(300, 60).chunk(sb.toString());
        assertThat(chunks).hasSizeGreaterThan(3);
        java.util.regex.Pattern fact = java.util.regex.Pattern.compile("Fact (\\d+) ");
        for (int i = 1; i < chunks.size(); i++) {
            java.util.regex.Matcher m = fact.matcher(chunks.get(i - 1));
            String last = null;
            while (m.find()) last = m.group(0);
            assertThat(chunks.get(i)).as("chunk %d repeats the last fact of chunk %d", i, i - 1).contains(last);
        }
    }

    @Test
    void lexicalEmbeddingIsDeterministicNormalisedAndTopical() {
        float[] a = HashingEmbedder.embed("Peak tariff price per kWh");
        float[] b = HashingEmbedder.embed("Peak tariff price per kWh");
        assertThat(a).containsExactly(b);
        double norm = 0;
        for (float x : a) norm += x * x;
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(cos(a, HashingEmbedder.embed("what is the peak price")))
                .isGreaterThan(cos(a, HashingEmbedder.embed("connector adapters are not permitted")));
        assertThat(HashingEmbedder.embed("the of and")).containsOnly(0f);
    }

    @Test
    void citationsKeepOnlyProvidedSources() {
        assertThat(Citations.referenced("Cost is 0.32 [S1]. See also [S2, S9] and [S3].", 3)).containsExactly(1, 2, 3);
        assertThat(Citations.referenced("No citations here.", 3)).isEmpty();
        assertThat(Citations.referenced(null, 3)).isEmpty();
    }

    @Test
    void retrievalMetrics() {
        var cases = List.of(
                new RetrievalEvaluation.CaseResult("q1", "A", RetrievalEvaluation.rankOf("A", List.of("A", "B")), List.of()),
                new RetrievalEvaluation.CaseResult("q2", "A", RetrievalEvaluation.rankOf("A", List.of("B", "C", "A")), List.of()),
                new RetrievalEvaluation.CaseResult("q3", "A", RetrievalEvaluation.rankOf("A", List.of("B", "C")), List.of()));
        RetrievalEvaluation e = RetrievalEvaluation.of(2, cases);
        assertThat(e.hitAtK()).isEqualTo(0.3333);          // only q1 within top 2
        assertThat(e.meanReciprocalRank()).isEqualTo(0.4444); // (1 + 1/3 + 0) / 3
    }

    private static double cos(float[] x, float[] y) {
        double d = 0;
        for (int i = 0; i < x.length; i++) d += x[i] * y[i];
        return d;
    }
}
