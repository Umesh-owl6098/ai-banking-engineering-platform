package com.umeshowl.banking.investigation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConfigurationProperties(prefix = "investigation.auto-create")
public class InvestigationAutoCreateProperties {

    private UUID defaultProjectId =
            UUID.fromString("8c0c0dee-dd8e-4419-bef3-a2e93c10a726");

    public UUID getDefaultProjectId() {
        return defaultProjectId;
    }

    public void setDefaultProjectId(UUID defaultProjectId) {
        this.defaultProjectId = defaultProjectId;
    }
}
