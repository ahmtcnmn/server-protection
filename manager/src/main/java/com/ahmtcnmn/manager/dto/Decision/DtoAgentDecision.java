package com.ahmtcnmn.manager.dto.Decision;

import java.time.Instant;

import com.ahmtcnmn.manager.model.Decision;

/** Decision stream'de Agent'a giden karar. Agent'taki DtoAgentDecision ile alan adları aynı olmalı. */
public record DtoAgentDecision(
    Long id,
    String value,
    String scope,
    Instant expiresAt
) {
    public static DtoAgentDecision from(Decision d) {
        return new DtoAgentDecision(d.getId(), d.getValue(), d.getScope().name(), d.getExpiresAt());
    }
}
