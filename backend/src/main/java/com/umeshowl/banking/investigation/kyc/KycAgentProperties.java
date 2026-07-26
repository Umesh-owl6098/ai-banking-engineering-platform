package com.umeshowl.banking.investigation.kyc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "kyc")
public class KycAgentProperties {

    private List<String> highRiskCountries = List.of(
            "Iran", "North Korea", "Syria", "Afghanistan"
    );
    private int newAccountDays = 90;
    private int recentActivityDays = 30;
    private BigDecimal highValueTransactionThreshold =
            new BigDecimal("10000.00");
    private int kycNotVerifiedScore = 15;
    private int missingOccupationScore = 10;
    private int missingSourceOfFundsScore = 15;
    private int pepCustomerScore = 20;
    private int highCustomerRiskScore = 15;
    private int restrictedNationalityScore = 15;
    private int restrictedResidenceScore = 15;
    private int newAccountScore = 10;
    private int inactiveAccountScore = 15;
    private int profileInconsistencyScore = 20;

    public List<String> getHighRiskCountries() { return highRiskCountries; }
    public void setHighRiskCountries(List<String> value) { highRiskCountries = List.copyOf(value); }
    public int getNewAccountDays() { return newAccountDays; }
    public void setNewAccountDays(int value) { newAccountDays = value; }
    public int getRecentActivityDays() { return recentActivityDays; }
    public void setRecentActivityDays(int value) { recentActivityDays = value; }
    public BigDecimal getHighValueTransactionThreshold() { return highValueTransactionThreshold; }
    public void setHighValueTransactionThreshold(BigDecimal value) { highValueTransactionThreshold = value; }
    public int getKycNotVerifiedScore() { return kycNotVerifiedScore; }
    public void setKycNotVerifiedScore(int value) { kycNotVerifiedScore = value; }
    public int getMissingOccupationScore() { return missingOccupationScore; }
    public void setMissingOccupationScore(int value) { missingOccupationScore = value; }
    public int getMissingSourceOfFundsScore() { return missingSourceOfFundsScore; }
    public void setMissingSourceOfFundsScore(int value) { missingSourceOfFundsScore = value; }
    public int getPepCustomerScore() { return pepCustomerScore; }
    public void setPepCustomerScore(int value) { pepCustomerScore = value; }
    public int getHighCustomerRiskScore() { return highCustomerRiskScore; }
    public void setHighCustomerRiskScore(int value) { highCustomerRiskScore = value; }
    public int getRestrictedNationalityScore() { return restrictedNationalityScore; }
    public void setRestrictedNationalityScore(int value) { restrictedNationalityScore = value; }
    public int getRestrictedResidenceScore() { return restrictedResidenceScore; }
    public void setRestrictedResidenceScore(int value) { restrictedResidenceScore = value; }
    public int getNewAccountScore() { return newAccountScore; }
    public void setNewAccountScore(int value) { newAccountScore = value; }
    public int getInactiveAccountScore() { return inactiveAccountScore; }
    public void setInactiveAccountScore(int value) { inactiveAccountScore = value; }
    public int getProfileInconsistencyScore() { return profileInconsistencyScore; }
    public void setProfileInconsistencyScore(int value) { profileInconsistencyScore = value; }
}
