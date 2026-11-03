package com.ahmtcnmn.manager.dto.Event;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Agent'tan gelen event. Alan sırası Agent'taki DtoEventRequest ile aynı tutulur.
 * rule/matchField/matched: hangi kural, hangi alanda, neyi yakaladı. parser + fields: satırın ayrıştırılmış hali.
 * observedAt: Agent saati (bilgi amaçlı; sıralama ve eşik Manager'ın timestamp'iyle yapılır).
 * queued = true → Agent'ın kuyruğunda beklemiş; kaydedilir ama eşiğe sayılmaz.
 */
public record DtoEventRequest(
    @NotBlank @Size(max = 64) String eventType,
    @Min(1) @Max(5) Integer severity,
    @Size(max = 65536) String rawData,
    @Size(max = 64) String sourceIp,
    @Size(max = 64) String rule,
    @Size(max = 64) String matchField,
    @Size(max = 512) String matched,
    @Size(max = 64) String parser,
    @Size(max = 32) Map<String, String> fields,
    Instant observedAt,
    Boolean queued
) {}
