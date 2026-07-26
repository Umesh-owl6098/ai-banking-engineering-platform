package com.umeshowl.banking.chat;

public record SourceReference(
        String fileName,
        Integer chunkIndex,
        Double similarity,
        String preview
) {
}