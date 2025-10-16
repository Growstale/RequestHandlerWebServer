package com.vodchyts.backend.feature.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("Shops")
public class Shop {

    @Id
    @Column("ShopID")
    private Integer shopID;

    @Column("ShopName")
    private String shopName;

    @Column("Address")
    private String address;

    @Column("Email")
    private String email;

    @Column("TelegramID")
    private Long telegramID;

    @Column("UserID")
    private Integer userID;

    public Integer getShopID() { return shopID; }
    public void setShopID(Integer shopID) { this.shopID = shopID; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getTelegramID() { return telegramID; }
    public void setTelegramID(Long telegramID) { this.telegramID = telegramID; }
    public Integer getUserID() { return userID; }
    public void setUserID(Integer userID) { this.userID = userID; }
}