package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateMessageTemplateRequest(
        @NotBlank(message = "Заголовок не может быть пустым")
        @Size(max = 200, message = "Заголовок не может превышать 200 символов")
        String title,

        @Size(max = 4000, message = "Сообщение не может превышать 4000 символов")
        String message,

        List<Integer> recipientChatIds

) {}