package com.umeshowl.banking.knowledge;

import com.umeshowl.banking.chat.OpenAiService;
import com.umeshowl.banking.knowledge.dto.UploadDocumentResponse;
import com.umeshowl.banking.project.Project;
import com.umeshowl.banking.project.ProjectRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private static final Logger log =
            LoggerFactory.getLogger(KnowledgeService.class);

    private static final int CHUNK_SIZE = 1500;
    private static final int CHUNK_OVERLAP = 200;

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ProjectRepository projectRepository;
    private final OpenAiService openAiService;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeService(
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            DocumentChunkRepository documentChunkRepository,
            ProjectRepository projectRepository,
            OpenAiService openAiService,
            JdbcTemplate jdbcTemplate
    ) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.projectRepository = projectRepository;
        this.openAiService = openAiService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public UploadDocumentResponse upload(
            UUID projectId,
            MultipartFile file
    ) {

        validateFile(file);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Project not found: " + projectId
                        )
                );

        KnowledgeDocument document = new KnowledgeDocument();

        document.setId(UUID.randomUUID());
        document.setProject(project);
        document.setFileName(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setStatus("PROCESSING");
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());

        knowledgeDocumentRepository.save(document);

        try {
            String extractedText = extractText(file);

            log.info(
                    "Extracted {} characters from PDF: {}",
                    extractedText.length(),
                    file.getOriginalFilename()
            );

            List<String> textChunks =
                    splitIntoChunks(extractedText);

            List<DocumentChunk> documentChunks =
                    createDocumentChunks(document, textChunks);

            /*
             * saveAllAndFlush is important because the rows must exist
             * before JdbcTemplate updates the embedding column.
             */
            documentChunkRepository.saveAllAndFlush(documentChunks);


            log.info(
                    "Created {} chunks for document {}",
                    documentChunks.size(),
                    document.getId()
            );

            generateAndStoreEmbeddings(documentChunks);

            document.setStatus("PROCESSED");
            document.setUpdatedAt(LocalDateTime.now());

            knowledgeDocumentRepository.save(document);

            log.info(
                    "Document {} processed successfully with {} embeddings",
                    document.getId(),
                    documentChunks.size()
            );

        } catch (Exception exception) {

            document.setStatus("FAILED");
            document.setUpdatedAt(LocalDateTime.now());

            knowledgeDocumentRepository.save(document);

            log.error(
                    "Document processing failed for document {}",
                    document.getId(),
                    exception
            );

            throw new IllegalStateException(
                    "Document processing failed: "
                            + exception.getMessage(),
                    exception
            );
        }

        return new UploadDocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getStatus()
        );
    }

    private List<DocumentChunk> createDocumentChunks(
            KnowledgeDocument document,
            List<String> textChunks
    ) {

        List<DocumentChunk> documentChunks =
                new ArrayList<>();

        LocalDateTime createdAt = LocalDateTime.now();

        for (int index = 0; index < textChunks.size(); index++) {

            DocumentChunk chunk = new DocumentChunk();

            chunk.setId(UUID.randomUUID());
            chunk.setDocument(document);
            chunk.setChunkIndex(index);
            chunk.setContent(textChunks.get(index));
            chunk.setCreatedAt(createdAt);

            documentChunks.add(chunk);
        }

        return documentChunks;
    }

    private void generateAndStoreEmbeddings(
            List<DocumentChunk> documentChunks
    ) {

        int totalChunks = documentChunks.size();

        for (int index = 0; index < totalChunks; index++) {

            DocumentChunk chunk = documentChunks.get(index);

            log.info(
                    "Generating embedding for chunk {}/{}",
                    index + 1,
                    totalChunks
            );

            List<Float> embedding =
                    openAiService.generateEmbedding(
                            chunk.getContent()
                    );

            if (embedding.size() != 1536) {
                throw new IllegalStateException(
                        "Expected 1536 embedding dimensions but received "
                                + embedding.size()
                );
            }

            storeEmbedding(
                    chunk.getId(),
                    embedding
            );
        }
    }

    private void storeEmbedding(
            UUID chunkId,
            List<Float> embedding
    ) {

        String vectorValue = embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(
                        ",",
                        "[",
                        "]"
                ));

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE document_chunks
                SET embedding = CAST(? AS vector)
                WHERE id = ?
                """,
                vectorValue,
                chunkId
        );

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Failed to save embedding for chunk: "
                            + chunkId
            );
        }
    }

    private String extractText(MultipartFile file) {

        try (
                PDDocument pdfDocument =
                        Loader.loadPDF(file.getBytes())
        ) {

            PDFTextStripper textStripper =
                    new PDFTextStripper();

            String extractedText =
                    textStripper.getText(pdfDocument);

            if (extractedText == null
                    || extractedText.isBlank()) {

                throw new IllegalStateException(
                        "No readable text was found in the uploaded PDF"
                );
            }

            return normalizeText(extractedText);

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to extract text from PDF: "
                            + file.getOriginalFilename(),
                    exception
            );
        }
    }

    private String normalizeText(String text) {

        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private List<String> splitIntoChunks(String text) {

        List<String> chunks = new ArrayList<>();

        int start = 0;

        while (start < text.length()) {

            int end = Math.min(
                    start + CHUNK_SIZE,
                    text.length()
            );

            if (end < text.length()) {

                int paragraphBreak =
                        text.lastIndexOf("\n\n", end);

                int sentenceBreak =
                        text.lastIndexOf(". ", end);

                if (paragraphBreak
                        > start + CHUNK_SIZE / 2) {

                    end = paragraphBreak;

                } else if (sentenceBreak
                        > start + CHUNK_SIZE / 2) {

                    end = sentenceBreak + 1;
                }
            }

            String chunk =
                    text.substring(start, end).trim();

            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            if (end >= text.length()) {
                break;
            }

            int nextStart =
                    end - CHUNK_OVERLAP;

            if (nextStart <= start) {
                nextStart = end;
            }

            start = nextStart;
        }

        return chunks;
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "A non-empty PDF file is required"
            );
        }

        String contentType =
                file.getContentType();

        if (!"application/pdf"
                .equalsIgnoreCase(contentType)) {

            throw new IllegalArgumentException(
                    "Only PDF files are supported"
            );
        }
    }
}