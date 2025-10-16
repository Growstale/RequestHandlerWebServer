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

        @Pattern(
                regexp = "^[0-9]*$",
                message = "Telegram ID должен состоять только из цифр"
        )
        @Size(max = 19, message = "Telegram ID слишком длинный")
        String telegramID
) {}