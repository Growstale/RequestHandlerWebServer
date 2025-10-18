package com.vodchyts.backend.feature.dto;

import java.time.LocalDateTime;

public record RequestResponse(
        Integer requestID,
        String description,
        String shopName,
        Integer shopID,
        String workCategoryName,
        Integer workCategoryID,
        String urgencyName,
        Integer urgencyID,
        String assignedContractorName,
        Integer assignedContractorID,
        String status,
        LocalDateTime createdAt,
        Integer daysRemaining,
        long commentCount,
        long photoCount
) {}