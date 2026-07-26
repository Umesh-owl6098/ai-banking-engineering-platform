package com.umeshowl.banking.observability;

import com.umeshowl.banking.investigation.InvestigationCaseRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class BankingMetrics {

    private final MeterRegistry meterRegistry;
    private final InvestigationCaseRepository investigationCaseRepository;

    public BankingMetrics(
            MeterRegistry meterRegistry,
            InvestigationCaseRepository investigationCaseRepository
    ) {
        this.meterRegistry = meterRegistry;
        this.investigationCaseRepository = investigationCaseRepository;

        meterRegistry.gauge(
                "investigations.open.current",
                investigationCaseRepository,
                repository -> repository.countByStatusNot("CLOSED")
        );
    }

    public void recordInvestigationCreated() {
        counter("investigations.created.total").increment();
    }

    public void recordStatusTransition(String fromStatus, String toStatus) {
        counter(
                "investigations.status.transitions.total",
                "from_status",
                fromStatus,
                "to_status",
                toStatus
        ).increment();

        if ("CLOSED".equals(toStatus)) {
            counter("investigations.closed.total").increment();
        }
    }

    public Timer.Sample startInvestigationExecutionTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordInvestigationExecutionDuration(Timer.Sample sample) {
        sample.stop(timer("investigation.execution.duration"));
    }

    public void recordInvestigationExecutionFailure() {
        counter("investigation.execution.failures.total").increment();
    }

    public Timer.Sample startAgentExecutionTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordAgentExecution(
            String agentType,
            String result,
            Timer.Sample sample
    ) {
        sample.stop(
                timer(
                        "agent.execution.duration",
                        "agent_type",
                        agentType
                )
        );
        counter(
                "agent.executions.total",
                "agent_type",
                agentType,
                "result",
                result
        ).increment();
    }

    public Timer.Sample startReportGenerationTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordReportGenerated(String generationMode, Timer.Sample sample) {
        sample.stop(timer("investigation.report.duration"));
        counter(
                "investigation.reports.generated.total",
                "generation_mode",
                generationMode
        ).increment();
    }

    public void recordReportFallback() {
        counter("investigation.report.fallback.total").increment();
    }

    public void recordReportFailure(Timer.Sample sample) {
        sample.stop(timer("investigation.report.duration"));
        counter("investigation.report.failures.total").increment();
    }

    public void recordReviewStarted() {
        counter("human.review.started.total").increment();
    }

    public Timer.Sample startHumanReviewTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordReviewDecision(String decision, Timer.Sample sample) {
        if (sample != null) {
            sample.stop(timer("human.review.duration"));
        }
        counter(
                "human.review.decisions.total",
                "decision",
                decision
        ).increment();
    }

    public void recordInvestigationEscalated() {
        counter("investigations.escalated.total").increment();
    }

    public void recordAuthenticationSuccess() {
        counter("authentication.success.total").increment();
    }

    public void recordAuthenticationFailure() {
        counter("authentication.failure.total").increment();
    }

    public void recordAuthorizationDenied() {
        counter("authorization.denied.total").increment();
    }

    public double investigationsCreatedTotal() {
        return counterValue("investigations.created.total");
    }

    public double authenticationSuccessTotal() {
        return counterValue("authentication.success.total");
    }

    public double authenticationFailureTotal() {
        return counterValue("authentication.failure.total");
    }

    public double reportFallbackTotal() {
        return counterValue("investigation.report.fallback.total");
    }

    public double executionFailureTotal() {
        return counterValue("investigation.execution.failures.total");
    }

    public double reportFailureTotal() {
        return counterValue("investigation.report.failures.total");
    }

    public double reviewDecisionsTotal(String decision) {
        return meterRegistry.find("human.review.decisions.total")
                .tag("decision", decision)
                .counter()
                .count();
    }

    private double counterValue(String name) {
        Counter counter = meterRegistry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name)
                .publishPercentileHistogram()
                .tags(tags)
                .register(meterRegistry);
    }
}
