package com.ahmtcnmn.manager.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * "Ne yapılacak": bir IP/CIDR için süreli ban. Agent'lar kararları decision stream'den okur ve ufw'ye uygular.
 * Aktif: revokedAt == null. Süresi dolunca DecisionExpiryJob revokedAt'ı doldurur (revokedBy = "system").
 */
@Entity
@Table(name = "decisions", indexes = {
    @Index(name = "idx_decisions_value", columnList = "decision_value"),
    @Index(name = "idx_decisions_active", columnList = "revoked_at, expires_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Decision {

    public static final String REVOKED_BY_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // IP ya da CIDR (ör. 203.0.113.7, 10.0.0.0/24)
    // "value" MySQL/MariaDB'de anahtar kelime olduğu için sütun adı farklı
    @Column(name = "decision_value", nullable = false, length = 64)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DecisionScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DecisionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DecisionOrigin origin;

    // origin = SCENARIO ise kuralın adı
    private String scenario;

    // origin = MANUAL ise admin'in yazdığı açıklama
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    // scope = AGENT ise kararın uygulanacağı tek Agent
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_agent_id")
    private Agent targetAgent;

    // MANUAL kararlarda kararı veren kullanıcı
    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private String revokedBy;

    @Column(nullable = false)
    private boolean simulated;

    public boolean isActive() {
        return revokedAt == null;
    }
}
