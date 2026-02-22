package com.vodchyts.backend.feature.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class UpdateBroadcaster {
    private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void publish(String eventType) {
        sink.tryEmitNext(eventType);
    }

    public Flux<String> getMessages() {
        return sink.asFlux();
    }
}