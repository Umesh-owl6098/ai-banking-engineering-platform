package com.umeshowl.banking.investigation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.fraud.FraudAnalysisResult;
import com.umeshowl.banking.investigation.fraud.FraudRiskLevel;
import com.umeshowl.banking.investigation.kyc.KycAnalysisResult;
import com.umeshowl.banking.investigation.kyc.KycRiskLevel;
import com.umeshowl.banking.investigation.aml.AmlAnalysisResult;
import com.umeshowl.banking.investigation.aml.AmlRiskLevel;
import com.umeshowl.banking.investigation.compliance.ComplianceAnalysisResult;
import com.umeshowl.banking.investigation.compliance.ComplianceRiskLevel;
import com.umeshowl.banking.investigation.dto.AgentFindingCitationResponse;
import com.umeshowl.banking.investigation.dto.AgentFindingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentFindingService {

    private final InvestigationCaseService investigationCaseService;
    private final AgentFindingRepository agentFindingRepository;
    private final AgentFindingCitationRepository agentFindingCitationRepository;
    private final ObjectMapper objectMapper;

    public AgentFindingService(
            InvestigationCaseService investigationCaseService,
            AgentFindingRepository agentFindingRepository,
            AgentFindingCitationRepository agentFindingCitationRepository,
            ObjectMapper objectMapper
    ) {
        this.investigationCaseService = investigationCaseService;
        this.agentFindingRepository = agentFindingRepository;
        this.agentFindingCitationRepository = agentFindingCitationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AgentFindingResponse> getFindingsForCase(UUID caseId) {
        investigationCaseService.getCase(caseId);

        return agentFindingRepository
                .findByInvestigationCase_IdOrderByCreatedAtAsc(caseId)
                .stream()
                .map(finding -> AgentFindingResponse.from(
                        finding,
                        agentFindingCitationRepository
                                .findByFinding_IdOrderByCreatedAtAsc(
                                        finding.getId()
                                )
                ))
                .toList();
    }

    @Transactional
    public AgentFinding persistFraudAnalysis(
            FraudAnalysisResult analysis
    ) {
        InvestigationCase investigationCase =
                investigationCaseService.getCase(
                        analysis.investigationId()
                );
        AgentFinding finding = new AgentFinding();
        String recommendation = recommendationFor(
                analysis.riskLevel()
        );

        finding.setInvestigationCase(investigationCase);
        finding.setAgentType("FRAUD");
        finding.setStatus("COMPLETE");
        finding.setRiskLevel(analysis.riskLevel().name());
        finding.setConfidence(confidenceFor(analysis.riskLevel()));
        finding.setSummary(analysis.summary());
        finding.setStructuredJson(toStructuredJson(
                analysis,
                recommendation
        ));
        finding.setStartedAt(analysis.analyzedAt());
        finding.setCompletedAt(analysis.analyzedAt());

        /*
         * TODO: Persist deterministic indicators as RAG citations only
         * after a retrieval stage supplies real knowledge_documents and
         * document_chunks. agent_finding_citations must not contain
         * fabricated evidence records.
         */
        return agentFindingRepository.save(finding);
    }

    @Transactional
    public AgentFinding persistKycAnalysis(KycAnalysisResult analysis) {
        InvestigationCase investigationCase =
                investigationCaseService.getCase(
                        analysis.investigationId()
                );
        AgentFinding finding = new AgentFinding();
        String recommendation = recommendationFor(
                analysis.riskLevel()
        );

        finding.setInvestigationCase(investigationCase);
        finding.setAgentType("KYC");
        finding.setStatus("COMPLETE");
        finding.setRiskLevel(analysis.riskLevel().name());
        finding.setConfidence(confidenceFor(analysis.riskLevel()));
        finding.setSummary(analysis.summary());
        finding.setStructuredJson(toStructuredJson(
                analysis,
                recommendation
        ));
        finding.setStartedAt(analysis.analyzedAt());
        finding.setCompletedAt(analysis.analyzedAt());

        /*
         * TODO: Link KYC evidence to real RAG document citations only after
         * the retrieval stage supplies knowledge_documents and document_chunks.
         */
        return agentFindingRepository.save(finding);
    }

    @Transactional
    public AgentFinding persistAmlAnalysis(AmlAnalysisResult analysis) {
        InvestigationCase investigationCase = investigationCaseService
                .getCase(analysis.investigationId());
        AgentFinding finding = new AgentFinding();
        finding.setInvestigationCase(investigationCase);
        finding.setAgentType("AML");
        finding.setStatus("COMPLETE");
        finding.setRiskLevel(analysis.riskLevel().name());
        finding.setConfidence(confidenceFor(analysis.riskLevel()));
        finding.setSummary(analysis.summary());
        finding.setStructuredJson(toStructuredJson(
                analysis, recommendationFor(analysis.riskLevel())
        ));
        finding.setStartedAt(analysis.analyzedAt());
        finding.setCompletedAt(analysis.analyzedAt());
        return agentFindingRepository.save(finding);
    }

    @Transactional
    public AgentFinding persistComplianceAnalysis(ComplianceAnalysisResult analysis) {
        InvestigationCase investigationCase = investigationCaseService.getCase(analysis.investigationId());
        AgentFinding finding = new AgentFinding();
        finding.setInvestigationCase(investigationCase);
        finding.setAgentType("COMPLIANCE");
        finding.setStatus("COMPLETE");
        finding.setRiskLevel(analysis.riskLevel().name());
        finding.setConfidence(confidenceFor(analysis.riskLevel()));
        finding.setSummary(analysis.summary());
        finding.setStructuredJson(toStructuredJson(analysis));
        finding.setStartedAt(analysis.analyzedAt());
        finding.setCompletedAt(analysis.analyzedAt());
        return agentFindingRepository.save(finding);
    }

    private String toStructuredJson(
            FraudAnalysisResult analysis,
            String recommendation
    ) {
        Map<String, Object> structuredData = new LinkedHashMap<>();
        structuredData.put("recommendation", recommendation);
        structuredData.put("fraudScore", analysis.totalScore());
        structuredData.put("riskLevel", analysis.riskLevel());
        structuredData.put(
                "triggeredIndicators",
                analysis.triggeredIndicators()
        );
        structuredData.put("analyzedAt", analysis.analyzedAt());

        try {
            return objectMapper.writeValueAsString(structuredData);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize fraud analysis evidence",
                    exception
            );
        }
    }

    private String toStructuredJson(
            KycAnalysisResult analysis,
            String recommendation
    ) {
        Map<String, Object> structuredData = new LinkedHashMap<>();
        structuredData.put("recommendation", recommendation);
        structuredData.put("kycScore", analysis.totalScore());
        structuredData.put("riskLevel", analysis.riskLevel());
        structuredData.put(
                "triggeredIndicators",
                analysis.triggeredIndicators()
        );
        structuredData.put("analyzedAt", analysis.analyzedAt());

        try {
            return objectMapper.writeValueAsString(structuredData);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize KYC analysis evidence",
                    exception
            );
        }
    }

    private String toStructuredJson(
            AmlAnalysisResult analysis, String recommendation
    ) {
        Map<String, Object> structuredData = new LinkedHashMap<>();
        structuredData.put("recommendation", recommendation);
        structuredData.put("amlScore", analysis.totalScore());
        structuredData.put("riskLevel", analysis.riskLevel());
        structuredData.put("triggeredIndicators", analysis.triggeredIndicators());
        structuredData.put("analyzedAt", analysis.analyzedAt());
        try {
            return objectMapper.writeValueAsString(structuredData);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize AML analysis evidence", exception
            );
        }
    }

    private String toStructuredJson(ComplianceAnalysisResult analysis) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recommendation", analysis.recommendation());
        data.put("overallScore", analysis.overallScore());
        data.put("riskLevel", analysis.riskLevel());
        data.put("contributingFindings", analysis.contributingFindings());
        data.put("analyzedAt", analysis.analyzedAt());
        try { return objectMapper.writeValueAsString(data); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize compliance analysis evidence", exception); }
    }

    String recommendationFor(FraudRiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> "APPROVE";
            case MEDIUM -> "REVIEW";
            case HIGH, CRITICAL -> "ESCALATE";
        };
    }

    BigDecimal confidenceFor(FraudRiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> new BigDecimal("0.350");
            case MEDIUM -> new BigDecimal("0.600");
            case HIGH -> new BigDecimal("0.800");
            case CRITICAL -> new BigDecimal("0.950");
        };
    }

    String recommendationFor(KycRiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> "APPROVE";
            case MEDIUM -> "REVIEW";
            case HIGH, CRITICAL -> "ESCALATE";
        };
    }

    BigDecimal confidenceFor(KycRiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> new BigDecimal("0.350");
            case MEDIUM -> new BigDecimal("0.600");
            case HIGH -> new BigDecimal("0.800");
            case CRITICAL -> new BigDecimal("0.950");
        };
    }

    String recommendationFor(AmlRiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> "APPROVE";
            case MEDIUM -> "REVIEW";
            case HIGH, CRITICAL -> "ESCALATE";
        };
    }

    BigDecimal confidenceFor(AmlRiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> new BigDecimal("0.350");
            case MEDIUM -> new BigDecimal("0.600");
            case HIGH -> new BigDecimal("0.800");
            case CRITICAL -> new BigDecimal("0.950");
        };
    }

    BigDecimal confidenceFor(ComplianceRiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> new BigDecimal("0.400");
            case MEDIUM -> new BigDecimal("0.650");
            case HIGH -> new BigDecimal("0.850");
            case CRITICAL -> new BigDecimal("0.980");
        };
    }
}
