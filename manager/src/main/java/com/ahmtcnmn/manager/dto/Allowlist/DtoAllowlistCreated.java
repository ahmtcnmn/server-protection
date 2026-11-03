package com.ahmtcnmn.manager.dto.Allowlist;

/** Eklenen giriş + bu girişle çakıştığı için kaldırılan aktif ban sayısı. */
public record DtoAllowlistCreated(
    DtoAllowlistResponse entry,
    int revokedDecisions
) {}
