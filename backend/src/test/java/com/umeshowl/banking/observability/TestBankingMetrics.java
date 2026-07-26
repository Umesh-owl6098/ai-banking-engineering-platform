package com.umeshowl.banking.observability;

import com.umeshowl.banking.investigation.InvestigationCaseRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TestBankingMetrics {

    private TestBankingMetrics() {
    }

    public static BankingMetrics create() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        InvestigationCaseRepository repository =
                mock(InvestigationCaseRepository.class);
        when(repository.countByStatusNot("CLOSED")).thenReturn(0L);
        return new BankingMetrics(meterRegistry, repository);
    }
}
