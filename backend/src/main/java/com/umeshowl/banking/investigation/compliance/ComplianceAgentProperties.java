package com.umeshowl.banking.investigation.compliance;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Component @ConfigurationProperties(prefix="compliance")
public class ComplianceAgentProperties {
    private int multipleHighRiskFindingsScore=25, fraudCriticalScore=35, amlCriticalScore=35, kycCriticalScore=25, fraudAndAmlCombinationScore=25, pepWithAmlScore=20, highConfidenceMatchScore=15, multipleEscalationsScore=20, consistentHighRiskPatternScore=20, manualReviewRequiredScore=15;
    public int getMultipleHighRiskFindingsScore(){return multipleHighRiskFindingsScore;} public void setMultipleHighRiskFindingsScore(int v){multipleHighRiskFindingsScore=v;}
    public int getFraudCriticalScore(){return fraudCriticalScore;} public void setFraudCriticalScore(int v){fraudCriticalScore=v;}
    public int getAmlCriticalScore(){return amlCriticalScore;} public void setAmlCriticalScore(int v){amlCriticalScore=v;}
    public int getKycCriticalScore(){return kycCriticalScore;} public void setKycCriticalScore(int v){kycCriticalScore=v;}
    public int getFraudAndAmlCombinationScore(){return fraudAndAmlCombinationScore;} public void setFraudAndAmlCombinationScore(int v){fraudAndAmlCombinationScore=v;}
    public int getPepWithAmlScore(){return pepWithAmlScore;} public void setPepWithAmlScore(int v){pepWithAmlScore=v;}
    public int getHighConfidenceMatchScore(){return highConfidenceMatchScore;} public void setHighConfidenceMatchScore(int v){highConfidenceMatchScore=v;}
    public int getMultipleEscalationsScore(){return multipleEscalationsScore;} public void setMultipleEscalationsScore(int v){multipleEscalationsScore=v;}
    public int getConsistentHighRiskPatternScore(){return consistentHighRiskPatternScore;} public void setConsistentHighRiskPatternScore(int v){consistentHighRiskPatternScore=v;}
    public int getManualReviewRequiredScore(){return manualReviewRequiredScore;} public void setManualReviewRequiredScore(int v){manualReviewRequiredScore=v;}
}
