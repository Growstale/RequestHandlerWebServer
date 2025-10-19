package com.vodchyts.backend.feature.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

@Setter
@Getter
@Table("Users")
public class User {

    @Id
    @Column("UserID")
    private Integer userID;

    @Column("Login")
    private String login;

    @Column("Password")
    @JsonIgnore
    private String password;

    @Column("RoleID")
    private Integer roleID;

    @Column("FullName")
    private String fullName;

    @Column("ContactInfo")
    private String contactInfo;

    @Column("TelegramID")
    private Long telegramID;

}
