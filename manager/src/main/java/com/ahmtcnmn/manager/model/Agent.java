package com.ahmtcnmn.manager.model;

import java.beans.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "agents")
@Getter @Setter              
@NoArgsConstructor
@Data
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String hostname;

    @Column(name = "api_key_hash", nullable = false, unique = true)
    private String apiKeyHash;

    @Column(name = "last_seen")
    private Instant lastSeen;   // UTC — zaman dilimi tutarlılığı için Instant

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL)
    private List<Events> events = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Agent'ın son heartbeat'te bildirdiği çalışan kural sürümü (Manager'ınkiyle aynıysa senkron)
    @Column(name = "rules_version", length = 64)
    private String rulesVersion;

    // is_online mantığı — Adım 2.6'da ekleyeceğiz
    @Transient   // Bu alan veritabanına KAYDEDİLMEZ, sadece hesaplanır
    public boolean isOnline() {
        if (lastSeen == null) {
            return false;
        }
        return lastSeen.isAfter(Instant.now().minusSeconds(60));
    }
}

