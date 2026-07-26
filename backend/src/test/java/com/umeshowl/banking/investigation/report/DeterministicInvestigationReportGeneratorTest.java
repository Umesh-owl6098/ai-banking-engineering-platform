package com.umeshowl.banking.investigation.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicInvestigationReportGeneratorTest {

    private static final UUID INVESTIGATION_ID = UUID.fromString(
            "a0000000-0000-4000-8000-000000000003"
    );

    private DeterministicInvestigationReportGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DeterministicInvestigationReportGenerator();
    }

    @Test
    void generatesAllRequiredSections() {
        ReportSourceData source = sampleSource();

        InvestigationReport report = generator.generate(
                INVESTIGATION_ID,
                source,
                metadata()
        );

        assertEquals(INVESTIGATION_ID, report.investigationId());
        assertTrue(report.executiveSummary().contains("Test case"));
        assertEquals("Investigation Overview", report.investigationOverview().title());
        assertEquals("Customer Risk Profile", report.customerRiskProfile().title());
        assertEquals("Fraud Analysis", report.fraudAnalysis().title());
        assertEquals("KYC Analysis", report.kycAnalysis().title());
        assertEquals("AML Analysis", report.amlAnalysis().title());
        assertEquals("Compliance Assessment", report.complianceAssessment().title());
        assertEquals("ESCALATE", report.analystRecommendation());
        assertTrue(report.limitations().contains("Deterministic agent rules"));
        assertTrue(report.limitations().contains("Human review is required"));
    }

    @Test
    void executiveSummaryIsLimitedToTwoHundredWords() {
        String longText = "word ".repeat(250);
        String truncated = generator.truncateWords(longText, 200);

        assertEquals(200, truncated.split("\\s+").length);
    }

    @Test
    void supportingEvidenceShowsFallbackWhenEmpty() {
        ReportSourceData source = new ReportSourceData(
                Map.of("title", "Case"),
                Map.of("available", false),
                Map.of("available", false),
                Map.of("available", false),
                Map.of("available", false),
                Map.of("available", false),
                Map.of("available", false),
                List.of()
        );

        List<InvestigationReportSection> evidence =
                generator.buildSupportingEvidence(source);

        assertEquals(1, evidence.size());
        assertEquals(
                "No supporting documentation was retrieved.",
                evidence.getFirst().narrative()
        );
    }

    private ReportSourceData sampleSource() {
        return new ReportSourceData(
                Map.of(
                        "title", "Test case",
                        "caseType", "FRAUD",
                        "status", "OPEN",
                        "priority", "HIGH",
                        "description", "Suspicious transfer"
                ),
                Map.of(
                        "available", true,
                        "name", "Jane Doe",
                        "accountNumber", "ACC-001",
                        "riskRating", "HIGH",
                        "kycStatus", "COMPLETE",
                        "pepStatus", "NONE",
                        "occupation", "Engineer",
                        "sourceOfFunds", "Salary",
                        "countryOfResidence", "US"
                ),
                Map.of(
                        "available", true,
                        "reference", "TX-001",
                        "amount", "15000",
                        "currency", "USD",
                        "channel", "WIRE",
                        "flagged", true,
                        "riskScore", 82
                ),
                Map.of(
                        "available", true,
                        "score", 80,
                        "riskLevel", "HIGH",
                        "recommendation", "ESCALATE",
                        "summary", "Fraud summary",
                        "indicators", List.of(
                                Map.of(
                                        "type", "FLAGGED_TRANSACTION",
                                        "explanation", "Transaction flagged"
                                )
                        )
                ),
                Map.of("available", false),
                Map.of("available", false),
                Map.of(
                        "available", true,
                        "score", 75,
                        "riskLevel", "HIGH",
                        "recommendation", "ESCALATE",
                        "confidence", "0.800",
                        "summary", "Compliance summary"
                ),
                List.of()
        );
    }

    private InvestigationReportMetadata metadata() {
        return new InvestigationReportMetadata(
                "1.0.0",
                OffsetDateTime.now(ZoneOffset.UTC),
                "deterministic",
                5L,
                "DETERMINISTIC"
        );
    }
}
