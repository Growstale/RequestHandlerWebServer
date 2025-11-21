package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateNotificationRequest {

    @Size(max = 200, message = "Заголовок не может превышать 200 символов")
    private String title;

    private String message;

    private byte[] imageData;

    @Pattern(regexp = "^\\S+ \\S+ \\S+ \\S+ \\S+$", message = "Неверный формат cron выражения")
    private String cronExpression;

    private Boolean isActive;

    private List<Integer> recipientChatIds;
}
