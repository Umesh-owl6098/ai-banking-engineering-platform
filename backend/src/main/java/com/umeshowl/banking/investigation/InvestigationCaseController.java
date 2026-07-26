package com.umeshowl.banking.investigation;

import com.umeshowl.banking.investigation.dto.AgentFindingResponse;
import com.umeshowl.banking.investigation.dto.InvestigationCaseCreateRequest;
import com.umeshowl.banking.investigation.dto.InvestigationCaseResponse;
import com.umeshowl.banking.investigation.dto.InvestigationStatusUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class InvestigationCaseController {

    private final InvestigationCaseService
            investigationCaseService;
    private final AgentFindingService agentFindingService;
    private final InvestigationNotificationHub investigationNotificationHub;

    public InvestigationCaseController(
            InvestigationCaseService investigationCaseService,
            AgentFindingService agentFindingService,
            InvestigationNotificationHub investigationNotificationHub
    ) {
        this.investigationCaseService =
                investigationCaseService;
        this.agentFindingService = agentFindingService;
        this.investigationNotificationHub = investigationNotificationHub;
    }

    @PostMapping("/investigations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FRAUD_ANALYST')")
    public ResponseEntity<InvestigationCaseResponse> createCase(
            @Valid
            @RequestBody InvestigationCaseCreateRequest request
    ) {
        InvestigationCase investigationCase =
                investigationCaseService.createCase(
                        request.projectId(),
                        request.customerId(),
                        request.transactionId(),
                        request.caseType(),
                        request.title(),
                        request.description(),
                        request.priority(),
                        request.analystId()
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        InvestigationCaseResponse.from(
                                investigationCase
                        )
                );
    }

    @GetMapping(
            value = "/investigations/live",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter investigationLiveStream() {
        return investigationNotificationHub.subscribe();
    }

    @GetMapping("/investigations/{id}")
    public InvestigationCaseResponse getCase(
            @PathVariable UUID id
    ) {
        return InvestigationCaseResponse.from(
                investigationCaseService.getCase(id)
        );
    }

    @GetMapping("/projects/{projectId}/investigations")
    public List<InvestigationCaseResponse> getProjectCases(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String status
    ) {
        List<InvestigationCase> cases = status == null
                ? investigationCaseService
                        .getCasesByProject(projectId)
                : investigationCaseService
                        .getCasesByProjectAndStatus(
                                projectId,
                                status
                        );

        return cases.stream()
                .map(InvestigationCaseResponse::from)
                .toList();
    }

    @GetMapping("/mock/customers/{customerId}/investigations")
    public List<InvestigationCaseResponse> getCustomerCases(
            @PathVariable UUID customerId
    ) {
        return investigationCaseService
                .getCasesByCustomer(customerId)
                .stream()
                .map(InvestigationCaseResponse::from)
                .toList();
    }

    @GetMapping("/mock/transactions/{transactionId}/investigations")
    public List<InvestigationCaseResponse> getTransactionCases(
            @PathVariable UUID transactionId
    ) {
        return investigationCaseService
                .getCasesByTransaction(transactionId)
                .stream()
                .map(InvestigationCaseResponse::from)
                .toList();
    }

    @GetMapping("/investigations/{id}/findings")
    public List<AgentFindingResponse> getCaseFindings(
            @PathVariable UUID id
    ) {
        return agentFindingService.getFindingsForCase(id);
    }

    @PatchMapping("/investigations/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FRAUD_ANALYST')")
    public InvestigationCaseResponse updateStatus(
            @PathVariable UUID id,
            @Valid
            @RequestBody InvestigationStatusUpdateRequest request
    ) {
        return InvestigationCaseResponse.from(
                investigationCaseService.updateStatus(
                        id,
                        request.status()
                )
        );
    }
}
