package com.umeshowl.banking.knowledge.dto;

import java.util.UUID;

public class UploadDocumentResponse {

    private UUID documentId;
    private String fileName;
    private String status;

    public UploadDocumentResponse() {
    }

    public UploadDocumentResponse(UUID documentId, String fileName, String status) {
        this.documentId = documentId;
        this.fileName = fileName;
        this.status = status;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}