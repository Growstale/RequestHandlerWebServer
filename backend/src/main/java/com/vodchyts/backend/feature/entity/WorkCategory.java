package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Setter
@Getter
@Table("WorkCategories")
public class WorkCategory {

    @Id
    @Column("WorkCategoryID")
    private Integer workCategoryID;

    @Column("WorkCategoryName")
    private String workCategoryName;

}