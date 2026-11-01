package com.gridpulse.advisor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * pgvector persistence with plain SQL. Every query is filtered by tenant in the WHERE clause, so retrieval can
 * never return another tenant's text regardless of what the question or the model asks for.
 */
@Repository
public class KnowledgeStore {
    private final JdbcTemplate jdbc;
    private volatile Boolean iterativeScan;

    KnowledgeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * HNSW is approximate and applies WHERE filters after finding candidates, so with many tenants in one index a
     * tenant's own chunks can be crowded out. Widen the candidate list, and on pgvector 0.8+ keep scanning until
     * enough rows pass the filter. Must run inside the caller's transaction (SET LOCAL).
     */
    void prepareFilteredSearch() {
        if (iterativeScan == null) {
            String version = jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
            String[] parts = version == null ? new String[]{"0", "0"} : version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            iterativeScan = major > 0 || minor >= 8;
        }
        jdbc.execute("SET LOCAL hnsw.ef_search = 200");
        if (iterativeScan) jdbc.execute("SET LOCAL hnsw.iterative_scan = relaxed_order");
    }

    public record DocumentRow(UUID id, String title, String source, DocType type, Instant createdAt, int chunks) {
    }

    public record ChunkHit(UUID documentId, String title, String source, int chunkIndex, String content, double score) {
    }

    Optional<UUID> findBySha(UUID tenantId, String sha) {
        return jdbc.query("SELECT id FROM knowledge_document WHERE tenant_id = ? AND content_sha256 = ?",
                (rs, i) -> UUID.fromString(rs.getString("id")), tenantId, sha).stream().findFirst();
    }

    void insertDocument(UUID id, UUID tenantId, String title, String source, DocType type, String sha, String actor) {
        jdbc.update("""
                INSERT INTO knowledge_document (id, tenant_id, title, source, doc_type, content_sha256, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, title, source, type.name(), sha, actor);
    }

    void insertChunk(UUID documentId, UUID tenantId, int index, String content, String model, float[] vector) {
        jdbc.update("""
                INSERT INTO knowledge_chunk (id, document_id, tenant_id, chunk_index, content, embedding_model, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?::vector)
                """, UUID.randomUUID(), documentId, tenantId, index, content, model, toLiteral(vector));
    }

    List<DocumentRow> list(UUID tenantId) {
        return jdbc.query("""
                SELECT d.id, d.title, d.source, d.doc_type, d.created_at, count(c.id) AS chunks
                FROM knowledge_document d LEFT JOIN knowledge_chunk c ON c.document_id = d.id
                WHERE d.tenant_id = ?
                GROUP BY d.id ORDER BY d.created_at DESC, d.title
                """, (rs, i) -> new DocumentRow(UUID.fromString(rs.getString("id")), rs.getString("title"),
                rs.getString("source"), DocType.valueOf(rs.getString("doc_type")),
                rs.getTimestamp("created_at").toInstant(), rs.getInt("chunks")), tenantId);
    }

    boolean delete(UUID tenantId, UUID documentId) {
        return jdbc.update("DELETE FROM knowledge_document WHERE id = ? AND tenant_id = ?", documentId, tenantId) > 0;
    }

    /** Top-k chunks by cosine similarity (1 - cosine distance), for one tenant and one embedding model. */
    List<ChunkHit> search(UUID tenantId, String model, float[] query, int k, DocType type) {
        prepareFilteredSearch();
        String vector = toLiteral(query);
        String typeFilter = type == null ? "" : " AND d.doc_type = ?";
        Object[] args = type == null
                ? new Object[]{vector, tenantId, model, vector, k}
                : new Object[]{vector, tenantId, model, type.name(), vector, k};
        return jdbc.query("""
                SELECT c.document_id, d.title, d.source, c.chunk_index, c.content,
                       1 - (c.embedding <=> ?::vector) AS score
                FROM knowledge_chunk c JOIN knowledge_document d ON d.id = c.document_id
                WHERE c.tenant_id = ? AND c.embedding_model = ?""" + typeFilter + """

                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
                """, (rs, i) -> new ChunkHit(UUID.fromString(rs.getString("document_id")), rs.getString("title"),
                rs.getString("source"), rs.getInt("chunk_index"), rs.getString("content"), rs.getDouble("score")), args)
                .stream()
                // relaxed_order may return near-ties out of order; restore exact order by score
                .sorted(java.util.Comparator.comparingDouble(ChunkHit::score).reversed())
                .toList();
    }

    static String toLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.isFinite(v[i]) ? v[i] : 0f);
        }
        return sb.append(']').toString();
    }

}
