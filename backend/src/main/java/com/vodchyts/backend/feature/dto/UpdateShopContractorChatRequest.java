package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateShopContractorChatRequest(
        Integer shopID,

        Integer contractorID,

        @NotNull(message = "Telegram ID не может быть пустым")
        Long telegramID
) {}