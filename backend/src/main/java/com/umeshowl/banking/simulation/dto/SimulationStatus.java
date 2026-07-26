package com.umeshowl.banking.simulation.dto;

import com.umeshowl.banking.simulation.SimulationScenario;

import java.time.OffsetDateTime;

public record SimulationStatus(
        boolean running,
        SimulationScenario scenario,
        long intervalMs,
        long transactionsGenerated,
        OffsetDateTime startedAt
) {
}
