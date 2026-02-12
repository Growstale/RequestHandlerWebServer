package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Setter
@Getter
@Table("AuditLog")
public class AuditLog {

    @Id
    @Column("LogID")
    private Integer logID;

    @Column("TableName")
    private String tableName;

    @Column("Action")
    private String action;

    @Column("RecordID")
    private Integer recordID;

    @Column("UserID")
    private Integer userID;

    @Column("UserLogin")
    private String userLogin;

    @Column("LogDate")
    private LocalDateTime logDate;

    @Column("Changes")
    private String changes;

    @Column("IPAddress")
    private String IPAddress;

    @Column("UserAgent")
    private String userAgent;

    @Column("Endpoint")
    private String endpoint;

    @Column("RequestMethod")
    private String requestMethod;
}

