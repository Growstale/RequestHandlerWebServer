package com.vodchyts.backend.feature.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

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

    @Column("ContactInfo")
    private String contactInfo;

    @Column("TelegramID")
    private Long telegramID;

    public Integer getUserID() {
        return userID;
    }

    public void setUserID(Integer userID) {
        this.userID = userID;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getRoleID() {
        return roleID;
    }

    public void setRoleID(Integer roleID) {
        this.roleID = roleID;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public Long getTelegramID() {
        return telegramID;
    }

    public void setTelegramID(Long telegramID) {
        this.telegramID = telegramID;
    }
}
