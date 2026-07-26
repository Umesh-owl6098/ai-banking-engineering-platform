package com.umeshowl.banking.investigation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InvestigationAutoExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(
            InvestigationAutoExecutionListener.class
    );

    private final InvestigationAutoExecutionTrigger autoExecutionTrigger;

    public InvestigationAutoExecutionListener(
            InvestigationAutoExecutionTrigger autoExecutionTrigger
    ) {
        this.autoExecutionTrigger = autoExecutionTrigger;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvestigationAutoCreated(
            InvestigationAutoCreatedEvent event
    ) {
        log.info(
                "investigation_auto_execution_scheduled investigationId={}",
                event.investigationId()
        );
        autoExecutionTrigger.trigger(event.investigationId());
    }
}
