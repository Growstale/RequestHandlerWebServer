package com.vodchyts.backend.feature.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CommentResponse(
        Integer commentID,
        Integer requestID,
        String userLogin,
        String commentText,
        LocalDateTime createdAt,
        Integer parentCommentID,
        List<CommentResponse> replies
) {}