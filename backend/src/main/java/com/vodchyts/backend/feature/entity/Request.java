package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Setter
@Getter
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

}