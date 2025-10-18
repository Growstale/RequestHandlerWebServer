package com.vodchyts.backend.feature.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Table("Requests")
public class Request {

    @Id
    @Column("RequestID")
    private Integer requestID;
    @Column("Description")
    private String description;
    @Column("ShopID")
    private Integer shopID;
    @Column("WorkCategoryID")
    private Integer workCategoryID;
    @Column("UrgencyID")
    private Integer urgencyID;
    @Column("CreatedByUserID")
    private Integer createdByUserID;
    @Column("AssignedContractorID")
    private Integer assignedContractorID;
    @Column("Status")
    private String status;
    @Column("CreatedAt")
    private LocalDateTime createdAt;
    @Column("ClosedAt")
    private LocalDateTime closedAt;
    @Column("IsOverdue")
    private Boolean isOverdue;

    // Геттеры и сеттеры для всех полей
    public Integer getRequestID() { return requestID; }
    public void setRequestID(Integer requestID) { this.requestID = requestID; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getShopID() { return shopID; }
    public void setShopID(Integer shopID) { this.shopID = shopID; }
    public Integer getWorkCategoryID() { return workCategoryID; }
    public void setWorkCategoryID(Integer workCategoryID) { this.workCategoryID = workCategoryID; }
    public Integer getUrgencyID() { return urgencyID; }
    public void setUrgencyID(Integer urgencyID) { this.urgencyID = urgencyID; }
    public Integer getCreatedByUserID() { return createdByUserID; }
    public void setCreatedByUserID(Integer createdByUserID) { this.createdByUserID = createdByUserID; }
    public Integer getAssignedContractorID() { return assignedContractorID; }
    public void setAssignedContractorID(Integer assignedContractorID) { this.assignedContractorID = assignedContractorID; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public Boolean getIsOverdue() { return isOverdue; }
    public void setIsOverdue(Boolean overdue) { isOverdue = overdue; }
}