package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Setter
@Getter
@Table("RequestCustomDays")
public class RequestCustomDay {

    @Id
    @Column("RequestCustomDayID")
    private Integer requestCustomDayID;
    @Column("RequestID")
    private Integer requestID;
    @Column("Days")
    private Integer days;

}