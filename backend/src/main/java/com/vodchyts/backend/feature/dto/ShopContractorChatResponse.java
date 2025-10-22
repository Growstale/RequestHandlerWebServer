package com.vodchyts.backend.feature.dto;

public record ShopContractorChatResponse(
        Integer shopContractorChatID,
        Integer shopID,
        String shopName,
        Integer contractorID,
        String contractorLogin,
        Long telegramID
) {}