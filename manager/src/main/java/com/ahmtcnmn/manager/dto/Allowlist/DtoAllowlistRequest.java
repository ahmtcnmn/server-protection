package com.ahmtcnmn.manager.dto.Allowlist;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Panelden allowlist girişi. durationMinutes boş → süresiz. */
public record DtoAllowlistRequest(
    @NotBlank(message = "IP ya da CIDR boş olamaz")
    String value,

    @Min(value = 1, message = "Süre en az 1 dakika olmalı")
    @Max(value = 525600, message = "Süre en fazla 1 yıl olabilir")
    Integer durationMinutes,

    @Size(max = 255, message = "Açıklama en fazla 255 karakter olabilir")
    String reason
) {}
