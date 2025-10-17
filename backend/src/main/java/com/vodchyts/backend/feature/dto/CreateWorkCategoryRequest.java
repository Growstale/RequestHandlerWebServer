package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkCategoryRequest(
        @NotBlank(message = "Название категории не может быть пустым")
        @Size(max = 150, message = "Название категории не может превышать 150 символов")
        String workCategoryName
) {}