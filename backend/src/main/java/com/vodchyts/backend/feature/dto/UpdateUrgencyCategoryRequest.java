package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateUrgencyCategoryRequest(
        @NotNull(message = "Количество дней не может быть пустым")
        @Min(value = 1, message = "Количество дней не может быть меньше 1")
        @Max(value = 365, message = "Количество дней не может быть больше 365")
        Integer defaultDays
) {}