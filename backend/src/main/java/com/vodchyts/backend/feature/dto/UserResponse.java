package com.vodchyts.backend.feature.dto;

public record UserResponse(
        Integer userID,
        String login,
        String roleName,
        String fullName,
        String contactInfo,
        Long telegramID
) {}
