package com.ahmtcnmn.agent.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bu Agent'ın uyguladığı ban kararları (H2). Agent yeniden başlasa da kaybolmaz:
 * açılışta ufw bu tabloyla, Manager'a ulaşınca da Manager'ın listesiyle eşitlenir.
 * Aynı IP için birden fazla karar olabilir; ufw kuralı son karar kalkınca silinir.
 */
@Entity
@Table(name = "applied_decisions")
@Getter
@Setter
@NoArgsConstructor
public class AppliedDecision {

    @Id
    private Long decisionId;

    @Column(name = "decision_value", nullable = false, length = 64)
    private String value;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant appliedAt;
}
