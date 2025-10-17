package com.vodchyts.backend.feature.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("UrgencyCategories")
public class UrgencyCategory {

    @Id
    @Column("UrgencyID")
    private Integer urgencyID;

    @Column("UrgencyName")
    private String urgencyName;

    @Column("DefaultDays")
    private Integer defaultDays;

    public Integer getUrgencyID() {
        return urgencyID;
    }

    public void setUrgencyID(Integer urgencyID) {
        this.urgencyID = urgencyID;
    }

    public String getUrgencyName() {
        return urgencyName;
    }

    public void setUrgencyName(String urgencyName) {
        this.urgencyName = urgencyName;
    }

    public Integer getDefaultDays() {
        return defaultDays;
    }

    public void setDefaultDays(Integer defaultDays) {
        this.defaultDays = defaultDays;
    }
}