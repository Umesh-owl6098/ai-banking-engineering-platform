package com.umeshowl.banking.investigation.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.InvestigationCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationEvidenceQueryBuilderTest {

    private InvestigationEvidenceQueryBuilder queryBuilder;

    @BeforeEach
    void setUp() {
        queryBuilder = new InvestigationEvidenceQueryBuilder(new ObjectMapper());
    }

    @Test
    void buildsFraudQueryWithPolicyAndIndicatorTerms() {
        InvestigationCase investigationCase = baseCase();
        investigationCase.setCaseType("FRAUD");
        AgentFinding finding = findingWithIndicators("""
                {
                  "recommendation":"ESCALATE",
                  "riskLevel":"HIGH",
                  "triggeredIndicators":[
                    {"type":"FLAGGED_TRANSACTION","scoreContribution":20}
                  ]
                }
                """);

        String query = queryBuilder.buildQuery("FRAUD", investigationCase, finding);

        assertTrue(query.contains("fraud detection policy"));
        assertTrue(query.contains("transaction monitoring rules"));
        assertTrue(query.contains("flagged transaction escalation"));
    }

    @Test
    void buildsKycQueryWithVerificationTerms() {
        InvestigationCase investigationCase = baseCase();
        investigationCase.setCaseType("KYC");
        AgentFinding finding = findingWithIndicators("""
                {
                  "triggeredIndicators":[
                    {"type":"PEP_CUSTOMER","scoreContribution":20}
                  ]
                }
                """);

        String query = queryBuilder.buildQuery("KYC", investigationCase, finding);

        assertTrue(query.contains("customer identification requirements"));
        assertTrue(query.contains("PEP due diligence"));
        assertTrue(query.contains("politically exposed person due diligence"));
    }

    @Test
    void buildsAmlQueryWithStructuringTerms() {
        InvestigationCase investigationCase = baseCase();
        AgentFinding finding = findingWithIndicators("""
                {
                  "triggeredIndicators":[
                    {"type":"STRUCTURING","scoreContribution":25}
                  ]
                }
                """);

        String query = queryBuilder.buildQuery("AML", investigationCase, finding);

        assertTrue(query.contains("structuring smurfing rules"));
        assertTrue(query.contains("transaction reporting thresholds"));
        assertTrue(query.contains("structuring smurfing reporting threshold"));
    }

    @Test
    void buildsComplianceQueryWithDispositionTerms() {
        InvestigationCase investigationCase = baseCase();
        AgentFinding finding = findingWithIndicators("""
                {
                  "contributingFindings":[
                    {"type":"MULTIPLE_ESCALATIONS","scoreContribution":20}
                  ]
                }
                """);

        String query = queryBuilder.buildQuery(
                "COMPLIANCE",
                investigationCase,
                finding
        );

        assertTrue(query.contains("investigation disposition policy"));
        assertTrue(query.contains("escalation criteria"));
        assertTrue(query.contains("documentation audit requirements"));
    }

    private InvestigationCase baseCase() {
        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setTitle("Suspicious transfer review");
        investigationCase.setDescription("Review flagged international transfer");
        return investigationCase;
    }

    private AgentFinding findingWithIndicators(String structuredJson) {
        AgentFinding finding = new AgentFinding();
        finding.setStructuredJson(structuredJson);
        finding.setSummary("Deterministic finding summary");
        return finding;
    }
}
