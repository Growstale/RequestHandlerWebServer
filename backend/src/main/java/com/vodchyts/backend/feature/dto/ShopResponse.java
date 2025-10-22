package com.vodchyts.backend.feature.dto;

public record ShopResponse(
        Integer shopID,
        String shopName,
        String address,
        String email,
        Integer userID,
        String userLogin
) {}