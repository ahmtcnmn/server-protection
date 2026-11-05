package com.ahmtcnmn.manager.dto.Rules;

import java.util.Map;

/** Agent'a giden kural seti: dosya yolu ("detectors/x.yaml") → içerik. Agent aynı sürümü kendisi hesaplar. */
public record DtoRuleBundle(String version, Map<String, String> files) {}
