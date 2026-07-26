package com.umeshowl.banking.simulation.demo;

import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseRepository;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.screening.TransactionScreeningResult;
import com.umeshowl.banking.screening.TransactionScreeningService;
import com.umeshowl.banking.simulation.SimulationScenario;
import com.umeshowl.banking.simulation.TransactionSimulationEventHub;
import com.umeshowl.banking.simulation.demo.dto.DemoScenarioRunResponse;
import com.umeshowl.banking.simulation.demo.dto.DemoScenarioRunResponse.DemoTransactionResult;
import com.umeshowl.banking.simulation.dto.LiveTransactionEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class DemoScenarioService {

    private final DemoScenarioFactory demoScenarioFactory;
    private final TransactionScreeningService screeningService;
    private final TransactionSimulationEventHub eventHub;
    private final InvestigationCaseRepository investigationCaseRepository;

    public DemoScenarioService(
            DemoScenarioFactory demoScenarioFactory,
            TransactionScreeningService screeningService,
            TransactionSimulationEventHub eventHub,
            InvestigationCaseRepository investigationCaseRepository
    ) {
        this.demoScenarioFactory = demoScenarioFactory;
        this.screeningService = screeningService;
        this.eventHub = eventHub;
        this.investigationCaseRepository = investigationCaseRepository;
    }

    @Transactional
    public DemoScenarioRunResponse run(DemoScenarioType scenarioType) {
        DemoScenarioFactory.DemoScenarioBatch batch =
                demoScenarioFactory.generate(scenarioType);
        List<DemoTransactionResult> transactionResults =
                new ArrayList<>(batch.transactions().size());

        for (MockTransaction transaction : batch.transactions()) {
            TransactionScreeningResult processingResult =
                    screeningService.beginProcessing(transaction);

            eventHub.publish(LiveTransactionEvent.from(
                    transaction,
                    resolveSimulationScenario(scenarioType),
                    processingResult,
                    null,
                    batch.scenarioGroupId()
            ));

            TransactionScreeningResult screened =
                    screeningService.screen(processingResult);

            transactionResults.add(toTransactionResult(transaction, screened));
        }

        InvestigationCase investigation = investigationCaseRepository
                .findFirstByScenarioGroupIdOrderByCreatedAtDesc(
                        batch.scenarioGroupId()
                )
                .orElse(null);

        return new DemoScenarioRunResponse(
                scenarioType.name(),
                batch.scenarioGroupId(),
                batch.transactions().size(),
                transactionResults,
                investigation == null ? null : investigation.getId(),
                investigation == null ? null : investigation.getStatus(),
                buildScreeningSummary(transactionResults)
        );
    }

    private DemoTransactionResult toTransactionResult(
            MockTransaction transaction,
            TransactionScreeningResult screened
    ) {
        return new DemoTransactionResult(
                transaction.getId(),
                transaction.getTransactionReference(),
                screened.getStatus().name(),
                screened.getTriggeredRules() == null
                        ? List.of()
                        : List.copyOf(
                                Arrays.asList(screened.getTriggeredRules())
                        ),
                transaction.getAmount(),
                transaction.getCurrency()
        );
    }

    private SimulationScenario resolveSimulationScenario(
            DemoScenarioType scenarioType
    ) {
        return switch (scenarioType) {
            case STRUCTURING_DEMO -> SimulationScenario.STRUCTURING;
            case HIGH_RISK_WIRE_DEMO -> SimulationScenario.HIGH_RISK_COUNTRY;
            case MONEY_MULE_DEMO -> SimulationScenario.RAPID_MOVEMENT;
            case NORMAL_ACTIVITY_DEMO -> SimulationScenario.NORMAL;
        };
    }

    private String buildScreeningSummary(
            List<DemoTransactionResult> transactionResults
    ) {
        if (transactionResults.isEmpty()) {
            return "No transactions generated";
        }

        DemoTransactionResult last =
                transactionResults.get(transactionResults.size() - 1);
        return last.screeningStatus()
                + " ("
                + String.join(
                        ", ",
                        last.triggeredRules().isEmpty()
                                ? List.of("no rules")
                                : last.triggeredRules()
                )
                + ")";
    }
}
