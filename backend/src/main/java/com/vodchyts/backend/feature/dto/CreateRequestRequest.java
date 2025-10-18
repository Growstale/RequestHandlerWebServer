package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record CreateRequestRequest(
        @NotBlank(message = "Описание не может быть пустым")
        @Size(max = 2000, message = "Описание не может превышать 2000 символов")
        String description,

        @NotNull(message = "Необходимо выбрать магазин")
        Integer shopID,

        @NotNull(message = "Необходимо выбрать вид работы")
        Integer workCategoryID,

        @NotNull(message = "Необходимо выбрать срочность")
        Integer urgencyID,

        Integer assignedContractorID,

        @Min(value = 1, message = "Количество дней должно быть больше 0")
        Integer customDays
) {}