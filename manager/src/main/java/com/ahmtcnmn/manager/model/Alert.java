package com.ahmtcnmn.manager.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * "Ne oldu": bir tespit kuralının (scenario) tetiklendiği an. Decision ise "ne yapılacak"tır.
 * Bir alert'e bağlı event'ler events.alert_id ile işaretlenir.
 */
@Entity
@Table(name = "alerts", indexes = @Index(name = "idx_alerts_source_ip", columnList = "source_ip"))
@Getter
@Setter
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String scenario;

    @Column(name = "source_ip", nullable = false)
    private String sourceIp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "stopped_at", nullable = false)
    private Instant stoppedAt;

    @Column(name = "events_count", nullable = false)
    private int eventsCount;

    @Column(nullable = false)
    private boolean simulated;
}
