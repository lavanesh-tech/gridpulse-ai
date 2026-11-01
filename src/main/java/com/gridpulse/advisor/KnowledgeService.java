package com.gridpulse.advisor;

import com.gridpulse.audit.AuditLog;
import com.gridpulse.shared.error.NotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Document ingestion (chunk → embed → store), tenant-scoped retrieval and retrieval evaluation. */
@Service
public class KnowledgeService {
    static final int MAX_CHARS = 200_000;

    private final KnowledgeStore store;
    private final EmbeddingProvider embeddings;
    private final AuditLog audit;
    private final TextChunker chunker = new TextChunker(800, 150);

    KnowledgeService(KnowledgeStore store, EmbeddingProvider embeddings, AuditLog audit) {
        this.store = store;
        this.embeddings = embeddings;
        this.audit = audit;
    }

    public record IngestResult(UUID documentId, String title, int chunks, boolean duplicate, String embeddingModel) {
    }

    public record Source(int ref, UUID documentId, String title, String source, int chunkIndex, double score, String text) {
    }

    public record EvalCase(String question, String expectedDocumentTitle) {
    }

    public String embeddingModel() {
        return embeddings.name();
    }

    /**
     * Idempotent: identical content (SHA-256) is stored once per tenant. If the document exists but has no vectors
     * from the current embedding model (the model was switched), its chunks are embedded again for that model.
     */
    @Transactional
    public IngestResult ingest(UUID tenantId, String actor, String title, String source, DocType type, String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content must not be empty");
        if (content.length() > MAX_CHARS) throw new IllegalArgumentException("content is limited to " + MAX_CHARS + " characters");
        String sha = sha256(content);
        var existing = store.findBySha(tenantId, sha);
        if (existing.isPresent() && store.hasChunks(existing.get(), embeddings.name())) {
            return new IngestResult(existing.get(), title, 0, true, embeddings.name());
        }
        List<String> chunks = chunker.chunk(content);
        List<float[]> vectors = embeddings.embed(chunks);
        UUID id = existing.orElseGet(UUID::randomUUID);
        if (existing.isEmpty()) {
            store.insertDocument(id, tenantId, title.strip(), source, type, sha, actor);
        }
        for (int i = 0; i < chunks.size(); i++) {
            store.insertChunk(id, tenantId, i, chunks.get(i), embeddings.name(), vectors.get(i));
        }
        audit.record(tenantId, actor, "INGEST_DOCUMENT", "knowledge_document", id.toString(),
                Map.of("title", title, "chunks", chunks.size(), "embeddingModel", embeddings.name()));
        return new IngestResult(id, title, chunks.size(), false, embeddings.name());
    }

    /** Loads the bundled synthetic sample documents (classpath:knowledge/*.md). Safe to call repeatedly. */
    @Transactional
    public List<IngestResult> loadSamples(UUID tenantId, String actor) {
        List<IngestResult> results = new ArrayList<>();
        for (Resource r : resources("classpath:knowledge/*.md")) {
            String text = read(r);
            String title = text.lines().findFirst().orElse(r.getFilename()).replaceAll("^#+\\s*", "").strip();
            results.add(ingest(tenantId, actor, title, "sample:" + r.getFilename(), typeFor(r.getFilename()), text));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeStore.DocumentRow> list(UUID tenantId) {
        return store.list(tenantId);
    }

    @Transactional
    public void delete(UUID tenantId, String actor, UUID documentId) {
        if (!store.delete(tenantId, documentId)) throw new NotFoundException("document", documentId);
        audit.record(tenantId, actor, "DELETE_DOCUMENT", "knowledge_document", documentId.toString(), Map.of());
    }

    /** Relevant chunks only (score ≥ provider threshold), numbered S1..Sn for citation. */
    @Transactional(readOnly = true)
    public List<Source> retrieve(UUID tenantId, String query, int k, DocType type) {
        float[] q = embeddings.embed(List.of(query)).get(0);
        List<Source> out = new ArrayList<>();
        for (KnowledgeStore.ChunkHit h : store.search(tenantId, embeddings.name(), q, k, type)) {
            if (h.score() < embeddings.minScore()) continue;
            out.add(new Source(out.size() + 1, h.documentId(), h.title(), h.source(), h.chunkIndex(),
                    Math.round(h.score() * 10_000) / 10_000.0, h.content()));
        }
        return out;
    }

    /** hit@k and MRR; ranks are taken from the raw top-k (before the relevance threshold). */
    @Transactional(readOnly = true)
    public RetrievalEvaluation evaluate(UUID tenantId, List<EvalCase> cases, int k) {
        List<RetrievalEvaluation.CaseResult> results = new ArrayList<>();
        for (EvalCase c : cases) {
            float[] q = embeddings.embed(List.of(c.question())).get(0);
            List<String> titles = store.search(tenantId, embeddings.name(), q, k, null).stream()
                    .map(KnowledgeStore.ChunkHit::title).toList();
            results.add(new RetrievalEvaluation.CaseResult(c.question(), c.expectedDocumentTitle(),
                    RetrievalEvaluation.rankOf(c.expectedDocumentTitle(), titles), titles));
        }
        return RetrievalEvaluation.of(k, results);
    }

    /** The bundled evaluation set for the sample documents (classpath:knowledge/eval-cases.tsv). */
    public List<EvalCase> sampleEvalCases() {
        List<EvalCase> cases = new ArrayList<>();
        for (Resource r : resources("classpath:knowledge/eval-cases.tsv")) {
            for (String line : read(r).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t");
                if (f.length == 2) cases.add(new EvalCase(f[0].strip(), f[1].strip()));
            }
        }
        return cases;
    }

    private static DocType typeFor(String filename) {
        if (filename == null) return DocType.OTHER;
        if (filename.contains("tariff")) return DocType.TARIFF;
        if (filename.contains("grid")) return DocType.GRID;
        if (filename.contains("procedure")) return DocType.PROCEDURE;
        if (filename.contains("policy")) return DocType.POLICY;
        return DocType.OTHER;
    }

    private static Resource[] resources(String pattern) {
        try {
            return new PathMatchingResourcePatternResolver().getResources(pattern);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Resource r) {
        try (var in = r.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
