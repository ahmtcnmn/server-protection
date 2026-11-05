package com.ahmtcnmn.manager.dto.Rules;

import java.time.Instant;
import java.util.List;

/** Panel: dağıtılan kural seti, son yükleme hatası ve Agent'ların senkron durumu. */
public record DtoRulesStatus(
    String version,
    Instant loadedAt,
    String rulesDir,
    String lastError,
    Instant lastErrorAt,
    List<Parser> parsers,
    List<Detector> detectors,
    long agentsTotal,
    long agentsInSync
) {
    public record Parser(String name, String description, String file) {}

    public record Detector(String name, String description, String eventType, int severity, List<String> parsers,
                           String field, List<String> transforms, List<String> patterns, String file) {}
}
