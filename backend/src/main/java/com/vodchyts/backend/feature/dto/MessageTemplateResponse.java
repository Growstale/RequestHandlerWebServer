package com.vodchyts.backend.feature.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MessageTemplateResponse(
        Integer messageID,
        String title,
        String message,
        LocalDateTime createdAt,
        boolean hasImage,
        List<Integer> recipientChatIds
) {}