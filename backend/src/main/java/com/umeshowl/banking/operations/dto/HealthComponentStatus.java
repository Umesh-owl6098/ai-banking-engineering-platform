package com.umeshowl.banking.operations.dto;

public record HealthComponentStatus(
        String component,
        String status,
        String message
) {
}
