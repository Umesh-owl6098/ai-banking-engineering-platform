package com.umeshowl.banking.dashboard;

import com.umeshowl.banking.dashboard.dto.OperationsDashboardResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class OperationsDashboardController {

    private final OperationsDashboardService operationsDashboardService;

    public OperationsDashboardController(
            OperationsDashboardService operationsDashboardService
    ) {
        this.operationsDashboardService = operationsDashboardService;
    }

    @GetMapping("/operations")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'SUPERVISOR',
                'FRAUD_ANALYST',
                'COMPLIANCE_ANALYST',
                'READ_ONLY'
            )
            """)
    public OperationsDashboardResponse getOperationsDashboard(
            @RequestParam(required = false) UUID projectId
    ) {
        return operationsDashboardService.loadOperationsDashboard(projectId);
    }
}
