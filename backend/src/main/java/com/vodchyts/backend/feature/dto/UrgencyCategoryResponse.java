package com.vodchyts.backend.feature.dto;

public record UrgencyCategoryResponse(
        Integer urgencyID,
        String urgencyName,
        Integer defaultDays
) {}