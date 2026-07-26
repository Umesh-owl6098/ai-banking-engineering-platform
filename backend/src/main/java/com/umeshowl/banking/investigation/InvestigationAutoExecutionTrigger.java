package com.umeshowl.banking.investigation;

import com.umeshowl.banking.investigation.execution.InvestigationAutoExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InvestigationAutoExecutionTrigger {

    private static final Logger log = LoggerFactory.getLogger(
            InvestigationAutoExecutionTrigger.class
    );

    private final InvestigationAutoExecutionService autoExecutionService;
    private final TaskExecutor investigationExecutionExecutor;
    private final Set<UUID> inFlightInvestigations =
            ConcurrentHashMap.newKeySet();

    public InvestigationAutoExecutionTrigger(
            InvestigationAutoExecutionService autoExecutionService,
            @Qualifier("investigationExecutionExecutor")
            TaskExecutor investigationExecutionExecutor
    ) {
        this.autoExecutionService = autoExecutionService;
        this.investigationExecutionExecutor = investigationExecutionExecutor;
    }

    public void trigger(UUID investigationId) {
        if (!inFlightInvestigations.add(investigationId)) {
            log.warn(
                    "investigation_auto_execution_duplicate_skipped investigationId={}",
                    investigationId
            );
            return;
        }

        investigationExecutionExecutor.execute(() -> {
            try {
                autoExecutionService.executeAutomatically(
                        investigationId
                );
            } finally {
                inFlightInvestigations.remove(investigationId);
            }
        });
    }
}
