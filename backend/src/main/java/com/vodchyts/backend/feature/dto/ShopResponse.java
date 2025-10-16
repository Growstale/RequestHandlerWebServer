package com.vodchyts.backend.feature.dto;

public record ShopResponse(
        Integer shopID,
        String shopName,
        String address,
        String email,
        Long telegramID,
        Integer userID,
        String userLogin
) {}