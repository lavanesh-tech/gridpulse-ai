package com.gridpulse.advisor;

import java.util.List;

/** Offline provider backed by {@link HashingEmbedder}: works without an API key, matches words not meaning. */
final class LexicalEmbeddingProvider implements EmbeddingProvider {
    static final String NAME = "lexical-hash-v1";
    private final double minScore;

    LexicalEmbeddingProvider(double minScore) {
        this.minScore = minScore;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream().map(HashingEmbedder::embed).toList();
    }

    @Override
    public double minScore() {
        return minScore;
    }
}
