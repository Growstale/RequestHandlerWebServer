package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.service.UpdateBroadcaster;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import java.time.Duration;

@RestController
@RequestMapping("/api/updates")
public class UpdateController {
    private final UpdateBroadcaster broadcaster;

    public UpdateController(UpdateBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamUpdates() {
        return broadcaster.getMessages()
                .mergeWith(Flux.interval(Duration.ofSeconds(15)).map(i -> "ping"));
    }

}