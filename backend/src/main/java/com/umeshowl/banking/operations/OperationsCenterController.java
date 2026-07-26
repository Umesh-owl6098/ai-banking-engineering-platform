package com.umeshowl.banking.operations;

import com.umeshowl.banking.operations.dto.OperationsCenterResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/operations")
public class OperationsCenterController {

    private final OperationsCenterService operationsCenterService;

    public OperationsCenterController(
            OperationsCenterService operationsCenterService
    ) {
        this.operationsCenterService = operationsCenterService;
    }

    @GetMapping("/center")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'SUPERVISOR',
                'FRAUD_ANALYST',
                'COMPLIANCE_ANALYST',
                'READ_ONLY'
            )
            """)
    public OperationsCenterResponse getOperationsCenter(
            @RequestParam(required = false) UUID projectId
    ) {
        return operationsCenterService.loadOperationsCenter(projectId);
    }
}
