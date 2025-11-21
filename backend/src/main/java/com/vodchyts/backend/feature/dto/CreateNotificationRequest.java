package com.vodchyts.backend.feature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateNotificationRequest {

    @NotBlank(message = "Заголовок не может быть пустым")
    @Size(max = 200, message = "Заголовок не может превышать 200 символов")
    private String title;

    private String message;

    private byte[] imageData;

    @NotBlank(message = "Cron выражение не может быть пустым")
    @Pattern(regexp = "^\\S+ \\S+ \\S+ \\S+ \\S+$", message = "Неверный формат cron выражения")
    private String cronExpression;

    @NotNull(message = "Статус активности должен быть указан")
    private Boolean isActive;

    private List<Integer> recipientChatIds;
}
