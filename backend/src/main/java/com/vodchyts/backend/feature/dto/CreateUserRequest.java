package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
        @NotBlank(message = "Login cannot be blank")
        @Size(min = 3, max = 50, message = "Login must be between 3 and 50 characters")
        String login,

        @NotBlank(message = "Password cannot be blank")
        String password,

        @NotBlank(message = "Role name cannot be blank")
        String roleName,

        String fullName,

        String contactInfo,

        @Pattern(
                regexp = "^[0-9]*$",
                message = "Telegram ID must contain only digits"
        )
        String telegramID
) {}