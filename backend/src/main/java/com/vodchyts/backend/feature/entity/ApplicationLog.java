package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Setter
@Getter
@Table("ApplicationLog")
public class ApplicationLog {

    @Id
    @Column("LogID")
    private Long logID;

    @Column("LogLevel")
    private String logLevel;

    @Column("LoggerName")
    private String loggerName;

    @Column("Message")
    private String message;

    @Column("ExceptionMessage")
    private String exceptionMessage;

    @Column("StackTrace")
    private String stackTrace;

    @Column("UserID")
    private Integer userID;

    @Column("UserLogin")
    private String userLogin;

    @Column("IPAddress")
    private String ipAddress;

    @Column("UserAgent")
    private String userAgent;

    @Column("Endpoint")
    private String endpoint;

    @Column("RequestMethod")
    private String requestMethod;

    @Column("RequestID")
    private String requestID;

    @Column("LogDate")
    private LocalDateTime logDate;
}

