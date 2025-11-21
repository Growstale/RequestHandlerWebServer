package com.vodchyts.backend.feature.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class NotificationResponse {

    private Integer notificationID;
    private String title;
    private String message;
    private boolean hasImage;
    private String cronExpression;
    private Boolean isActive;
    private List<Integer> recipientChatIds;
}
