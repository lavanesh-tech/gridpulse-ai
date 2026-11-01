package com.gridpulse.advisor;

import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.List;

/** Provider backed by a Spring AI {@link EmbeddingModel} (OpenAI text-embedding-3-small: 1536 dimensions). */
final class ModelEmbeddingProvider implements EmbeddingProvider {
    private static final int BATCH = 64;

    private final EmbeddingModel model;
    private final String name;
    private final double minScore;

    ModelEmbeddingProvider(EmbeddingModel model, String name, double minScore) {
        this.model = model;
        this.name = name;
        this.minScore = minScore;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH) {
            List<float[]> batch = model.embed(texts.subList(i, Math.min(texts.size(), i + BATCH)));
            for (float[] v : batch) {
                if (v.length != HashingEmbedder.DIMENSIONS) {
                    throw new IllegalStateException("embedding model " + name + " returned " + v.length
                            + " dimensions; the knowledge base stores " + HashingEmbedder.DIMENSIONS);
                }
                out.add(v);
            }
        }
        return out;
    }

    @Override
    public double minScore() {
        return minScore;
    }
}
