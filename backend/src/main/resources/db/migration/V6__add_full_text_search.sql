ALTER TABLE document_chunks
ADD COLUMN search_vector tsvector;

UPDATE document_chunks
SET search_vector =
    to_tsvector(
        'english',
        COALESCE(content, '')
    );

CREATE INDEX idx_document_chunks_search_vector
ON document_chunks
USING GIN(search_vector);

CREATE OR REPLACE FUNCTION update_document_chunk_search_vector()
RETURNS trigger AS
$$
BEGIN
    NEW.search_vector :=
        to_tsvector(
            'english',
            COALESCE(NEW.content, '')
        );

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_document_chunk_search_vector
BEFORE INSERT OR UPDATE OF content
ON document_chunks
FOR EACH ROW
EXECUTE FUNCTION update_document_chunk_search_vector();