package com.vodchyts.backend.feature.dto;

public record LoginResponseWithRefresh(String accessToken, String refreshToken) {}
