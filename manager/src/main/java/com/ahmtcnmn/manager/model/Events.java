package com.ahmtcnmn.manager.model;

import java.time.Instant;
import java.util.Map;

import com.ahmtcnmn.manager.model.converter.StringMapJsonConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

    // Eşleşen kural (rules/detectors altındaki name); eski event'lerde ve IP_BLOCKED'da null
    @Column(length = 64)
    private String rule;

    // Kuralın incelediği alan (ör. url); null → ham satır
    @Column(name = "match_field", length = 64)
    private String matchField;

    // Kuralın yakaladığı metin (ör. "UNION SELECT")
    @Column(length = 512)
    private String matched;

    @Column(length = 64)
    private String parser;

    // Parser'ın çıkardığı alanlar (method, url, status, userAgent...), JSON olarak
    @Convert(converter = StringMapJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, String> fields;

    // Agent'ın satırı gördüğü an (Agent saati)
    @Column(name = "observed_at")
    private Instant observedAt;

    // Agent kuyruğunda beklemiş, geç gelmiş event (eşiğe sayılmaz)
    private Boolean queued;

    // Bu event bir alert'i tetikleyen olaylardan biriyse dolu (ör. SQLi eşiği aşıldığında)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Alert alert;
}
