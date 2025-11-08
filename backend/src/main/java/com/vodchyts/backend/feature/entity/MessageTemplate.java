package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Setter
@Getter
@Table("MessageTemplates")
public class MessageTemplate {

    @Id
    @Column("MessageID")
    private Integer messageID;

    @Column("Title")
    private String title;

    @Column("Message")
    private String message;

    @Column("CreatedAt")
    private LocalDateTime createdAt;

    @Column("ImageData")
    private byte[] imageData;
}