package com.umeshowl.banking.simulation;

import com.umeshowl.banking.simulation.dto.LiveTransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class TransactionSimulationEventHub {

    private static final Logger log = LoggerFactory.getLogger(
            TransactionSimulationEventHub.class
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

    public void publish(LiveTransactionEvent event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("transaction")
                                .data(event)
                );
            } catch (IOException exception) {
                emitters.remove(emitter);
                log.debug(
                        "simulation_sse_client_disconnected message={}",
                        exception.getMessage()
                );
            }
        }
    }
}
