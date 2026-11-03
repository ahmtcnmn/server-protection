package com.ahmtcnmn.agent.Dto.Decision;

import java.time.Instant;

/** Manager'daki DtoAgentDecision ile aynı alan adları. */
public record DtoAgentDecision(
    Long id,
    String value,
    String scope,
    Instant expiresAt
) {}
