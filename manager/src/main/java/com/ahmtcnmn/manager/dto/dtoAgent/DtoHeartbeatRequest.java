package com.ahmtcnmn.manager.dto.dtoAgent;

import jakarta.validation.constraints.Size;

/** Agent'ın çalışan kural sürümü (yoksa null). Eski Agent'lar gövde göndermez. */
public record DtoHeartbeatRequest(@Size(max = 64) String rulesVersion) {}
