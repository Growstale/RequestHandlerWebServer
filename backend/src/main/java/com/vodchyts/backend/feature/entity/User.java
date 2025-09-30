package com.vodchyts.backend.feature.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "Users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserID")
    private Integer userID;

    @Column(name = "Login", nullable = false, unique = true)
    private String login;

    @Column(name = "Password", nullable = false)
    @JsonIgnore
    private String password;

    @ManyToOne
    @JoinColumn(name = "RoleID")
    private Role role;

    @Column(name = "ContactInfo")
    private String contactInfo;

    @Column(name = "TelegramID", unique = true)
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
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
