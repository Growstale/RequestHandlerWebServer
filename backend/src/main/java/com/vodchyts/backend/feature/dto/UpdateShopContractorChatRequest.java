package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateShopContractorChatRequest(
        @NotNull(message = "Магазин не может быть пустым")
        Integer shopID,

        Integer contractorID,

        @NotNull(message = "Telegram ID не может быть пустым")
        Long telegramID
) {}