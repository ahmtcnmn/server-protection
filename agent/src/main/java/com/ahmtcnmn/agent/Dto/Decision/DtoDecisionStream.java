package com.ahmtcnmn.agent.Dto.Decision;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /api/agent/decisions/stream yanıtı. Önce "new", sonra "deleted" uygulanır. */
public record DtoDecisionStream(
    long lastChangeId,
    @JsonProperty("new") List<DtoAgentDecision> added,
    List<DtoAgentDecision> deleted
) {}
