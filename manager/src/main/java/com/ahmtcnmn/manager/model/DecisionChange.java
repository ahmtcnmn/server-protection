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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kararlardaki değişikliklerin sıralı günlüğü. Agent son gördüğü id'yi tutar (cursor) ve
 * yalnız ondan sonrasını ister. Zaman yerine artan id kullanılır: saat farkı olsa bile
 * hiçbir değişiklik kaçmaz ya da iki kez gelmez.
 */
@Entity
@Table(name = "decision_changes")
@Getter
@Setter
@NoArgsConstructor
public class DecisionChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    private Decision decision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DecisionChangeAction action;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DecisionChange(Decision decision, DecisionChangeAction action) {
        this.decision = decision;
        this.action = action;
        this.createdAt = Instant.now();
    }
}
