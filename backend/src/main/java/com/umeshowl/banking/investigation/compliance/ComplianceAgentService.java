package com.umeshowl.banking.investigation.compliance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.AgentFinding;
import com.umeshowl.banking.investigation.AgentFindingRepository;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class ComplianceAgentService {
    private final InvestigationCaseService caseService; private final AgentFindingRepository findings; private final ObjectMapper mapper; private final ComplianceAgentProperties properties;
    public ComplianceAgentService(InvestigationCaseService caseService, AgentFindingRepository findings, ObjectMapper mapper, ComplianceAgentProperties properties) {this.caseService=caseService;this.findings=findings;this.mapper=mapper;this.properties=properties;}
    @Transactional(readOnly=true)
    public ComplianceAnalysisResult analyze(UUID investigationId) {
        caseService.getCase(investigationId);
        Map<String,AgentFinding> latest=new HashMap<>();
        for(String type:List.of("FRAUD","KYC","AML")) findings.findByInvestigationCase_IdAndAgentType(investigationId,type).stream().filter(f->"COMPLETE".equals(f.getStatus())).max(Comparator.comparing(AgentFinding::getCreatedAt)).ifPresent(f->latest.put(type,f));
        List<ComplianceIndicator> indicators=new ArrayList<>();
        List<AgentFinding> high=latest.values().stream().filter(this::high).toList();
        if(high.size()>=2)add(indicators,ComplianceIndicatorType.MULTIPLE_HIGH_RISK_FINDINGS,properties.getMultipleHighRiskFindingsScore(),"Multiple specialist findings are high risk",Map.of("findingCount",high.size()));
        critical(latest,"FRAUD",ComplianceIndicatorType.FRAUD_CRITICAL,properties.getFraudCriticalScore(),indicators);
        critical(latest,"AML",ComplianceIndicatorType.AML_CRITICAL,properties.getAmlCriticalScore(),indicators);
        critical(latest,"KYC",ComplianceIndicatorType.KYC_CRITICAL,properties.getKycCriticalScore(),indicators);
        if(high(latest.get("FRAUD"))&&high(latest.get("AML")))add(indicators,ComplianceIndicatorType.FRAUD_AND_AML_COMBINATION,properties.getFraudAndAmlCombinationScore(),"Fraud and AML findings jointly indicate heightened risk",Map.of());
        if(hasIndicator(latest.get("AML"),"PEP_ACTIVITY"))add(indicators,ComplianceIndicatorType.PEP_WITH_AML,properties.getPepWithAmlScore(),"AML analysis identified material PEP activity",Map.of());
        if(high.size()>=2&&high.stream().allMatch(f->f.getConfidence()!=null&&f.getConfidence().compareTo(new BigDecimal("0.800"))>=0))add(indicators,ComplianceIndicatorType.HIGH_CONFIDENCE_MATCH,properties.getHighConfidenceMatchScore(),"Multiple high-risk findings have high confidence",Map.of());
        long escalations=latest.values().stream().filter(f->"ESCALATE".equals(recommendation(f))).count();
        if(escalations>=2)add(indicators,ComplianceIndicatorType.MULTIPLE_ESCALATIONS,properties.getMultipleEscalationsScore(),"Multiple specialist analyses recommend escalation",Map.of("escalationCount",escalations));
        if(high.size()>=2&&latest.size()==3)add(indicators,ComplianceIndicatorType.CONSISTENT_HIGH_RISK_PATTERN,properties.getConsistentHighRiskPatternScore(),"All specialist analyses show a consistent high-risk pattern",Map.of());
        if(!indicators.isEmpty())add(indicators,ComplianceIndicatorType.MANUAL_REVIEW_REQUIRED,properties.getManualReviewRequiredScore(),"Consolidated analysis requires human compliance review",Map.of());
        int score=Math.clamp(indicators.stream().mapToInt(ComplianceIndicator::scoreContribution).sum(),0,100); ComplianceRiskLevel risk=level(score);
        String recommendation=recommendation(risk);
        return new ComplianceAnalysisResult(investigationId,score,risk,recommendation,indicators.isEmpty()?"No compliance escalation indicators were triggered.":indicators.size()+" consolidated compliance indicators triggered; score "+score+" ("+risk+").",indicators,OffsetDateTime.now(ZoneOffset.UTC));
    }
    private boolean high(AgentFinding f){return f!=null&&("HIGH".equals(f.getRiskLevel())||"CRITICAL".equals(f.getRiskLevel()));}
    private void critical(Map<String,AgentFinding> fs,String agent,ComplianceIndicatorType type,int score,List<ComplianceIndicator> out){if("CRITICAL".equals(fs.getOrDefault(agent,new AgentFinding()).getRiskLevel()))add(out,type,score,agent+" analysis is CRITICAL",Map.of("agentType",agent));}
    private boolean hasIndicator(AgentFinding finding,String type){try {Map<String,Object> payload=mapper.readValue(finding.getStructuredJson(),new TypeReference<>(){});return payload.toString().contains(type);}catch(Exception e){return false;}}
    private String recommendation(AgentFinding f){try {Map<String,Object> payload=mapper.readValue(f.getStructuredJson(),new TypeReference<>(){});return String.valueOf(payload.get("recommendation"));}catch(Exception e){return "";}}
    private String recommendation(ComplianceRiskLevel l){return switch(l){case LOW->"APPROVE";case MEDIUM->"REVIEW";case HIGH->"ESCALATE";case CRITICAL->"REJECT";};}
    private ComplianceRiskLevel level(int s){if(s>=80)return ComplianceRiskLevel.CRITICAL;if(s>=60)return ComplianceRiskLevel.HIGH;if(s>=30)return ComplianceRiskLevel.MEDIUM;return ComplianceRiskLevel.LOW;}
    private void add(List<ComplianceIndicator> out,ComplianceIndicatorType type,int score,String exp,Map<String,Object> evidence){out.add(new ComplianceIndicator(type,score,exp,evidence));}
}
