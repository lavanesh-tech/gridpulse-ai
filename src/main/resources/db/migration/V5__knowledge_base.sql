-- RAG knowledge base: documents and their embedded chunks (pgvector).
CREATE TABLE knowledge_document (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    title           VARCHAR(300) NOT NULL,
    source          VARCHAR(500),
    doc_type        VARCHAR(20) NOT NULL CHECK (doc_type IN ('POLICY','TARIFF','GRID','PROCEDURE','OTHER')),
    content_sha256  CHAR(64) NOT NULL,
    created_by      VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, content_sha256)
);

CREATE TABLE knowledge_chunk (
    id               UUID PRIMARY KEY,
    document_id      UUID NOT NULL REFERENCES knowledge_document(id) ON DELETE CASCADE,
    tenant_id        UUID NOT NULL REFERENCES tenant(id),
    chunk_index      INT NOT NULL,
    content          TEXT NOT NULL,
    -- which embedding model produced the vector; vectors from different models are never compared
    embedding_model  VARCHAR(100) NOT NULL,
    embedding        vector(1536) NOT NULL,
    UNIQUE (document_id, chunk_index, embedding_model)
);
CREATE INDEX idx_chunk_tenant_model ON knowledge_chunk(tenant_id, embedding_model);
-- Approximate nearest-neighbour index for cosine distance (<=>).
CREATE INDEX idx_chunk_embedding_hnsw ON knowledge_chunk USING hnsw (embedding vector_cosine_ops);
