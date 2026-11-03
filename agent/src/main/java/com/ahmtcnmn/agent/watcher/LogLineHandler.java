package com.ahmtcnmn.agent.watcher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.ahmtcnmn.agent.Libs.ColorLogger;
import com.ahmtcnmn.agent.client.HeartbeatTask;
import com.ahmtcnmn.agent.detector.Interface.Detector;
import com.ahmtcnmn.agent.service.RuleService;
import com.ahmtcnmn.engine.CompiledParser;
import com.ahmtcnmn.engine.Detection;
import com.ahmtcnmn.engine.ParsedLine;
import com.ahmtcnmn.engine.RuleSet;

import lombok.RequiredArgsConstructor;

/**
 * Watcher'lardan gelen her satır: kaynağın parser'ları sırayla denenir (ilk uyan kazanır),
 * ayrıştırılan satır tüm Detector'lardan geçer, her tespit Manager'a ayrı event olarak gider.
 */
@Component
@RequiredArgsConstructor
public class LogLineHandler {

    // Bundan uzun satırın fazlası atılır (nginx istek satırı zaten ~8 KB ile sınırlı)
    private static final int MAX_LINE = 32 * 1024;

    private final RuleService ruleService;
    private final List<Detector> detectors;
    private final HeartbeatTask heartbeatTask;

    // "Parser tanımlı değil" uyarısını kaynak başına bir kez yaz
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public void handle(String source, String line, List<String> parserNames) {
        Instant observedAt = Instant.now();
        if (line.length() > MAX_LINE) {
            line = line.substring(0, MAX_LINE);
        }
        ColorLogger.debug("LOG", source + ": " + ColorLogger.cut(line, 120));

        ParsedLine parsed = parse(source, line, parserNames, ruleService.rules());
        if (parsed == null) {
            return;
        }
        for (Detector detector : detectors) {
            for (Detection d : detector.detect(parsed)) {
                ColorLogger.warn("TESPİT", d.rule() + " '" + ColorLogger.cut(d.matched(), 40) + "' | "
                        + ColorLogger.cut(line, 100));
                heartbeatTask.sendDetectedEvent(parsed, d, observedAt);
            }
        }
    }

    private ParsedLine parse(String source, String line, List<String> parserNames, RuleSet rules) {
        for (String name : parserNames) {
            Optional<CompiledParser> parser = rules.parser(name);
            if (parser.isEmpty()) {
                if (warned.add(source + "|" + name)) {
                    ColorLogger.warn("KURAL", source + " için '" + name + "' parser'ı kural setinde yok, bu kaynakta tespit yapılamıyor");
                }
                continue;
            }
            Optional<ParsedLine> parsed = parser.get().parse(line);
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }
        ColorLogger.debug("LOG", source + ": satır " + parserNames + " formatına uymadı, atlandı");
        return null;
    }
}
