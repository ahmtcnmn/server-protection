package com.ahmtcnmn.manager.dto.Decision;

import java.time.Instant;

import com.ahmtcnmn.manager.model.Decision;

/** Panel için karar görünümü. status: ACTIVE, EXPIRED (süresi doldu) ya da REVOKED (elle kaldırıldı). */
public record DtoDecisionResponse(
    Long id,
    String value,
    String scope,
    String type,
    String origin,
    String scenario,
    String reason,
    Long targetAgentId,
    String targetAgentHostname,
    Long alertId,
    Integer alertEventsCount,
    String alertAgentHostname,
    String createdBy,
    Instant createdAt,
    Instant expiresAt,
    Instant revokedAt,
    String revokedBy,
    String status
) {
    public static DtoDecisionResponse from(Decision d) {
        String status = d.isActive() ? "ACTIVE"
                : Decision.REVOKED_BY_SYSTEM.equals(d.getRevokedBy()) ? "EXPIRED" : "REVOKED";
        return new DtoDecisionResponse(
            d.getId(),
            d.getValue(),
            d.getScope().name(),
            d.getType().name(),
            d.getOrigin().name(),
            d.getScenario(),
            d.getReason(),
            d.getTargetAgent() != null ? d.getTargetAgent().getId() : null,
            d.getTargetAgent() != null ? d.getTargetAgent().getHostname() : null,
            d.getAlert() != null ? d.getAlert().getId() : null,
            d.getAlert() != null ? d.getAlert().getEventsCount() : null,
            d.getAlert() != null ? d.getAlert().getAgent().getHostname() : null,
            d.getCreatedBy(),
            d.getCreatedAt(),
            d.getExpiresAt(),
            d.getRevokedAt(),
            d.getRevokedBy(),
            status
        );
    }
}
