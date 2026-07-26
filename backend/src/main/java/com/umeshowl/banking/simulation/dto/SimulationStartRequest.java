package com.umeshowl.banking.simulation.dto;

import com.umeshowl.banking.simulation.SimulationScenario;

public record SimulationStartRequest(
        SimulationScenario scenario
) {
}
