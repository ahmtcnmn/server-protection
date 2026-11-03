package com.ahmtcnmn.manager.dto.Allowlist;

import java.time.Instant;

import com.ahmtcnmn.manager.model.AllowlistEntry;

/** builtIn = true → config'ten gelir (security.whitelisted-ips), id'si yoktur ve silinemez. */
public record DtoAllowlistResponse(
    Long id,
    String value,
    String reason,
    String createdBy,
    Instant createdAt,
    Instant expiresAt,
    boolean builtIn
) {
    public static DtoAllowlistResponse from(AllowlistEntry e) {
        return new DtoAllowlistResponse(e.getId(), e.getValue(), e.getReason(), e.getCreatedBy(),
                e.getCreatedAt(), e.getExpiresAt(), false);
    }

    public static DtoAllowlistResponse builtIn(String value) {
        return new DtoAllowlistResponse(null, value, "security.whitelisted-ips", null, null, null, true);
    }
}
