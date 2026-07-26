package com.umeshowl.banking.investigation;

import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockCustomerRepository;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.mockdata.MockTransactionRepository;
import com.umeshowl.banking.observability.BankingMetrics;
import com.umeshowl.banking.project.Project;
import com.umeshowl.banking.project.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class InvestigationCaseService {

    private static final Set<String> CASE_TYPES =
            Set.of(
                    "FRAUD",
                    "KYC",
                    "AML",
                    "COMPLIANCE",
                    "MULTI"
            );

    private static final Set<String> PRIORITIES =
            Set.of(
                    "LOW",
                    "MEDIUM",
                    "HIGH",
                    "CRITICAL"
            );

    private static final Set<String> CASE_STATUSES =
            Set.of(
                    "NEW",
                    "RUNNING",
                    "REPORT_GENERATED",
                    "EXECUTION_FAILED",
                    "OPEN",
                    "INVESTIGATING",
                    "AWAITING_REVIEW",
                    "ASSIGNED",
                    "IN_REVIEW",
                    "APPROVED",
                    "REJECTED",
                    "ESCALATED",
                    "CLOSED"
            );

    private static final Map<String, Set<String>>
            ALLOWED_STATUS_TRANSITIONS =
            Map.ofEntries(
                    Map.entry(
                            "NEW",
                            Set.of(
                                    "RUNNING",
                                    "INVESTIGATING",
                                    "EXECUTION_FAILED"
                            )
                    ),
                    Map.entry(
                            "RUNNING",
                            Set.of(
                                    "REPORT_GENERATED",
                                    "EXECUTION_FAILED"
                            )
                    ),
                    Map.entry(
                            "REPORT_GENERATED",
                            Set.of("AWAITING_REVIEW")
                    ),
                    Map.entry(
                            "EXECUTION_FAILED",
                            Set.of("RUNNING")
                    ),
                    Map.entry("OPEN", Set.of("INVESTIGATING")),
                    Map.entry(
                            "INVESTIGATING",
                            Set.of("AWAITING_REVIEW")
                    ),
                    Map.entry(
                            "AWAITING_REVIEW",
                            Set.of(
                                    "ASSIGNED",
                                    "APPROVED",
                                    "REJECTED",
                                    "ESCALATED",
                                    "INVESTIGATING"
                            )
                    ),
                    Map.entry(
                            "ASSIGNED",
                            Set.of(
                                    "IN_REVIEW",
                                    "AWAITING_REVIEW"
                            )
                    ),
                    Map.entry(
                            "IN_REVIEW",
                            Set.of(
                                    "APPROVED",
                                    "REJECTED",
                                    "ESCALATED",
                                    "INVESTIGATING"
                            )
                    ),
                    Map.entry("ESCALATED", Set.of("INVESTIGATING")),
                    Map.entry("APPROVED", Set.of("CLOSED")),
                    Map.entry("REJECTED", Set.of("CLOSED")),
                    Map.entry("CLOSED", Set.of())
            );

    private final InvestigationCaseRepository
            investigationCaseRepository;

    private final ProjectRepository projectRepository;

    private final MockCustomerRepository
            mockCustomerRepository;

    private final MockTransactionRepository
            mockTransactionRepository;

    private final BankingMetrics bankingMetrics;

    public InvestigationCaseService(
            InvestigationCaseRepository investigationCaseRepository,
            ProjectRepository projectRepository,
            MockCustomerRepository mockCustomerRepository,
            MockTransactionRepository mockTransactionRepository,
            BankingMetrics bankingMetrics
    ) {
        this.investigationCaseRepository =
                investigationCaseRepository;
        this.projectRepository = projectRepository;
        this.mockCustomerRepository = mockCustomerRepository;
        this.mockTransactionRepository =
                mockTransactionRepository;
        this.bankingMetrics = bankingMetrics;
    }

    @Transactional
    public InvestigationCase createCase(
            UUID projectId,
            UUID customerId,
            UUID transactionId,
            String caseType,
            String title,
            String description,
            String priority,
            String analystId
    ) {
        Project project = getProject(projectId);
        MockCustomer customer = getCustomerOrNull(customerId);
        MockTransaction transaction =
                getTransactionOrNull(transactionId);

        validateCaseSubject(customer, transaction);
        validateTransactionCustomerMatch(
                customer,
                transaction
        );

        InvestigationCase investigationCase =
                new InvestigationCase();

        investigationCase.setProject(project);
        investigationCase.setCustomer(customer);
        investigationCase.setTransaction(transaction);
        investigationCase.setCaseType(
                validateCaseType(caseType)
        );
        investigationCase.setTitle(
                requireNonBlank(title, "Case title is required")
        );
        investigationCase.setDescription(
                requireNonBlank(
                        description,
                        "Case description is required"
                )
        );
        investigationCase.setPriority(
                validatePriority(priority)
        );
        investigationCase.setAnalystId(
                normalizeOptional(analystId)
        );

        InvestigationCase savedCase = investigationCaseRepository.save(
                investigationCase
        );
        bankingMetrics.recordInvestigationCreated();
        return savedCase;
    }

    @Transactional(readOnly = true)
    public InvestigationCase getCase(UUID caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException(
                    "Investigation case ID is required"
            );
        }

        return investigationCaseRepository.findById(caseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Investigation case not found: "
                                        + caseId
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<InvestigationCase> getCasesByProject(
            UUID projectId
    ) {
        getProject(projectId);

        return investigationCaseRepository
                .findByProject_IdOrderByCreatedAtDesc(projectId);
    }

    @Transactional(readOnly = true)
    public List<InvestigationCase> getCasesByProjectAndStatus(
            UUID projectId,
            String status
    ) {
        getProject(projectId);

        return investigationCaseRepository
                .findByProject_IdAndStatusOrderByCreatedAtDesc(
                        projectId,
                        validateCaseStatus(status)
                );
    }

    @Transactional(readOnly = true)
    public List<InvestigationCase> getCasesByCustomer(
            UUID customerId
    ) {
        getCustomerOrNullRequired(customerId);

        return investigationCaseRepository
                .findByCustomer_IdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<InvestigationCase> getCasesByTransaction(
            UUID transactionId
    ) {
        getTransactionOrNullRequired(transactionId);

        return investigationCaseRepository
                .findByTransaction_IdOrderByCreatedAtDesc(
                        transactionId
                );
    }

    @Transactional
    public InvestigationCase updateStatus(
            UUID caseId,
            String requestedStatus
    ) {
        InvestigationCase investigationCase =
                getCase(caseId);

        String targetStatus =
                validateCaseStatus(requestedStatus);

        String currentStatus =
                investigationCase.getStatus();

        if (currentStatus.equals(targetStatus)) {
            return investigationCase;
        }

        Set<String> allowedTargets =
                ALLOWED_STATUS_TRANSITIONS.get(currentStatus);

        if (allowedTargets == null
                || !allowedTargets.contains(targetStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid investigation case status transition: "
                            + currentStatus
                            + " -> "
                            + targetStatus
            );
        }

        investigationCase.setStatus(targetStatus);
        bankingMetrics.recordStatusTransition(currentStatus, targetStatus);

        /*
         * InvestigationCase.beforeUpdate() updates updatedAt when
         * this managed entity is flushed.
         */
        return investigationCaseRepository.save(
                investigationCase
        );
    }

    @Transactional
    public boolean beginAutoExecution(UUID caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException(
                    "Investigation case ID is required"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int updated = investigationCaseRepository.beginAutoExecution(
                caseId,
                now
        );

        if (updated > 0) {
            bankingMetrics.recordStatusTransition("NEW", "RUNNING");
        }

        return updated > 0;
    }

    @Transactional
    public boolean markExecutionFailed(
            UUID caseId,
            String stage,
            String message
    ) {
        if (caseId == null) {
            throw new IllegalArgumentException(
                    "Investigation case ID is required"
            );
        }

        String safeStage = requireNonBlank(
                stage,
                "Execution failure stage is required"
        );
        String safeMessage = sanitizeFailureMessage(message);
        OffsetDateTime failedAt = OffsetDateTime.now(ZoneOffset.UTC);

        int updated = investigationCaseRepository.markExecutionFailed(
                caseId,
                safeStage,
                safeMessage,
                failedAt
        );

        if (updated > 0) {
            bankingMetrics.recordStatusTransition("RUNNING", "EXECUTION_FAILED");
            return true;
        }

        InvestigationCase investigationCase = getCase(caseId);
        if ("EXECUTION_FAILED".equals(investigationCase.getStatus())) {
            return true;
        }

        if ("NEW".equals(investigationCase.getStatus())) {
            investigationCase.setStatus("EXECUTION_FAILED");
            investigationCase.setExecutionFailureStage(safeStage);
            investigationCase.setExecutionFailureMessage(safeMessage);
            investigationCase.setExecutionFailureAt(failedAt);
            investigationCaseRepository.save(investigationCase);
            bankingMetrics.recordStatusTransition("NEW", "EXECUTION_FAILED");
            return true;
        }

        return false;
    }

    @Transactional(readOnly = true)
    public InvestigationCase getCaseAfterExecutionAttempt(UUID caseId) {
        return getCase(caseId);
    }

    private String sanitizeFailureMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Investigation execution failed";
        }

        String sanitized = message
                .replaceAll("(?i)sk-[a-zA-Z0-9_-]+", "[redacted]")
                .replaceAll("(?i)api[_-]?key[^\\s]*", "[redacted]")
                .trim();

        if (sanitized.length() > 500) {
            return sanitized.substring(0, 497) + "...";
        }

        return sanitized;
    }

    private Project getProject(UUID projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException(
                    "Project ID is required"
            );
        }

        return projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Project not found: " + projectId
                        )
                );
    }

    private MockCustomer getCustomerOrNull(
            UUID customerId
    ) {
        if (customerId == null) {
            return null;
        }

        return getCustomerOrNullRequired(customerId);
    }

    private MockCustomer getCustomerOrNullRequired(
            UUID customerId
    ) {
        if (customerId == null) {
            throw new IllegalArgumentException(
                    "Customer ID is required"
            );
        }

        return mockCustomerRepository.findById(customerId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Mock customer not found: "
                                        + customerId
                        )
                );
    }

    private MockTransaction getTransactionOrNull(
            UUID transactionId
    ) {
        if (transactionId == null) {
            return null;
        }

        return getTransactionOrNullRequired(transactionId);
    }

    private MockTransaction getTransactionOrNullRequired(
            UUID transactionId
    ) {
        if (transactionId == null) {
            throw new IllegalArgumentException(
                    "Transaction ID is required"
            );
        }

        return mockTransactionRepository.findById(transactionId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Mock transaction not found: "
                                        + transactionId
                        )
                );
    }

    private void validateCaseSubject(
            MockCustomer customer,
            MockTransaction transaction
    ) {
        if (customer == null && transaction == null) {
            throw new IllegalArgumentException(
                    "At least one customer or transaction is required"
            );
        }
    }

    private void validateTransactionCustomerMatch(
            MockCustomer customer,
            MockTransaction transaction
    ) {
        if (customer == null || transaction == null) {
            return;
        }

        if (!transaction.getCustomer().getId()
                .equals(customer.getId())) {

            throw new IllegalArgumentException(
                    "The transaction does not belong to the customer"
            );
        }
    }

    private String validateCaseType(String caseType) {
        String normalizedCaseType = normalizeRequired(
                caseType,
                "Case type is required"
        );

        if (!CASE_TYPES.contains(normalizedCaseType)) {
            throw new IllegalArgumentException(
                    "Unsupported case type: " + normalizedCaseType
                            + ". Allowed values: " + CASE_TYPES
            );
        }

        return normalizedCaseType;
    }

    private String validatePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "MEDIUM";
        }

        String normalizedPriority =
                priority.trim().toUpperCase(Locale.ROOT);

        if (!PRIORITIES.contains(normalizedPriority)) {
            throw new IllegalArgumentException(
                    "Unsupported case priority: "
                            + normalizedPriority
                            + ". Allowed values: " + PRIORITIES
            );
        }

        return normalizedPriority;
    }

    private String validateCaseStatus(String status) {
        String normalizedStatus = normalizeRequired(
                status,
                "Case status is required"
        );

        if (!CASE_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException(
                    "Unsupported case status: "
                            + normalizedStatus
            );
        }

        return normalizedStatus;
    }

    private String normalizeRequired(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireNonBlank(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
