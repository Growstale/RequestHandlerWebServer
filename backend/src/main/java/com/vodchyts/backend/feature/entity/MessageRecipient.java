package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Setter
@Getter
@Table("MessageRecipients")
public class MessageRecipient {

    @Id
    @Column("MessageRecipientsID")
    private Integer messageRecipientsID;

    @Column("MessageID")
    private Integer messageID;

    @Column("ShopContractorChatID")
    private Integer shopContractorChatID;
}