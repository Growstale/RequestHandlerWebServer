package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Setter
@Getter
@Table("Notifications")
public class Notification {

    @Id
    @Column("NotificationID")
    private Integer notificationID;

    @Column("Title")
    private String title;

    @Column("Message")
    private String message;

    @Column("ImageData")
    private byte[] imageData;

    @Column("CronExpression")
    private String cronExpression;

    @Column("IsActive")
    private Boolean isActive;
}
