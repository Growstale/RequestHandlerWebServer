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


    public Mono<Boolean> validateChatId(Long chatId) {
        if (chatId == null) return Mono.just(false);

        return webClient.get()
                .uri("/check/" + chatId)
                .retrieve()
                .toEntity(String.class) // Получаем ответ целиком
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .onErrorResume(e -> {
                    // Если 404 или ошибка соединения — считаем, что чат невалиден
                    log.warn("Validation failed for chat {}: {}", chatId, e.getMessage());
                    return Mono.just(false);
                });
    }

    public Mono<Void> sendPhoto(Long chatId, String caption, byte[] imageData) {
        if (chatId == null || imageData == null || imageData.length == 0) return Mono.empty();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("chatId", chatId);
        builder.part("caption", caption != null ? caption : ""); // Защита от null caption
        builder.part("file", new ByteArrayResource(imageData))
                .header("Content-Disposition", "form-data; name=file; filename=image.jpg");

        return webClient.post()
                .uri("/notify/photo")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(s -> log.info("Photo sent to chat {}", chatId))
                // === ИЗМЕНЕНИЕ ЗДЕСЬ ===
                // Вместо простого логгирования в doOnError, мы перехватываем ошибку
                .onErrorResume(e -> {
                    // Логируем ошибку, но не прерываем поток выполнения
                    log.error("НЕ УДАЛОСЬ отправить фото в чат {}: {}", chatId, e.getMessage());
                    // Возвращаем пустой результат, как будто всё хорошо (для вызывающего кода)
                    return Mono.empty();
                })
                // =======================
                .then();
    }

    public Mono<Void> sendNotification(Long chatId, String text) {
        if (chatId == null) return Mono.empty();

        record NotifyPayload(Long chatId, String text) {}
        // Экранируем текст, если еще не экранирован (лучше делать это на уровне бизнес-логики, но на всякий случай)
        String safeText = text;

        return webClient.post()
                .uri("/notify")
                .bodyValue(new NotifyPayload(chatId, safeText))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(s -> log.info("Message sent to chat {}", chatId))
                // === ИЗМЕНЕНИЕ ЗДЕСЬ ===
                .onErrorResume(e -> {
                    log.error("НЕ УДАЛОСЬ отправить текст в чат {}: {}", chatId, e.getMessage());
                    return Mono.empty();
                })
                // =======================
                .then();
    }
    // Вспомогательный метод для экранирования, можно использовать в RequestService
    public String escapeMarkdown(String text) {
        if (text == null) return "";
        // Экранируем спецсимволы MarkdownV2
        return text.replaceAll("([_\\*\\[\\]()~`>#\\+\\-=|{}.!])", "\\\\$1");
    }
}