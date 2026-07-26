package com.umeshowl.banking.investigation.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaInvestigationReportStore implements InvestigationReportStore {

    private final InvestigationCaseService investigationCaseService;
    private final InvestigationReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    public JpaInvestigationReportStore(
            InvestigationCaseService investigationCaseService,
            InvestigationReportRepository reportRepository,
            ObjectMapper objectMapper
    ) {
        this.investigationCaseService = investigationCaseService;
        this.reportRepository = reportRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public InvestigationReport save(
            UUID investigationId,
            InvestigationReport report,
            String rawLlmResponse
    ) {
        InvestigationCase investigationCase =
                investigationCaseService.getCase(investigationId);

        InvestigationReportEntity entity = new InvestigationReportEntity();
        entity.setInvestigationCase(investigationCase);
        entity.setPromptVersion(report.metadata().promptVersion());
        entity.setStatus("COMPLETE");
        entity.setRawLlmResponse(rawLlmResponse);
        entity.setGeneratedAt(report.metadata().generatedAt());

        try {
            entity.setStructuredJson(objectMapper.writeValueAsString(report));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to serialize investigation report",
                    exception
            );
        }

        InvestigationReportEntity saved = reportRepository.save(entity);
        return new InvestigationReport(
                saved.getId(),
                report.investigationId(),
                report.metadata(),
                report.executiveSummary(),
                report.investigationOverview(),
                report.customerRiskProfile(),
                report.fraudAnalysis(),
                report.kycAnalysis(),
                report.amlAnalysis(),
                report.complianceAssessment(),
                report.supportingEvidence(),
                report.analystRecommendation(),
                report.confidenceExplanation(),
                report.limitations()
        );
    }

    @Override
    public Optional<InvestigationReport> findLatest(UUID investigationId) {
        return reportRepository
                .findFirstByInvestigationCase_IdOrderByGeneratedAtDesc(
                        investigationId
                )
                .map(this::toReport);
    }

    private InvestigationReport toReport(InvestigationReportEntity entity) {
        try {
            InvestigationReport report = objectMapper.readValue(
                    entity.getStructuredJson(),
                    InvestigationReport.class
            );
            if (report.id() == null) {
                return new InvestigationReport(
                        entity.getId(),
                        report.investigationId(),
                        report.metadata(),
                        report.executiveSummary(),
                        report.investigationOverview(),
                        report.customerRiskProfile(),
                        report.fraudAnalysis(),
                        report.kycAnalysis(),
                        report.amlAnalysis(),
                        report.complianceAssessment(),
                        report.supportingEvidence(),
                        report.analystRecommendation(),
                        report.confidenceExplanation(),
                        report.limitations()
                );
            }
            return report;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to deserialize stored investigation report",
                    exception
            );
        }
    }
}
