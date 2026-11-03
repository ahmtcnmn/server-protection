package com.ahmtcnmn.manager.dto.Event;

import java.time.Instant;
import java.util.Map;

import com.ahmtcnmn.manager.model.Events;

public record DtoEventResponse(
    Long id,
    Long agentId,
    String agentHostname,
    String eventType,
    Instant timestamp,
    Integer severity,
    String rawData,
    String sourceIp,
    String rule,
    String matchField,
    String matched,
    String parser,
    Map<String, String> fields,
    Instant observedAt,
    boolean queued
) {
    public static DtoEventResponse from(Events event) {
        return new DtoEventResponse(
            event.getId(),
            event.getAgent().getId(),
            event.getAgent().getHostname(),
            event.getEventType(),
            event.getTimestamp(),
            event.getSeverity(),
            event.getRawData(),
            event.getSourceIp(),
            event.getRule(),
            event.getMatchField(),
            event.getMatched(),
            event.getParser(),
            event.getFields(),
            event.getObservedAt(),
            Boolean.TRUE.equals(event.getQueued())
        );
    }
}
