package com.umeshowl.banking.simulation;

import com.umeshowl.banking.simulation.dto.SimulationStartRequest;
import com.umeshowl.banking.simulation.dto.SimulationStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/simulation")
public class TransactionSimulationController {

    private final TransactionSimulationService simulationService;
    private final TransactionSimulationEventHub eventHub;

    public TransactionSimulationController(
            TransactionSimulationService simulationService,
            TransactionSimulationEventHub eventHub
    ) {
        this.simulationService = simulationService;
        this.eventHub = eventHub;
    }

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public SimulationStatus start(
            @RequestBody(required = false) SimulationStartRequest request
    ) {
        SimulationScenario scenario = request == null
                ? null
                : request.scenario();
        return simulationService.start(scenario);
    }

    @PostMapping("/stop")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public SimulationStatus stop() {
        return simulationService.stop();
    }

    @GetMapping("/status")
    public SimulationStatus status() {
        return simulationService.status();
    }

    @PostMapping("/scenario/{scenario}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public SimulationStatus generateScenario(
            @PathVariable SimulationScenario scenario
    ) {
        return simulationService.generateScenarioBatch(scenario);
    }

    @GetMapping(value = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter liveStream() {
        return eventHub.subscribe();
    }
}
