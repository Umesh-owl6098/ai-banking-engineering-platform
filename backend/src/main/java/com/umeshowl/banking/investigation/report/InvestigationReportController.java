package com.umeshowl.banking.investigation.report;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/investigations")
public class InvestigationReportController {

    private final InvestigationReportService reportService;

    public InvestigationReportController(
            InvestigationReportService reportService
    ) {
        this.reportService = reportService;
    }

    @GetMapping("/{investigationId}/report")
    public InvestigationReport getLatestReport(
            @PathVariable UUID investigationId
    ) {
        return reportService.getLatestReport(investigationId);
    }

    @PostMapping("/{investigationId}/report")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public InvestigationReport generateReport(
            @PathVariable UUID investigationId
    ) {
        return reportService.generateReport(investigationId);
    }
}
