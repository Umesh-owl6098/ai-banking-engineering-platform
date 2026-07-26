package com.umeshowl.banking.investigation.fraud;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "fraud")
public class FraudAgentProperties {

    private BigDecimal highRiskScoreThreshold = new BigDecimal("75.00");
    private BigDecimal largeTransactionThreshold =
            new BigDecimal("10000.00");
    private int rapidMovementWindowHours = 24;
    private BigDecimal rapidMovementCombinedAmountThreshold =
            new BigDecimal("20000.00");
    private int structuringWindowDays = 7;
    private BigDecimal structuringReportingThreshold =
            new BigDecimal("10000.00");
    private List<String> highRiskCountries = List.of(
            "Iran", "North Korea", "Syria", "Afghanistan"
    );
    private int unusualChannelMinimumHistory = 3;
    private BigDecimal unusualChannelCommonRatio =
            new BigDecimal("0.60");
    private BigDecimal profileMismatchLargeTransactionThreshold =
            new BigDecimal("25000.00");
    private int profileMismatchNewAccountDays = 90;
    private int flaggedTransactionScore = 20;
    private int highTransactionRiskScore = 15;
    private int largeTransactionScore = 15;
    private int highRiskCountryScore = 15;
    private int rapidMovementScore = 20;
    private int structuringScore = 25;
    private int unusualChannelScore = 10;
    private int customerProfileMismatchScore = 15;

    public BigDecimal getHighRiskScoreThreshold() { return highRiskScoreThreshold; }
    public void setHighRiskScoreThreshold(BigDecimal value) { highRiskScoreThreshold = value; }
    public BigDecimal getLargeTransactionThreshold() { return largeTransactionThreshold; }
    public void setLargeTransactionThreshold(BigDecimal value) { largeTransactionThreshold = value; }
    public int getRapidMovementWindowHours() { return rapidMovementWindowHours; }
    public void setRapidMovementWindowHours(int value) { rapidMovementWindowHours = value; }
    public BigDecimal getRapidMovementCombinedAmountThreshold() { return rapidMovementCombinedAmountThreshold; }
    public void setRapidMovementCombinedAmountThreshold(BigDecimal value) { rapidMovementCombinedAmountThreshold = value; }
    public int getStructuringWindowDays() { return structuringWindowDays; }
    public void setStructuringWindowDays(int value) { structuringWindowDays = value; }
    public BigDecimal getStructuringReportingThreshold() { return structuringReportingThreshold; }
    public void setStructuringReportingThreshold(BigDecimal value) { structuringReportingThreshold = value; }
    public List<String> getHighRiskCountries() { return highRiskCountries; }
    public void setHighRiskCountries(List<String> value) { highRiskCountries = List.copyOf(value); }
    public int getUnusualChannelMinimumHistory() { return unusualChannelMinimumHistory; }
    public void setUnusualChannelMinimumHistory(int value) { unusualChannelMinimumHistory = value; }
    public BigDecimal getUnusualChannelCommonRatio() { return unusualChannelCommonRatio; }
    public void setUnusualChannelCommonRatio(BigDecimal value) { unusualChannelCommonRatio = value; }
    public BigDecimal getProfileMismatchLargeTransactionThreshold() { return profileMismatchLargeTransactionThreshold; }
    public void setProfileMismatchLargeTransactionThreshold(BigDecimal value) { profileMismatchLargeTransactionThreshold = value; }
    public int getProfileMismatchNewAccountDays() { return profileMismatchNewAccountDays; }
    public void setProfileMismatchNewAccountDays(int value) { profileMismatchNewAccountDays = value; }
    public int getFlaggedTransactionScore() { return flaggedTransactionScore; }
    public void setFlaggedTransactionScore(int value) { flaggedTransactionScore = value; }
    public int getHighTransactionRiskScore() { return highTransactionRiskScore; }
    public void setHighTransactionRiskScore(int value) { highTransactionRiskScore = value; }
    public int getLargeTransactionScore() { return largeTransactionScore; }
    public void setLargeTransactionScore(int value) { largeTransactionScore = value; }
    public int getHighRiskCountryScore() { return highRiskCountryScore; }
    public void setHighRiskCountryScore(int value) { highRiskCountryScore = value; }
    public int getRapidMovementScore() { return rapidMovementScore; }
    public void setRapidMovementScore(int value) { rapidMovementScore = value; }
    public int getStructuringScore() { return structuringScore; }
    public void setStructuringScore(int value) { structuringScore = value; }
    public int getUnusualChannelScore() { return unusualChannelScore; }
    public void setUnusualChannelScore(int value) { unusualChannelScore = value; }
    public int getCustomerProfileMismatchScore() { return customerProfileMismatchScore; }
    public void setCustomerProfileMismatchScore(int value) { customerProfileMismatchScore = value; }
}
