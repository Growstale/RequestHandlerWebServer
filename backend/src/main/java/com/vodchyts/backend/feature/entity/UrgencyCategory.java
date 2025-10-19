package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Setter
@Getter
@Table("UrgencyCategories")
public class UrgencyCategory {

    @Id
    @Column("UrgencyID")
    private Integer urgencyID;

    @Column("UrgencyName")
    private String urgencyName;

    @Column("DefaultDays")
    private Integer defaultDays;

}