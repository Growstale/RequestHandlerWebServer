package com.vodchyts.backend.feature.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("WorkCategories")
public class WorkCategory {

    @Id
    @Column("WorkCategoryID")
    private Integer workCategoryID;

    @Column("WorkCategoryName")
    private String workCategoryName;

    public Integer getWorkCategoryID() {
        return workCategoryID;
    }

    public void setWorkCategoryID(Integer workCategoryID) {
        this.workCategoryID = workCategoryID;
    }

    public String getWorkCategoryName() {
        return workCategoryName;
    }

    public void setWorkCategoryName(String workCategoryName) {
        this.workCategoryName = workCategoryName;
    }
}