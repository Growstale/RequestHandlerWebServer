package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Setter
@Getter
@Table("ShopContractorChats")
public class ShopContractorChat {

    @Id
    @Column("ShopContractorChatID")
    private Integer shopContractorChatID;

    @Column("ShopID")
    private Integer shopID;

    @Column("ContractorID")
    private Integer contractorID;

    @Column("TelegramID")
    private Long telegramID;
}