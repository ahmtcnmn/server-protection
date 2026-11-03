package com.ahmtcnmn.manager.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Panel oturumunu uzatan refresh token. Ham değer yalnız kullanıcının cookie'sinde durur;
 * DB'de SHA-256 hash'i tutulur (API key'lerde olduğu gibi), DB sızsa bile token kullanılamaz.
 */
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_tokens_username", columnList = "username"))
@Data
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // Dolu ise token kullanılmış (rotation) ya da logout ile iptal edilmiş demektir
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
