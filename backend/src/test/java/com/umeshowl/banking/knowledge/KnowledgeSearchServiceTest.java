package com.umeshowl.banking.knowledge;

import com.umeshowl.banking.chat.OpenAiService;
import com.umeshowl.banking.knowledge.dto.KnowledgeSearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeSearchServiceTest {

    @Mock
    private OpenAiService openAiService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private KnowledgeSearchService knowledgeSearchService;

    @Test
    void search_fallsBackToKeywordSearchWhenEmbeddingFails() {
        UUID projectId = UUID.randomUUID();

        when(openAiService.generateEmbedding(anyString()))
                .thenThrow(new IllegalStateException("embedding failed"));

        KnowledgeSearchResult keywordResult =
                new KnowledgeSearchResult(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "account-policy.pdf",
                        0,
                        "Account closure policy",
                        0.75d
                );

        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(),
                eq(projectId),
                any(),
                anyInt()
        )).thenReturn(List.of(keywordResult));

        List<KnowledgeSearchResult> results =
                knowledgeSearchService.search(
                        projectId,
                        "close my account",
                        5
                );

        assertThat(results).containsExactly(keywordResult);
    }
}
