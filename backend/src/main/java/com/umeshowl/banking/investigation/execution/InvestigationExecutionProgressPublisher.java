package com.umeshowl.banking.investigation.execution;

import java.util.UUID;

public interface InvestigationExecutionProgressPublisher {

    void publish(InvestigationExecutionEvent event);

    int nextSequence(UUID investigationId);

    void resetSequence(UUID investigationId);

    static InvestigationExecutionProgressPublisher noop() {
        return new InvestigationExecutionProgressPublisher() {
            @Override
            public void publish(InvestigationExecutionEvent event) {
            }

            @Override
            public int nextSequence(UUID investigationId) {
                return 0;
            }

            @Override
            public void resetSequence(UUID investigationId) {
            }
        };
    }
}
