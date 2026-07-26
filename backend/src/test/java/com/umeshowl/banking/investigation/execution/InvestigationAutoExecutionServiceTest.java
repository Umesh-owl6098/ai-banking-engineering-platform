package com.umeshowl.banking.investigation.execution;

import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseService;
import com.umeshowl.banking.investigation.InvestigationNotificationHub;
import com.umeshowl.banking.notification.NotificationPublisher;
import com.umeshowl.banking.investigation.report.InvestigationReport;
import com.umeshowl.banking.investigation.report.InvestigationReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestigationAutoExecutionServiceTest {

    private static final UUID INVESTIGATION_ID = UUID.fromString(
            "70000000-0000-4000-8000-000000000010"
    );

    private InvestigationCaseService investigationCaseService;
    private InvestigationExecutionService investigationExecutionService;
    private InvestigationReportService investigationReportService;
    private InvestigationExecutionProgressPublisherImpl progressPublisher;
    private InvestigationNotificationHub notificationHub;
    private InvestigationAutoExecutionService autoExecutionService;

    @BeforeEach
    void setUp() {
        investigationCaseService = mock(InvestigationCaseService.class);
        investigationExecutionService = mock(
                InvestigationExecutionService.class
        );
        investigationReportService = mock(InvestigationReportService.class);
        notificationHub = mock(InvestigationNotificationHub.class);
        progressPublisher = new InvestigationExecutionProgressPublisherImpl(
                notificationHub,
                mock(NotificationPublisher.class)
        );
        autoExecutionService = new InvestigationAutoExecutionService(
                investigationCaseService,
                investigationExecutionService,
                investigationReportService,
                progressPublisher
        );

        InvestigationCase investigationCase = new InvestigationCase();
        investigationCase.setId(INVESTIGATION_ID);
        investigationCase.setStatus("NEW");
        when(investigationCaseService.getCase(INVESTIGATION_ID))
                .thenReturn(investigationCase);
        when(investigationCaseService.beginAutoExecution(INVESTIGATION_ID))
                .thenReturn(true);
    }

    @Test
    void runsExecutionReportAndStatusTransitions() {
        InvestigationExecutionSummary summary = new InvestigationExecutionSummary(
                INVESTIGATION_ID,
                null,
                null,
                InvestigationExecutionStatus.COMPLETE,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                java.util.Map.of(),
                0,
                null
        );
        when(investigationExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(summary);
        when(investigationReportService.generateReport(INVESTIGATION_ID))
                .thenReturn(mock(InvestigationReport.class));

        autoExecutionService.executeAutomatically(INVESTIGATION_ID);

        var inOrder = inOrder(
                investigationCaseService,
                investigationExecutionService,
                investigationReportService
        );
        inOrder.verify(investigationCaseService)
                .beginAutoExecution(INVESTIGATION_ID);
        inOrder.verify(investigationExecutionService).execute(INVESTIGATION_ID);
        inOrder.verify(investigationReportService)
                .generateReport(INVESTIGATION_ID);
        inOrder.verify(investigationCaseService)
                .updateStatus(INVESTIGATION_ID, "REPORT_GENERATED");
        inOrder.verify(investigationCaseService)
                .updateStatus(INVESTIGATION_ID, "AWAITING_REVIEW");
    }

    @Test
    void publishesOrderedExecutionEvents() {
        InvestigationExecutionSummary summary = new InvestigationExecutionSummary(
                INVESTIGATION_ID,
                null,
                null,
                InvestigationExecutionStatus.COMPLETE,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                java.util.Map.of(),
                0,
                null
        );
        when(investigationExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(summary);
        when(investigationReportService.generateReport(INVESTIGATION_ID))
                .thenReturn(mock(InvestigationReport.class));

        autoExecutionService.executeAutomatically(INVESTIGATION_ID);

        ArgumentCaptor<InvestigationExecutionEvent> captor =
                ArgumentCaptor.forClass(InvestigationExecutionEvent.class);
        verify(notificationHub, org.mockito.Mockito.atLeast(4))
                .publishExecutionEvent(captor.capture());

        List<InvestigationExecutionEvent> events =
                new ArrayList<>(captor.getAllValues());
        assertTrue(events.size() >= 4);
        assertEquals(
                InvestigationExecutionEventType.INVESTIGATION_CREATED,
                events.getFirst().eventType()
        );
        assertEquals(
                InvestigationExecutionEventType.REPORT_GENERATION_STARTED,
                events.stream()
                        .filter(event ->
                                event.eventType()
                                        == InvestigationExecutionEventType
                                                .REPORT_GENERATION_STARTED
                        )
                        .findFirst()
                        .orElseThrow()
                        .eventType()
        );
        assertEquals(
                InvestigationExecutionEventType.INVESTIGATION_READY_FOR_REVIEW,
                events.getLast().eventType()
        );

        for (int index = 1; index < events.size(); index++) {
            assertTrue(
                    events.get(index).sequence()
                            > events.get(index - 1).sequence()
            );
        }
    }

    @Test
    void marksExecutionFailedWhenAgentExecutionFails() {
        InvestigationExecutionSummary summary = new InvestigationExecutionSummary(
                INVESTIGATION_ID,
                null,
                null,
                InvestigationExecutionStatus.FAILED,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                java.util.Map.of(),
                0,
                null
        );
        when(investigationExecutionService.execute(INVESTIGATION_ID))
                .thenReturn(summary);

        autoExecutionService.executeAutomatically(INVESTIGATION_ID);

        verify(investigationCaseService)
                .beginAutoExecution(INVESTIGATION_ID);
        verify(investigationExecutionService).execute(INVESTIGATION_ID);
        verify(investigationReportService, org.mockito.Mockito.never())
                .generateReport(any());
        verify(investigationCaseService).markExecutionFailed(
                eq(INVESTIGATION_ID),
                eq("AGENT_EXECUTION"),
                eq("Specialist agent execution did not complete successfully")
        );
        verify(investigationCaseService, org.mockito.Mockito.never())
                .updateStatus(eq(INVESTIGATION_ID), eq("AWAITING_REVIEW"));
    }

    @Test
    void marksExecutionFailedWhenUnexpectedExceptionOccurs() {
        when(investigationExecutionService.execute(INVESTIGATION_ID))
                .thenThrow(new IllegalStateException("Report service unavailable"));

        autoExecutionService.executeAutomatically(INVESTIGATION_ID);

        verify(investigationCaseService).markExecutionFailed(
                eq(INVESTIGATION_ID),
                eq("AGENT_EXECUTION"),
                eq("Report service unavailable")
        );
    }
}
