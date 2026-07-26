package com.umeshowl.banking.investigation.execution;

import com.umeshowl.banking.investigation.InvestigationAutoExecutionTrigger;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.investigation.dto.InvestigationCaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/investigations")
public class InvestigationExecutionController {

    private static final Set<String> RETRYABLE_STATUSES = Set.of(
            "NEW",
            "EXECUTION_FAILED"
    );

    private final InvestigationAutoExecutionTrigger autoExecutionTrigger;
    private final InvestigationCaseService investigationCaseService;

    public InvestigationExecutionController(
            InvestigationAutoExecutionTrigger autoExecutionTrigger,
            InvestigationCaseService investigationCaseService
    ) {
        this.autoExecutionTrigger = autoExecutionTrigger;
        this.investigationCaseService = investigationCaseService;
    }

    @PostMapping("/{investigationId}/execute")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public InvestigationCaseResponse execute(
            @PathVariable UUID investigationId
    ) {
        String status = investigationCaseService
                .getCase(investigationId)
                .getStatus();

        if (RETRYABLE_STATUSES.contains(status)) {
            autoExecutionTrigger.trigger(investigationId);
            return InvestigationCaseResponse.from(
                    investigationCaseService.getCaseAfterExecutionAttempt(
                            investigationId
                    )
            );
        }

        if ("RUNNING".equals(status) || "REPORT_GENERATED".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Investigation execution is already in progress"
            );
        }

        return InvestigationCaseResponse.from(
                investigationCaseService.getCase(investigationId)
        );
    }
}
