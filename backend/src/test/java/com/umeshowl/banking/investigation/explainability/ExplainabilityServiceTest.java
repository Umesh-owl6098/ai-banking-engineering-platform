package com.umeshowl.banking.investigation.explainability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingCitation;
import com.umeshowl.banking.investigation.AgentFindingCitationRepository;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.investigation.aml.AmlAgentProperties;
import com.umeshowl.banking.investigation.compliance.ComplianceAgentProperties;
import com.umeshowl.banking.investigation.fraud.FraudAgentProperties;
import com.umeshowl.banking.investigation.kyc.KycAgentProperties;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExplainabilityServiceTest {

    private static final UUID INVESTIGATION_ID = UUID.fromString(
            "c0000000-0000-4000-8000-000000000001"
    );
    private static final UUID FINDING_ID = UUID.fromString(
            "c0000000-0000-4000-8000-000000000002"
    );

    private InvestigationCaseService investigationCaseService;
    private AgentFindingRepository agentFindingRepository;
    private AgentFindingCitationRepository citationRepository;
    private ExplainabilityService explainabilityService;

    @BeforeEach
    void setUp() {
        investigationCaseService = mock(InvestigationCaseService.class);
        agentFindingRepository = mock(AgentFindingRepository.class);
        citationRepository = mock(AgentFindingCitationRepository.class);
        explainabilityService = new ExplainabilityService(
                investigationCaseService,
                agentFindingRepository,
                citationRepository,
                new ExplainabilityRuleMetadataResolver(
                        new FraudAgentProperties(),
                        new KycAgentProperties(),
                        new AmlAgentProperties(),
                        new ComplianceAgentProperties()
                ),
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void exposesTriggeredRulesScoresAndRecommendation() {
        AgentFinding finding = fraudFinding();
        when(agentFindingRepository.findById(FINDING_ID))
                .thenReturn(Optional.of(finding));
        when(citationRepository.findByFinding_IdOrderByCreatedAtAsc(FINDING_ID))
                .thenReturn(List.of());

        ExplainabilityResponse response =
                explainabilityService.explainFinding(FINDING_ID);

        assertEquals("FRAUD", response.agentType());
        assertEquals(60, response.totalScore());
        assertEquals("HIGH", response.riskLevel());
        assertEquals("ESCALATE", response.recommendation());
        assertEquals(3, response.triggeredRules().size());
        assertEquals(
                "Flagged Transaction",
                response.triggeredRules().getFirst().displayName()
        );
        assertEquals(
                20,
                response.triggeredRules().getFirst().scoreContribution()
        );
        assertTrue(
                response.triggeredRules().getFirst().description().toLowerCase()
                        .contains("explicitly")
        );
    }

    @Test
    void includesCitationsAndRelatedFields() {
        AgentFinding finding = fraudFinding();
        AgentFindingCitation citation = new AgentFindingCitation();
        citation.setId(UUID.randomUUID());
        citation.setFileName("fraud-policy.pdf");
        citation.setChunkIndex(2);
        citation.setSimilarity(new BigDecimal("0.91000"));
        citation.setContentPreview("Flagged transactions require escalation.");

        when(agentFindingRepository.findById(FINDING_ID))
                .thenReturn(Optional.of(finding));
        when(citationRepository.findByFinding_IdOrderByCreatedAtAsc(FINDING_ID))
                .thenReturn(List.of(citation));

        ExplainabilityResponse response =
                explainabilityService.explainFinding(FINDING_ID);

        assertEquals(1, response.supportingEvidence().size());
        assertEquals(
                "fraud-policy.pdf",
                response.supportingEvidence().getFirst().documentName()
        );
        assertEquals(
                "Flagged transactions require escalation.",
                response.supportingEvidence().getFirst().excerpt()
        );
        assertEquals("TX-001", response.relatedTransactionFields().get(
                "transactionReference"
        ));
        assertEquals("Jane Doe", response.relatedCustomerFields().get(
                "fullName"
        ));
        assertEquals(
                1,
                response.triggeredRules().getFirst().supportingEvidence().size()
        );
    }

    @Test
    void explainInvestigationReturnsLatestCompleteFindings() {
        InvestigationCase investigationCase = buildCase();
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "FRAUD"
        )).thenReturn(List.of(fraudFinding()));
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "KYC"
        )).thenReturn(List.of());
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "AML"
        )).thenReturn(List.of());
        when(agentFindingRepository.findByInvestigationCase_IdAndAgentType(
                INVESTIGATION_ID,
                "COMPLIANCE"
        )).thenReturn(List.of());
        when(citationRepository.findByFinding_IdOrderByCreatedAtAsc(FINDING_ID))
                .thenReturn(List.of());

        List<ExplainabilityResponse> responses =
                explainabilityService.explainInvestigation(INVESTIGATION_ID);

        assertEquals(1, responses.size());
        assertEquals("FRAUD", responses.getFirst().agentType());
    }

    private AgentFinding fraudFinding() {
        AgentFinding finding = new AgentFinding();
        finding.setId(FINDING_ID);
        finding.setInvestigationCase(buildCase());
        finding.setAgentType("FRAUD");
        finding.setStatus("COMPLETE");
        finding.setRiskLevel("HIGH");
        finding.setConfidence(new BigDecimal("0.800"));
        finding.setSummary("Fraud indicators triggered");
        finding.setStructuredJson("""
                {
                  "fraudScore": 60,
                  "recommendation": "ESCALATE",
                  "triggeredIndicators": [
                    {
                      "type": "FLAGGED_TRANSACTION",
                      "scoreContribution": 20,
                      "explanation": "Transaction has been explicitly flagged",
                      "evidenceValues": {"flagged": true}
                    },
                    {
                      "type": "LARGE_TRANSACTION",
                      "scoreContribution": 15,
                      "explanation": "Transaction amount meets threshold",
                      "evidenceValues": {
                        "amount": 15000,
                        "threshold": 10000
                      }
                    },
                    {
                      "type": "RAPID_MOVEMENT",
                      "scoreContribution": 25,
                      "explanation": "Multiple transactions moved funds rapidly",
                      "evidenceValues": {
                        "windowHours": 24,
                        "combinedAmount": 25000,
                        "threshold": 20000
                      }
                    }
                  ]
                }
                """);
        finding.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return finding;
    }

    private InvestigationCase buildCase() {
        MockCustomer customer = new MockCustomer();
        customer.setFullName("Jane Doe");
        customer.setAccountNumber("ACC-001");
        customer.setRiskRating("HIGH");
        customer.setKycStatus("COMPLETE");

        MockTransaction transaction = new MockTransaction();
        transaction.setTransactionReference("TX-001");
        transaction.setAmount(new BigDecimal("15000"));
        transaction.setCurrency("USD");
        transaction.setChannel("WIRE");
        transaction.setFlagged(true);
        transaction.setRiskScore(new BigDecimal("82"));
        transaction.setCustomer(customer);

        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setId(INVESTIGATION_ID);
        investigationCase.setCustomer(customer);
        investigationCase.setTransaction(transaction);
        return investigationCase;
    }
}
