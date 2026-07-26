package com.umeshowl.banking.knowledge;

import com.umeshowl.banking.knowledge.dto.KnowledgeSearchResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeSearchController {

    private final KnowledgeSearchService knowledgeSearchService;

    public KnowledgeSearchController(
            KnowledgeSearchService knowledgeSearchService
    ) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<KnowledgeSearchResult>> search(
            @RequestParam UUID projectId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") Integer limit
    ) {

        List<KnowledgeSearchResult> results =
                knowledgeSearchService.search(
                        projectId,
                        query,
                        limit
                );

        return ResponseEntity.ok(results);
    }
}