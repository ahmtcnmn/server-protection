package com.ahmtcnmn.engine;

import java.time.Instant;
import java.util.Map;

/**
 * Parser'dan geçmiş log satırı.
 * fields: regex'teki named group'lar + extract alanları (eşleşmeyen opsiyonel gruplar yer almaz).
 * time: parser'da timeFormat varsa satırdaki zaman, yoksa ya da çözülemezse null.
 */
public record ParsedLine(String parser, String raw, Map<String, String> fields, Instant time) {

    public String field(String name) {
        return fields.get(name);
    }
}
