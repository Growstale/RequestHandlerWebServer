package com.vodchyts.backend.feature.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Setter
@Getter
@Table("RefreshTokens")
public class RefreshToken {

    @Id
    @Column("TokenID")
    private Integer tokenID;
    @Column("UserID")
    private Integer userID;
    @Column("TokenHash")
    private String tokenHash;
    @Column("IssuedAt")
    private LocalDateTime issuedAt;
    @Column("ExpiresAt")
    private LocalDateTime expiresAt;

}
