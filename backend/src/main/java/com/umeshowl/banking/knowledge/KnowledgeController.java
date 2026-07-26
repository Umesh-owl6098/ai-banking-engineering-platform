package com.umeshowl.banking.knowledge;

import com.umeshowl.banking.knowledge.dto.UploadDocumentResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<UploadDocumentResponse> uploadDocument(
            @RequestParam("projectId") UUID projectId,
            @RequestPart("file") MultipartFile file) {

        return ResponseEntity.ok(
                knowledgeService.upload(projectId, file)
        );
    }
}