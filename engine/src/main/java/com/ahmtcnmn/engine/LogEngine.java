package com.ahmtcnmn.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bir log satırını kural setinden geçirir: önce parser (sırayla denenir, ilk uyan kazanır),
 * sonra o parser'a bağlı detector'lar. Durumsuzdur; aynı satır her zaman aynı sonucu verir (replay ve testler için).
 */
public final class LogEngine {

    /** parsed == null → satır verilen parser'ların hiçbirine uymadı. */
    public record Result(ParsedLine parsed, List<Detection> detections) {
        public boolean parsedOk() {
            return parsed != null;
        }
    }

    private LogEngine() {}

    public static Result process(RuleSet rules, String line, List<String> parserNames) {
        Optional<ParsedLine> parsed = Optional.empty();
        for (String name : parserNames) {
            CompiledParser parser = rules.parser(name)
                    .orElseThrow(() -> new IllegalArgumentException("Tanımsız parser: " + name));
            parsed = parser.parse(line);
            if (parsed.isPresent()) break;
        }
        if (parsed.isEmpty()) {
            return new Result(null, List.of());
        }
        ParsedLine p = parsed.get();
        List<Detection> detections = new ArrayList<>();
        for (CompiledDetector d : rules.detectors()) {
            if (d.appliesTo(p.parser())) {
                d.detect(p).ifPresent(detections::add);
            }
        }
        return new Result(p, List.copyOf(detections));
    }
}
