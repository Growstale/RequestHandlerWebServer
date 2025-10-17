package com.vodchyts.backend.feature.dto;

import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        int currentPage,
        long totalItems,
        int totalPages
) {
}