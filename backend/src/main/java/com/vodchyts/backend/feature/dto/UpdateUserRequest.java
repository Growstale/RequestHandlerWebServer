// feature/dto/UpdateUserRequest.java

package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        String password,

        String roleName,

        @Size(max = 200, message = "ФИО не должно превышать 200 символов")
        String fullName,

        @Size(max = 400, message = "Контактная информация не должна превышать 400 символов")
        String contactInfo,

        @Pattern(regexp = "^[0-9,\\s\\-]*$", message = "Telegram ID должен содержать только цифры и запятые")
        @Size(max = 500, message = "Поле Telegram ID слишком длинное")
        String telegramID,

        @Size(max = 100, message = "Ник Telegram слишком длинный")
        String telegramUsername
) {}