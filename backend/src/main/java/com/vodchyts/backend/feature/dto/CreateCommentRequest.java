package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "Текст комментария не может быть пустым")
        @Size(max = 1000, message = "Комментарий не может превышать 1000 символов")
        String commentText,
        Integer parentCommentID
) {}