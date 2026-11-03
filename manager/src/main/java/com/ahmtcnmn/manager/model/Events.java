package com.ahmtcnmn.manager.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Table(name = "events")
public class Events {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent; 

    @Column(nullable = false, name = "event_type")
    private String eventType;

    @Column(nullable = false)
    private Instant timestamp;

    @Min(1) @Max(5)
    private Integer severity;

    @Column(columnDefinition = "TEXT")
    private String rawData;
    
    @Column(name = "source_ip")
    private String sourceIp;

    // Bu event bir alert'i tetikleyen olaylardan biriyse dolu (ör. SQLi eşiği aşıldığında)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Alert alert;
}
