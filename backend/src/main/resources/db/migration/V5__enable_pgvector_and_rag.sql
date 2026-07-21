CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_documents (
                                     id UUID PRIMARY KEY,

                                     project_id UUID NOT NULL,

                                     file_name VARCHAR(255) NOT NULL,
                                     content_type VARCHAR(100),

                                     status VARCHAR(50) NOT NULL,

                                     created_at TIMESTAMP NOT NULL,
                                     updated_at TIMESTAMP NOT NULL,

                                     CONSTRAINT fk_document_project
                                         FOREIGN KEY (project_id)
                                             REFERENCES projects(id)
);

CREATE TABLE document_chunks (
                                 id UUID PRIMARY KEY,

                                 document_id UUID NOT NULL,

                                 chunk_index INT NOT NULL,

                                 content TEXT NOT NULL,

                                 embedding VECTOR(1536),

                                 created_at TIMESTAMP NOT NULL,

                                 CONSTRAINT fk_chunk_document
                                     FOREIGN KEY (document_id)
                                         REFERENCES knowledge_documents(id)
);

CREATE INDEX idx_documents_project
    ON knowledge_documents(project_id);

CREATE INDEX idx_chunks_document
    ON document_chunks(document_id);