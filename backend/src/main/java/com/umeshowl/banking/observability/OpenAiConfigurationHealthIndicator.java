package com.umeshowl.banking.observability;

import com.umeshowl.banking.chat.OpenAiService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class OpenAiConfigurationHealthIndicator implements HealthIndicator {

    private final OpenAiService openAiService;

    public OpenAiConfigurationHealthIndicator(OpenAiService openAiService) {
        this.openAiService = openAiService;
    }

    @Override
    public Health health() {
        boolean configured = openAiService.isConfigured();

        Health.Builder builder = Health.up()
                .withDetail("configured", configured);

        if (configured) {
            builder.withDetail(
                    "status",
                    "OpenAI API key is configured"
            );
        } else {
            builder.withDetail(
                    "status",
                    "OpenAI API key is not configured; deterministic fallback is available"
            );
        }

        return builder.build();
    }
}
