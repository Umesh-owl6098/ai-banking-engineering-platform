package com.umeshowl.banking.investigation.aml;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "aml")
public class AmlAgentProperties {
    private BigDecimal reportingThreshold = new BigDecimal("10000.00");
    private BigDecimal largeTransactionThreshold = new BigDecimal("10000.00");
    private BigDecimal highRiskScoreThreshold = new BigDecimal("75.00");
    private int rapidWindowHours = 24;
    private BigDecimal rapidCombinedThreshold = new BigDecimal("20000.00");
    private int newAccountDays = 90;
    private List<String> highRiskCountries = List.of("Iran", "North Korea", "Syria", "Afghanistan");
    private int structuringScore = 25, rapidMovementScore = 20, largeTransactionScore = 15,
            highRiskCountryScore = 15, highRiskCustomerScore = 15, pepActivityScore = 20,
            flaggedTransactionScore = 20, highTransactionRiskScore = 15,
            newAccountActivityScore = 15, multipleHighRiskIndicatorsScore = 20;
    public BigDecimal getReportingThreshold() { return reportingThreshold; } public void setReportingThreshold(BigDecimal v) { reportingThreshold=v; }
    public BigDecimal getLargeTransactionThreshold() { return largeTransactionThreshold; } public void setLargeTransactionThreshold(BigDecimal v) { largeTransactionThreshold=v; }
    public BigDecimal getHighRiskScoreThreshold() { return highRiskScoreThreshold; } public void setHighRiskScoreThreshold(BigDecimal v) { highRiskScoreThreshold=v; }
    public int getRapidWindowHours() { return rapidWindowHours; } public void setRapidWindowHours(int v) { rapidWindowHours=v; }
    public BigDecimal getRapidCombinedThreshold() { return rapidCombinedThreshold; } public void setRapidCombinedThreshold(BigDecimal v) { rapidCombinedThreshold=v; }
    public int getNewAccountDays() { return newAccountDays; } public void setNewAccountDays(int v) { newAccountDays=v; }
    public List<String> getHighRiskCountries() { return highRiskCountries; } public void setHighRiskCountries(List<String> v) { highRiskCountries=List.copyOf(v); }
    public int getStructuringScore() { return structuringScore; } public void setStructuringScore(int v) { structuringScore=v; }
    public int getRapidMovementScore() { return rapidMovementScore; } public void setRapidMovementScore(int v) { rapidMovementScore=v; }
    public int getLargeTransactionScore() { return largeTransactionScore; } public void setLargeTransactionScore(int v) { largeTransactionScore=v; }
    public int getHighRiskCountryScore() { return highRiskCountryScore; } public void setHighRiskCountryScore(int v) { highRiskCountryScore=v; }
    public int getHighRiskCustomerScore() { return highRiskCustomerScore; } public void setHighRiskCustomerScore(int v) { highRiskCustomerScore=v; }
    public int getPepActivityScore() { return pepActivityScore; } public void setPepActivityScore(int v) { pepActivityScore=v; }
    public int getFlaggedTransactionScore() { return flaggedTransactionScore; } public void setFlaggedTransactionScore(int v) { flaggedTransactionScore=v; }
    public int getHighTransactionRiskScore() { return highTransactionRiskScore; } public void setHighTransactionRiskScore(int v) { highTransactionRiskScore=v; }
    public int getNewAccountActivityScore() { return newAccountActivityScore; } public void setNewAccountActivityScore(int v) { newAccountActivityScore=v; }
    public int getMultipleHighRiskIndicatorsScore() { return multipleHighRiskIndicatorsScore; } public void setMultipleHighRiskIndicatorsScore(int v) { multipleHighRiskIndicatorsScore=v; }
}
