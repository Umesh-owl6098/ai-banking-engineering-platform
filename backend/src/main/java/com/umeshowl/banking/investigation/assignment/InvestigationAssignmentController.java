package com.umeshowl.banking.investigation.assignment;

import com.umeshowl.banking.investigation.assignment.dto.AnalystQueueResponse;
import com.umeshowl.banking.investigation.assignment.dto.AssignInvestigationRequest;
import com.umeshowl.banking.investigation.assignment.dto.AssignableAnalystResponse;
import com.umeshowl.banking.investigation.dto.InvestigationCaseResponse;
import com.umeshowl.banking.auth.User;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class InvestigationAssignmentController {

    private final InvestigationAssignmentService assignmentService;
    private final AnalystQueueService analystQueueService;

    public InvestigationAssignmentController(
            InvestigationAssignmentService assignmentService,
            AnalystQueueService analystQueueService
    ) {
        this.assignmentService = assignmentService;
        this.analystQueueService = analystQueueService;
    }

    @GetMapping("/analyst-queue")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'SUPERVISOR',
                'FRAUD_ANALYST',
                'COMPLIANCE_ANALYST',
                'READ_ONLY'
            )
            """)
    public AnalystQueueResponse getAnalystQueue(
            @RequestParam(required = false) UUID projectId
    ) {
        return analystQueueService.loadQueue(projectId);
    }

    @PostMapping("/investigations/{investigationId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public InvestigationCaseResponse assignInvestigation(
            @PathVariable UUID investigationId,
            @Valid @RequestBody AssignInvestigationRequest request
    ) {
        return assignmentService.assign(investigationId, request);
    }

    @PostMapping("/investigations/{investigationId}/unassign")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public InvestigationCaseResponse unassignInvestigation(
            @PathVariable UUID investigationId
    ) {
        return assignmentService.unassign(investigationId);
    }

    @PostMapping("/investigations/{investigationId}/claim")
    @PreAuthorize("hasAnyRole('FRAUD_ANALYST','COMPLIANCE_ANALYST')")
    public InvestigationCaseResponse claimInvestigation(
            @PathVariable UUID investigationId
    ) {
        return assignmentService.claim(investigationId);
    }

    @GetMapping("/analysts")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public List<AssignableAnalystResponse> listAssignableAnalysts() {
        return assignmentService.listAssignableAnalysts().stream()
                .map(user -> new AssignableAnalystResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getRole()
                ))
                .toList();
    }
}
