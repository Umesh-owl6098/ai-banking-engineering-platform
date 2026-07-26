package com.umeshowl.banking.investigation;

import com.umeshowl.banking.investigation.dto.InvestigationCreatedNotification;
import com.umeshowl.banking.investigation.execution.InvestigationExecutionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class InvestigationNotificationHub {

    private static final Logger log = LoggerFactory.getLogger(
            InvestigationNotificationHub.class
    );

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        return emitter;
    }

    public void publish(InvestigationCreatedNotification notification) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("investigation-created")
                                .data(notification)
                );
            } catch (IOException exception) {
                emitters.remove(emitter);
                log.debug(
                        "investigation_sse_client_disconnected message={}",
                        exception.getMessage()
                );
            }
        }
    }

    public void publishExecutionEvent(
            InvestigationExecutionEvent event
    ) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("investigation-execution")
                                .data(event)
                );
            } catch (IOException exception) {
                emitters.remove(emitter);
                log.debug(
                        "investigation_execution_sse_client_disconnected message={}",
                        exception.getMessage()
                );
            }
        }
    }
}
