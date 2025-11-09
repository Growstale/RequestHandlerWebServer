package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.SendMessageRequest;
import com.vodchyts.backend.feature.service.MessagingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/messaging")
@PreAuthorize("hasRole('RetailAdmin')")
public class MessagingController {

    private final MessagingService messagingService;

    public MessagingController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> sendMessage(@Valid @RequestBody Mono<SendMessageRequest> request) {
        return request.flatMap(messagingService::sendMessage);
    }

    @PostMapping(value = "/send-with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> sendMessageWithImage(
            @RequestPart("message") String message,
            @RequestPart("recipientChatIds") String recipientChatIdsStr,
            @RequestPart(name = "image", required = false) Mono<FilePart> imageFile) {

        List<Integer> recipientChatIds = Arrays.stream(recipientChatIdsStr.split(","))
                .map(Integer::parseInt)
                .collect(Collectors.toList());

        return messagingService.sendMessageWithImage(message, recipientChatIds, imageFile);
    }
}