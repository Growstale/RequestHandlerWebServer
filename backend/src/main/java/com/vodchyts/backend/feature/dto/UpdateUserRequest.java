package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserRequest(
        String password,
        String roleName,
        String contactInfo,
        @Pattern(regexp = "^[0-9]*$", message = "Telegram ID must contain only digits")
        String telegramID
) {}
