package com.vodchyts.backend.feature.dto;

import java.time.LocalDateTime;

public record CommentResponse(
        Integer commentID,
        Integer requestID,
        String userLogin,
        String commentText,
        LocalDateTime createdAt
) {}