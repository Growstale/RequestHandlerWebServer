package com.vodchyts.backend.feature.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("RequestCustomDays")
public class RequestCustomDay {

    @Id
    @Column("RequestCustomDayID")
    private Integer requestCustomDayID;
    @Column("RequestID")
    private Integer requestID;
    @Column("Days")
    private Integer days;

    public Integer getRequestCustomDayID() { return requestCustomDayID; }
    public void setRequestCustomDayID(Integer requestCustomDayID) { this.requestCustomDayID = requestCustomDayID; }
    public Integer getRequestID() { return requestID; }
    public void setRequestID(Integer requestID) { this.requestID = requestID; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
}