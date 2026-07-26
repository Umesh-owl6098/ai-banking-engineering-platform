package com.umeshowl.banking.investigation.review;

import com.umeshowl.banking.investigation.review.dto.HumanReviewDecisionRequest;
import com.umeshowl.banking.investigation.review.dto.InvestigationReviewContextResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/investigations/{investigationId}")
public class HumanReviewController {

    private final HumanReviewService humanReviewService;
    private final InvestigationDecisionService investigationDecisionService;

    public HumanReviewController(
            HumanReviewService humanReviewService,
            InvestigationDecisionService investigationDecisionService
    ) {
        this.humanReviewService = humanReviewService;
        this.investigationDecisionService = investigationDecisionService;
    }

    @GetMapping("/review")
    public InvestigationReviewContextResponse getReviewContext(
            @PathVariable UUID investigationId
    ) {
        return humanReviewService.getReviewContext(investigationId);
    }

    @PostMapping("/review/start")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'SUPERVISOR',
                'FRAUD_ANALYST',
                'COMPLIANCE_ANALYST'
            )
            """)
    public InvestigationReviewContextResponse startReview(
            @PathVariable UUID investigationId
    ) {
        return humanReviewService.startReview(investigationId);
    }

    @PostMapping("/decisions/approve")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','COMPLIANCE_ANALYST')")
    public InvestigationReviewContextResponse approve(
            @PathVariable UUID investigationId,
            @Valid @RequestBody HumanReviewDecisionRequest request
    ) {
        return investigationDecisionService.approve(
                investigationId,
                request
        );
    }

    @PostMapping("/decisions/reject")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','COMPLIANCE_ANALYST')")
    public InvestigationReviewContextResponse reject(
            @PathVariable UUID investigationId,
            @Valid @RequestBody HumanReviewDecisionRequest request
    ) {
        return investigationDecisionService.reject(
                investigationId,
                request
        );
    }

    @PostMapping("/decisions/escalate")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','COMPLIANCE_ANALYST')")
    public InvestigationReviewContextResponse escalate(
            @PathVariable UUID investigationId,
            @Valid @RequestBody HumanReviewDecisionRequest request
    ) {
        return investigationDecisionService.escalate(
                investigationId,
                request
        );
    }

    @PostMapping("/decisions/request-more-investigation")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FRAUD_ANALYST','COMPLIANCE_ANALYST')")
    public InvestigationReviewContextResponse requestMoreInvestigation(
            @PathVariable UUID investigationId,
            @Valid @RequestBody HumanReviewDecisionRequest request
    ) {
        return investigationDecisionService.requestMoreInvestigation(
                investigationId,
                request
        );
    }
}
