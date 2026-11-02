package com.ahmtcnmn.manager.dto.Event;

import java.time.Instant;

import com.ahmtcnmn.manager.model.Events;

public record DtoEventResponse(
    Long id,
    Long agentId,
    String agentHostname,
    String eventType,
    Instant timestamp,
    Integer severity,
    String rawData,
    String sourceIp
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
            event.getSourceIp()
        );
    }
}