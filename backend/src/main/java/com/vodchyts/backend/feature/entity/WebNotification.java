package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Getter
@Setter
@Table("WebNotifications")
public class WebNotification {
    @Id
    @Column("NotificationID")
    private Integer notificationID;

    @Column("UserID")
    private Integer userID;

    @Column("RequestID")
    private Integer requestID;

    @Column("Title")
    private String title;

    @Column("Message")
    private String message;

    @Column("CreatedAt")
    private LocalDateTime createdAt;

    @Column("IsRead")
    private Boolean isRead;
}