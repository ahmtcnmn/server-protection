package com.ahmtcnmn.engine;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Doğrulanmış ve derlenmiş, değişmez kural seti. Yeni kurallar gelince yenisi oluşturulup referans
 * tek seferde değiştirilir; çalışan set hiçbir zaman yarım kalmaz.
 * version: kural dosyalarının SHA-256 özeti (Agent'ın kural sürümünü Manager'la karşılaştırmak için).
 */
public final class RuleSet {

    private final Map<String, CompiledParser> parsers;
    private final List<CompiledDetector> detectors;
    private final String version;

    RuleSet(Map<String, CompiledParser> parsers, List<CompiledDetector> detectors, String version) {
        this.parsers = Map.copyOf(parsers);
        this.detectors = List.copyOf(detectors);
        this.version = version;
    }

    public Optional<CompiledParser> parser(String name) {
        return Optional.ofNullable(parsers.get(name));
    }

    public Map<String, CompiledParser> parsers() {
        return parsers;
    }

    public List<CompiledDetector> detectors() {
        return detectors;
    }

    public String version() {
        return version;
    }
}
