package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SendMessageRequest(
        @NotBlank(message = "Текст сообщения не может быть пустым")
        String message,

        @NotEmpty(message = "Нужно выбрать хотя бы одного получателя")
        List<Integer> recipientChatIds
) {}