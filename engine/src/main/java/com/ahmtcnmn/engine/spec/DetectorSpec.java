package com.ahmtcnmn.engine.spec;

import java.util.List;

/**
 * rules/detectors/*.yaml dosyasının ham hali.
 * parsers: hangi parser'ların ürettiği satırlara uygulanır.
 * field: incelenecek alan; boşsa ham satır. transforms: incelemeden önce alana uygulanan dönüşümler.
 * maxLength: incelenen metnin en fazla uzunluğu (ReDoS'a karşı); fazlası kesilir.
 */
public record DetectorSpec(
        String name,
        String description,
        String eventType,
        int severity,
        List<String> parsers,
        String field,
        List<String> transforms,
        int maxLength,
        List<String> patterns) {
}
