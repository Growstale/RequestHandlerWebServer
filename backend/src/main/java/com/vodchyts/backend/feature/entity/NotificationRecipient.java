package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Setter
@Getter
@Table("NotificationRecipients")
public class NotificationRecipient {

    @Id
    @Column("NotificationRecipientID")
    private Integer notificationRecipientID;

    @Column("NotificationID")
    private Integer notificationID;

    @Column("ShopContractorChatID")
    private Integer shopContractorChatID;
}
