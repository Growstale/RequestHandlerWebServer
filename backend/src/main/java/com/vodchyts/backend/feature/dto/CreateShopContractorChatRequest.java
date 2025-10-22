package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.NotNull;

public record CreateShopContractorChatRequest(
        @NotNull(message = "Магазин не может быть пустым")
        Integer shopID,

        @NotNull(message = "Подрядчик не может быть пустым")
        Integer contractorID,

        @NotNull(message = "Telegram ID не может быть пустым")
        Long telegramID
) {}