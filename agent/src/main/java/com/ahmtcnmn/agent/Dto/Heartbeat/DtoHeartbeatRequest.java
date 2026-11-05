package com.ahmtcnmn.agent.Dto.Heartbeat;

/** Agent'ın çalışan kural sürümü (kural yoksa null); Manager panelde senkron durumunu gösterir. */
public record DtoHeartbeatRequest(String rulesVersion) {}
