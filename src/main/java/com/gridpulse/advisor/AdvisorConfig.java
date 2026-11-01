package com.gridpulse.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AdvisorConfig {
    private static final Logger log = LoggerFactory.getLogger(AdvisorConfig.class);

    @Bean
    EmbeddingProvider embeddingProvider(ObjectProvider<EmbeddingModel> model,
                                        @Value("${gridpulse.ai.embeddings:auto}") String mode,
                                        @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String modelName,
                                        @Value("${gridpulse.ai.min-score-lexical:0.20}") double minLexical,
                                        @Value("${gridpulse.ai.min-score-model:0.30}") double minModel) {
        EmbeddingModel available = model.getIfAvailable();
        if (!"lexical".equals(mode) && available != null) {
            log.info("Advisor embeddings: model {}", modelName);
            return new ModelEmbeddingProvider(available, modelName, minModel);
        }
        if ("model".equals(mode)) {
            throw new IllegalStateException("gridpulse.ai.embeddings=model but no EmbeddingModel is configured");
        }
        log.info("Advisor embeddings: offline lexical embedder (no embedding model configured)");
        return new LexicalEmbeddingProvider(minLexical);
    }
}
