package com.ahmtcnmn.agent.Dto.responder;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;

/**
 * Manager'a giden event. Alan sırası Manager'daki DtoEventRequest ile aynı tutulur.
 * rule: eşleşen kural (YAML name), matchField: incelenen alan (null → ham satır), matched: yakalanan metin.
 * parser + fields: satırın ayrıştırılmış hali. observedAt: Agent'ın satırı gördüğü an (Agent saati).
 * queued: Manager'a ulaşılamadığı için kuyrukta beklemiş (Manager bu event'leri eşiğe saymaz).
 */
@Builder(toBuilder = true)
public record DtoEventRequest(
    String eventType,
    Integer severity,
    String rawData,
    String sourceIp,
    String rule,
    String matchField,
    String matched,
    String parser,
    Map<String, String> fields,
    Instant observedAt,
    Boolean queued
) {}
