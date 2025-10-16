// feature/dto/CreateUserRequest.java

package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
        @NotBlank(message = "Логин не может быть пустым")
        @Size(min = 3, max = 100, message = "Логин должен содержать от 3 до 100 символов")
        String login,

        @NotBlank(message = "Пароль не может быть пустым")
        String password,

        @NotBlank(message = "Имя роли не может быть пустым")
        String roleName,

        @Size(max = 200, message = "ФИО не должно превышать 200 символов")
        String fullName,

        @Size(max = 400, message = "Контактная информация не должна превышать 400 символов")
        String contactInfo,

        @Pattern(
                regexp = "^[0-9]*$",
                message = "Telegram ID должен состоять только из цифр"
        )
        @Size(max = 19, message = "Telegram ID слишком длинный")
        String telegramID
) {}