package com.umeshowl.banking.simulation;

import com.umeshowl.banking.screening.TransactionScreeningResult;
import com.umeshowl.banking.screening.TransactionScreeningService;
import com.umeshowl.banking.simulation.dto.LiveTransactionEvent;
import com.umeshowl.banking.simulation.dto.SimulationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TransactionSimulationService {

    private static final Logger log = LoggerFactory.getLogger(
            TransactionSimulationService.class
    );

    private final SimulatedTransactionFactory transactionFactory;
    private final TransactionSimulationEventHub eventHub;
    private final TransactionSimulationScheduler scheduler;
    private final TransactionSimulationProperties properties;
    private final TransactionScreeningService screeningService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<SimulationScenario> activeScenario =
            new AtomicReference<>(SimulationScenario.NORMAL);
    private final AtomicLong transactionsGenerated = new AtomicLong(0);
    private final AtomicReference<OffsetDateTime> startedAt =
            new AtomicReference<>();

    public TransactionSimulationService(
            SimulatedTransactionFactory transactionFactory,
            TransactionSimulationEventHub eventHub,
            TransactionSimulationScheduler scheduler,
            TransactionSimulationProperties properties,
            TransactionScreeningService screeningService
    ) {
        this.transactionFactory = transactionFactory;
        this.eventHub = eventHub;
        this.scheduler = scheduler;
        this.properties = properties;
        this.screeningService = screeningService;
    }

    public synchronized SimulationStatus start(SimulationScenario scenario) {
        if (running.get()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transaction simulation is already running"
            );
        }

        if (scenario != null) {
            activeScenario.set(scenario);
        }

        running.set(true);
        startedAt.set(OffsetDateTime.now(ZoneOffset.UTC));
        scheduler.start(properties.getIntervalMs());

        log.info(
                "simulation_started scenario={} intervalMs={}",
                activeScenario.get(),
                properties.getIntervalMs()
        );

        return status();
    }

    public synchronized SimulationStatus stop() {
        if (!running.get()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transaction simulation is not running"
            );
        }

        running.set(false);
        scheduler.stop();
        startedAt.set(null);

        log.info(
                "simulation_stopped transactionsGenerated={}",
                transactionsGenerated.get()
        );

        return status();
    }

    public SimulationStatus status() {
        return new SimulationStatus(
                running.get(),
                activeScenario.get(),
                properties.getIntervalMs(),
                transactionsGenerated.get(),
                startedAt.get()
        );
    }

    @Transactional
    public SimulationStatus generateScenarioBatch(SimulationScenario scenario) {
        activeScenario.set(scenario);
        publishBatch(transactionFactory.generate(scenario));

        log.info(
                "simulation_scenario_generated scenario={}",
                scenario
        );

        return status();
    }

    @Transactional
    public void generateScheduledBatch() {
        if (!running.get()) {
            return;
        }

        publishBatch(transactionFactory.generate(activeScenario.get()));
    }

    private void publishBatch(
            List<SimulatedTransactionFactory.GeneratedTransaction> generated
    ) {
        for (SimulatedTransactionFactory.GeneratedTransaction item : generated) {
            transactionsGenerated.incrementAndGet();

            TransactionScreeningResult processingResult =
                    screeningService.beginProcessing(item.transaction());

            eventHub.publish(LiveTransactionEvent.from(
                    item.transaction(),
                    item.scenario(),
                    processingResult
            ));

            screeningService.screen(processingResult);
        }
    }
}
