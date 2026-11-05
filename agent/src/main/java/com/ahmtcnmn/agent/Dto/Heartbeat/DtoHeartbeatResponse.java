package com.ahmtcnmn.agent.Dto.Heartbeat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Manager'ın RootEntity sarmalı: { result, messages, data: { rulesVersion } }. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DtoHeartbeatResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String rulesVersion) {}

    public String rulesVersion() {
        return data == null ? null : data.rulesVersion();
    }
}
