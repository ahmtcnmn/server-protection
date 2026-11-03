package com.ahmtcnmn.manager.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ahmtcnmn.manager.Libs.ColorLogger;
import com.ahmtcnmn.manager.Libs.IpValues;
import com.ahmtcnmn.manager.model.AllowlistEntry;
import com.ahmtcnmn.manager.repository.AllowlistRepository;

import lombok.RequiredArgsConstructor;

/**
 * Allowlist'in bellekteki kopyası: her ban kararında DB'ye gidilmesin.
 * Ekleme/silmede AllowlistService yeniler; süresi dolan girişler dakikada bir silinir.
 * Config'teki security.whitelisted-ips her zaman listededir (whitelist hiçbir zaman boşalmaz).
 */
@Component
@RequiredArgsConstructor
public class AllowlistCache {

    private record Entry(String value, Instant expiresAt) {
        boolean activeAt(Instant now) {
            return expiresAt == null || expiresAt.isAfter(now);
        }
    }

    private final AllowlistRepository allowlistRepository;

    @Value("${security.whitelisted-ips}")
    private List<String> builtIn;

    private volatile List<Entry> entries = List.of();

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        refresh();
    }

    public void refresh() {
        entries = allowlistRepository.findAll().stream()
                .map(e -> new Entry(e.getValue(), e.getExpiresAt()))
                .toList();
    }

    /** Değer (IP ya da CIDR) config'teki ya da paneldeki bir allowlist girişiyle çakışıyor mu? */
    public boolean overlaps(String value) {
        if (IpValues.overlapsAny(value, builtIn)) return true;
        Instant now = Instant.now();
        List<String> active = entries.stream().filter(e -> e.activeAt(now)).map(Entry::value).toList();
        return IpValues.overlapsAny(value, active);
    }

    public List<String> builtInValues() {
        return builtIn.stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void removeExpired() {
        List<AllowlistEntry> expired = allowlistRepository.findByExpiresAtBefore(Instant.now());
        if (expired.isEmpty()) return;
        allowlistRepository.deleteAll(expired);
        expired.forEach(e -> ColorLogger.info("Allowlist girişi süresi doldu: " + e.getValue()));
        refresh();
    }
}
