package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Setter
@Getter
@Table("RequestComments")
public class RequestComment {
    @Id
    @Column("CommentID")
    private Integer commentID;
    @Column("RequestID")
    private Integer requestID;
    @Column("UserID")
    private Integer userID;
    @Column("CommentText")
    private String commentText;
    @Column("CreatedAt")
    private LocalDateTime createdAt;

}