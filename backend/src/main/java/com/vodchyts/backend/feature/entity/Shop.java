package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Setter
@Getter
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

}