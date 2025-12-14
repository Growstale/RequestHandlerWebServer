package com.vodchyts.backend.feature.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class TelegramNotificationService {

    @Value("${bot.url:http://localhost:8081}")
    private String botUrl;

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);
    private final WebClient webClient;

    public TelegramNotificationService(@Value("${bot.url:http://localhost:8081}") String botUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(botUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024))
                .build();
    }

    public Mono<Void> sendNotification(Long chatId, String text) {
        if (chatId == null) {
            log.warn("Попытка отправки уведомления с chatId = null");
            return Mono.empty();
        }

        // 1. Лог ПЕРЕД отправкой
        log.info(">>> ОТПРАВКА В БОТ: chatId={}, text={}", chatId, text);

        record NotifyPayload(Long chatId, String text) {}
        String safeText = text;

        return webClient.post()
                .uri("/notify")
                .bodyValue(new NotifyPayload(chatId, safeText))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(s -> log.info("<<< УСПЕХ: Бот принял сообщение для {}", chatId)) // 2. Лог ПОСЛЕ успеха
                .doOnError(e -> log.error("!!! ОШИБКА отправки в бот для {}: {}", chatId, e.getMessage()))
                .then();
    }


    public Mono<Void> sendPhoto(Long chatId, String caption, byte[] imageData) {
        if (chatId == null || imageData == null || imageData.length == 0) return Mono.empty();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("chatId", chatId);
        builder.part("caption", caption);
        builder.part("file", new ByteArrayResource(imageData))
                .header("Content-Disposition", "form-data; name=file; filename=image.jpg");

        return webClient.post()
                .uri("/notify/photo")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(s -> log.info("Photo sent to chat {}", chatId))
                .doOnError(e -> log.error("Failed to send photo to chat {}: {}", chatId, e.getMessage()))
                .then();
    }

    // Вспомогательный метод для экранирования, можно использовать в RequestService
    public String escapeMarkdown(String text) {
        if (text == null) return "";
        // Экранируем спецсимволы MarkdownV2
        return text.replaceAll("([_\\*\\[\\]()~`>#\\+\\-=|{}.!])", "\\\\$1");
    }
}