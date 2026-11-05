package com.ahmtcnmn.manager.dto.dtoAgent;

import java.time.Instant;

import com.ahmtcnmn.manager.model.Agent;

public record DtoAgentResponse(
    Long id,
    String hostname,
    boolean isOnline,
    Instant lastSeen,
    Instant createdAt,
    String rulesVersion,
    // null → bilinmiyor (Agent sürüm bildirmedi ya da Manager'da kural seti yok)
    Boolean rulesInSync
) {
    public static DtoAgentResponse from(Agent agent, String managerRulesVersion) {
        Boolean inSync = agent.getRulesVersion() == null || managerRulesVersion == null
                ? null : agent.getRulesVersion().equals(managerRulesVersion);
        return new DtoAgentResponse(
            agent.getId(),
            agent.getHostname(),
            agent.isOnline(),
            agent.getLastSeen(),
            agent.getCreatedAt(),
            agent.getRulesVersion(),
            inSync
        );
    }
}