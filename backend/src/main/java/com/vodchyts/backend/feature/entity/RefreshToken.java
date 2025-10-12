package com.vodchyts.backend.feature.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

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

    public Integer getTokenID() { return tokenID; }
    public void setTokenID(Integer tokenID) { this.tokenID = tokenID; }

    public Integer getUserID() { return userID; }
    public void setUserID(Integer userID) { this.userID = userID; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
