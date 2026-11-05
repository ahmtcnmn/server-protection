package com.ahmtcnmn.agent.Dto.Rules;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Manager'dan gelen kural seti: dosya yolu ("detectors/x.yaml") → içerik. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DtoRuleBundle(String version, Map<String, String> files) {}
