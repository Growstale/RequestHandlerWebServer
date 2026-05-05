package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateShopRequest(
        @NotBlank(message = "Название магазина не может быть пустым")
        @Size(max = 150, message = "Название магазина не может превышать 150 символов")
        String shopName,

        @Size(max = 300, message = "Адрес не может превышать 300 символов")
        String address,

        Integer userID
) {}