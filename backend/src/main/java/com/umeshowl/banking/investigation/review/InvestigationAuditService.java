package com.umeshowl.banking.investigation.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umeshowl.banking.investigation.InvestigationCase;
import com.umeshowl.banking.investigation.InvestigationCaseEvent;
import com.umeshowl.banking.investigation.InvestigationCaseEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class InvestigationAuditService {

    private final InvestigationCaseEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public InvestigationAuditService(
            InvestigationCaseEventRepository eventRepository,
            ObjectMapper objectMapper
    ) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InvestigationCaseEvent recordEvent(
            InvestigationCase investigationCase,
            String eventType,
            String actor,
            Map<String, Object> payload
    ) {
        InvestigationCaseEvent event = new InvestigationCaseEvent();
        event.setInvestigationCase(investigationCase);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setPayload(toJson(payload));
        return eventRepository.save(event);
    }

    @Transactional
    public InvestigationCaseEvent recordStatusChange(
            InvestigationCase investigationCase,
            String actor,
            String previousStatus,
            String newStatus
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previousStatus", previousStatus);
        payload.put("newStatus", newStatus);
        return recordEvent(
                investigationCase,
                InvestigationAuditEventTypes.CASE_STATUS_CHANGED,
                actor,
                payload
        );
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize audit event payload",
                    exception
            );
        }
    }
}
