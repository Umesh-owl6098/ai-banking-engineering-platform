package com.umeshowl.banking.knowledge;

import com.umeshowl.banking.chat.OpenAiService;
import com.umeshowl.banking.knowledge.dto.KnowledgeSearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeSearchService {

    private static final int DEFAULT_RESULT_LIMIT = 5;
    private static final int MAX_RESULT_LIMIT = 10;

    private final OpenAiService openAiService;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeSearchService(
            OpenAiService openAiService,
            JdbcTemplate jdbcTemplate
    ) {
        this.openAiService = openAiService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<KnowledgeSearchResult> search(
            UUID projectId,
            String query,
            Integer requestedLimit
    ) {

        validateQuery(query);

        int limit = resolveLimit(requestedLimit);

        List<Float> embedding =
                openAiService.generateEmbedding(query.trim());

        String vector =
                convertToVector(embedding);

        List<KnowledgeSearchResult> vectorResults =
                searchByVector(
                        projectId,
                        vector,
                        limit * 2
                );

        List<KnowledgeSearchResult> keywordResults =
                searchByKeyword(
                        projectId,
                        query,
                        limit * 2
                );

        /*
         * Merge both searches.
         * Vector results keep priority.
         */

        Map<UUID, KnowledgeSearchResult> merged =
                new LinkedHashMap<>();

        for (KnowledgeSearchResult result : vectorResults) {

            merged.put(
                    result.chunkId(),
                    result
            );
        }

        for (KnowledgeSearchResult result : keywordResults) {

            merged.putIfAbsent(
                    result.chunkId(),
                    result
            );
        }

        return merged.values()
                .stream()
                .limit(limit)
                .toList();
    }

    private List<KnowledgeSearchResult> searchByVector(
            UUID projectId,
            String vector,
            int limit
    ) {

        String sql = """
                SELECT
                    dc.id AS chunk_id,
                    kd.id AS document_id,
                    kd.file_name,
                    dc.chunk_index,
                    dc.content,
                    1 - (dc.embedding <=> CAST(? AS vector)) AS similarity
                FROM document_chunks dc
                JOIN knowledge_documents kd
                    ON kd.id = dc.document_id
                WHERE kd.project_id = ?
                AND kd.status='PROCESSED'
                AND dc.embedding IS NOT NULL
                ORDER BY dc.embedding <=> CAST(? AS vector)
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, row) ->
                        new KnowledgeSearchResult(
                                rs.getObject(
                                        "chunk_id",
                                        UUID.class
                                ),
                                rs.getObject(
                                        "document_id",
                                        UUID.class
                                ),
                                rs.getString(
                                        "file_name"
                                ),
                                rs.getInt(
                                        "chunk_index"
                                ),
                                rs.getString(
                                        "content"
                                ),
                                rs.getDouble(
                                        "similarity"
                                )
                        ),
                vector,
                projectId,
                vector,
                limit
        );
    }

    private List<KnowledgeSearchResult> searchByKeyword(
            UUID projectId,
            String query,
            int limit
    ) {

        String sql = """
                SELECT
                    dc.id AS chunk_id,
                    kd.id AS document_id,
                    kd.file_name,
                    dc.chunk_index,
                    dc.content,
                    ts_rank(
                        dc.search_vector,
                        plainto_tsquery('english', ?)
                    ) AS similarity
                FROM document_chunks dc
                JOIN knowledge_documents kd
                    ON kd.id = dc.document_id
                WHERE kd.project_id = ?
                AND kd.status='PROCESSED'
                AND dc.search_vector @@
                    plainto_tsquery('english', ?)
                ORDER BY similarity DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, row) ->
                        new KnowledgeSearchResult(
                                rs.getObject(
                                        "chunk_id",
                                        UUID.class
                                ),
                                rs.getObject(
                                        "document_id",
                                        UUID.class
                                ),
                                rs.getString(
                                        "file_name"
                                ),
                                rs.getInt(
                                        "chunk_index"
                                ),
                                rs.getString(
                                        "content"
                                ),
                                rs.getDouble(
                                        "similarity"
                                )
                        ),
                query,
                projectId,
                query,
                limit
        );
    }

    private String convertToVector(
            List<Float> embedding
    ) {

        return embedding.stream()
                .map(String::valueOf)
                .collect(
                        Collectors.joining(
                                ",",
                                "[",
                                "]"
                        )
                );
    }

    private void validateQuery(
            String query
    ) {

        if (query == null
                || query.isBlank()) {

            throw new IllegalArgumentException(
                    "Search query cannot be empty"
            );
        }

        if (query.length() > 4000) {

            throw new IllegalArgumentException(
                    "Search query cannot exceed 4000 characters"
            );
        }
    }

    private int resolveLimit(
            Integer requestedLimit
    ) {

        if (requestedLimit == null) {

            return DEFAULT_RESULT_LIMIT;
        }

        return Math.max(
                1,
                Math.min(
                        requestedLimit,
                        MAX_RESULT_LIMIT
                )
        );
    }
}