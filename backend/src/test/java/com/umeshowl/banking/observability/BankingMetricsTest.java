package com.umeshowl.banking.observability;

import com.umeshowl.banking.investigation.InvestigationCaseRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BankingMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private BankingMetrics bankingMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        InvestigationCaseRepository repository =
                mock(InvestigationCaseRepository.class);
        when(repository.countByStatusNot("CLOSED")).thenReturn(4L);
        bankingMetrics = new BankingMetrics(meterRegistry, repository);
    }

    @Test
    void recordsAuthenticationMetrics() {
        bankingMetrics.recordAuthenticationSuccess();
        bankingMetrics.recordAuthenticationFailure();

        assertEquals(1.0, bankingMetrics.authenticationSuccessTotal());
        assertEquals(1.0, bankingMetrics.authenticationFailureTotal());
    }

    @Test
    void recordsReportFallbackMetric() {
        bankingMetrics.recordReportFallback();

        assertEquals(1.0, bankingMetrics.reportFallbackTotal());
    }

    @Test
    void recordsHumanReviewDecisionMetric() {
        bankingMetrics.recordReviewDecision("APPROVED", null);

        assertEquals(
                1.0,
                bankingMetrics.reviewDecisionsTotal("APPROVED")
        );
    }

    @Test
    void recordsInvestigationCreatedMetric() {
        bankingMetrics.recordInvestigationCreated();

        assertEquals(1.0, bankingMetrics.investigationsCreatedTotal());
    }
}
