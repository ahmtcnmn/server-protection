package com.ahmtcnmn.manager.dto.Decision;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /api/agent/decisions/stream yanıtı (CrowdSec decision stream modeli).
 * Agent önce "new"leri, sonra "deleted"ları uygular; bir sonraki istekte after = lastChangeId gönderir.
 */
public record DtoDecisionStream(
    long lastChangeId,
    @JsonProperty("new") List<DtoAgentDecision> added,
    List<DtoAgentDecision> deleted
) {}
