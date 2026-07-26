package com.umeshowl.banking.investigation.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "investigation.evidence")
public class InvestigationEvidenceProperties {

    private int maxCitationsPerAgent = 3;

    private BigDecimal minimumRelevanceScore = new BigDecimal("0.250");

    private int searchLimit = 8;

    private boolean enableHybridSearch = true;

    public int getMaxCitationsPerAgent() {
        return maxCitationsPerAgent;
    }

    public void setMaxCitationsPerAgent(int maxCitationsPerAgent) {
        this.maxCitationsPerAgent = maxCitationsPerAgent;
    }

    public BigDecimal getMinimumRelevanceScore() {
        return minimumRelevanceScore;
    }

    public void setMinimumRelevanceScore(BigDecimal minimumRelevanceScore) {
        this.minimumRelevanceScore = minimumRelevanceScore;
    }

    public int getSearchLimit() {
        return searchLimit;
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }

    public boolean isEnableHybridSearch() {
        return enableHybridSearch;
    }

    public void setEnableHybridSearch(boolean enableHybridSearch) {
        this.enableHybridSearch = enableHybridSearch;
    }
}
