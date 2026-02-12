package com.vodchyts.backend.feature.dto;

import java.util.List;

public record PagedLogResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int pageSize
) {
}

