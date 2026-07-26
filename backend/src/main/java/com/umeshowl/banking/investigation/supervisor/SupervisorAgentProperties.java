package com.umeshowl.banking.investigation.supervisor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "investigation.supervisor.aml")
public class SupervisorAgentProperties {

    private BigDecimal amountThreshold = new BigDecimal("10000.00");

    private BigDecimal riskScoreThreshold = new BigDecimal("75.00");

    public BigDecimal getAmountThreshold() {
        return amountThreshold;
    }

    public void setAmountThreshold(BigDecimal amountThreshold) {
        this.amountThreshold = amountThreshold;
    }

    public BigDecimal getRiskScoreThreshold() {
        return riskScoreThreshold;
    }

    public void setRiskScoreThreshold(
            BigDecimal riskScoreThreshold
    ) {
        this.riskScoreThreshold = riskScoreThreshold;
    }
}
