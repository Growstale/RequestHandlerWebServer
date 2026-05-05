package com.vodchyts.backend.feature.dto;

public record ShopResponse(
        Integer shopID,
        String shopName,
        String address,
        Integer userID,
        String userLogin
) {}