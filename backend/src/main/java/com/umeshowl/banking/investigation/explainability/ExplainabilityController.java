package com.umeshowl.banking.investigation.explainability;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/investigations")
public class ExplainabilityController {

    private final ExplainabilityService explainabilityService;

    public ExplainabilityController(
            ExplainabilityService explainabilityService
    ) {
        this.explainabilityService = explainabilityService;
    }

    @GetMapping("/{investigationId}/explainability")
    public List<ExplainabilityResponse> explainInvestigation(
            @PathVariable UUID investigationId
    ) {
        return explainabilityService.explainInvestigation(investigationId);
    }

    @GetMapping("/{investigationId}/findings/{findingId}/explainability")
    public ExplainabilityResponse explainFinding(
            @PathVariable UUID investigationId,
            @PathVariable UUID findingId
    ) {
        ExplainabilityResponse response =
                explainabilityService.explainFinding(findingId);

        if (!investigationId.equals(response.investigationId())) {
            throw new IllegalArgumentException(
                    "Finding does not belong to investigation "
                            + investigationId
            );
        }

        return response;
    }
}
