package com.gridpulse.advisor;

import java.util.List;

/**
 * Turns text into vectors. {@link #name()} is stored with every chunk so vectors from different models are never
 * compared (switching models requires re-ingesting).
 */
public interface EmbeddingProvider {
    String name();

    List<float[]> embed(List<String> texts);

    /** Similarity below which a chunk is treated as irrelevant (scales differ between models). */
    double minScore();
}
