package com.umeshowl.banking.simulation;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TransactionSimulationScheduler implements DisposableBean {

    private final TaskScheduler taskScheduler;
    private final TransactionSimulationService simulationService;
    private final AtomicReference<ScheduledFuture<?>> scheduledFuture =
            new AtomicReference<>();

    public TransactionSimulationScheduler(
            TaskScheduler taskScheduler,
            @Lazy TransactionSimulationService simulationService
    ) {
        this.taskScheduler = taskScheduler;
        this.simulationService = simulationService;
    }

    public synchronized void start(long intervalMs) {
        stop();

        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                simulationService::generateScheduledBatch,
                Duration.ofMillis(intervalMs)
        );
        scheduledFuture.set(future);
    }

    public synchronized void stop() {
        ScheduledFuture<?> future = scheduledFuture.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    public boolean isScheduled() {
        ScheduledFuture<?> future = scheduledFuture.get();
        return future != null && !future.isCancelled();
    }

    @Override
    public void destroy() {
        stop();
    }
}
