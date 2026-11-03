package com.ahmtcnmn.manager.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Asla banlanmayacak IP/CIDR (panelden eklenir). Config'teki security.whitelisted-ips ayrıca geçerlidir ve silinemez.
 * expiresAt == null → süresiz. Süresi dolan giriş AllowlistCache tarafından silinir.
 */
@Entity
@Table(name = "allowlist")
@Getter
@Setter
@NoArgsConstructor
public class AllowlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "value" MySQL/MariaDB'de anahtar kelime olduğu için sütun adı farklı
    @Column(name = "allow_value", nullable = false, length = 64)
    private String value;

    private String reason;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
