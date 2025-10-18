package com.vodchyts.backend.feature.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

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

    // Геттеры и сеттеры
    public Integer getCommentID() { return commentID; }
    public void setCommentID(Integer commentID) { this.commentID = commentID; }
    public Integer getRequestID() { return requestID; }
    public void setRequestID(Integer requestID) { this.requestID = requestID; }
    public Integer getUserID() { return userID; }
    public void setUserID(Integer userID) { this.userID = userID; }
    public String getCommentText() { return commentText; }
    public void setCommentText(String commentText) { this.commentText = commentText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}