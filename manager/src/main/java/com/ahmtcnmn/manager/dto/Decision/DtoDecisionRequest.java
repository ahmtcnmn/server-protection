package com.ahmtcnmn.manager.dto.Decision;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Panelden elle ban. scope = AGENT ise agentId zorunlu. */
public record DtoDecisionRequest(
    @NotBlank(message = "IP ya da CIDR boş olamaz")
    String value,

    @NotNull(message = "Scope boş olamaz")
    @Pattern(regexp = "GLOBAL|AGENT", message = "Scope GLOBAL ya da AGENT olmalı")
    String scope,

    Long agentId,

    @NotNull(message = "Süre boş olamaz")
    @Min(value = 1, message = "Süre en az 1 dakika olmalı")
    @Max(value = 43200, message = "Süre en fazla 30 gün olabilir")
    Integer durationMinutes,

    @Size(max = 255, message = "Açıklama en fazla 255 karakter olabilir")
    String reason
) {}
