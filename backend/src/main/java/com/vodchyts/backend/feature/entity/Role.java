package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

@Setter
@Getter
@Table("Roles")
public class Role {

    @Id
    @Column("RoleID")
    private Integer roleID;

    @Column("RoleName")
    private String roleName;

}
