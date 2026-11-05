package com.ahmtcnmn.manager.dto.dtoAgent;

/** rulesVersion: Agent'ın çalıştırması gereken kural sürümü; farklıysa GET /api/agent/rules ile çeker. */
public record DtoHeartbeatResponse(String rulesVersion) {}
