package com.umeshowl.banking.operations.dto;

import java.util.List;

public record PlatformHealthSummary(
        String overallStatus,
        List<HealthComponentStatus> components
) {
}
