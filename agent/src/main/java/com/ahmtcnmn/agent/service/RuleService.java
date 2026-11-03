package com.ahmtcnmn.agent.service;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ahmtcnmn.agent.Libs.ColorLogger;
import com.ahmtcnmn.engine.RuleLoader;
import com.ahmtcnmn.engine.RuleSet;

import jakarta.annotation.PostConstruct;

/**
 * Agent'ın çalışan kural seti. Kurallar jar'da değil, agent.rules-dir klasöründedir (sunucuda /opt/sentinel/rules).
 * Klasör dakikada bir kontrol edilir; içerik değiştiyse yeni set doğrulanır ve referans tek seferde değişir.
 * Yeni set geçersizse eski set çalışmaya devam eder (yarım kural seti hiç çalışmaz).
 */
@Service
public class RuleService {

    private static final RuleSet EMPTY = RuleLoader.fromFiles(Map.of());

    private final AtomicReference<RuleSet> current = new AtomicReference<>(EMPTY);
    private final Path rulesDir;

    // Aynı hatayı her dakika yeniden yazmamak için
    private String lastError;

    public RuleService(@Value("${agent.rules-dir}") String rulesDir) {
        this.rulesDir = Path.of(rulesDir).toAbsolutePath().normalize();
    }

    public RuleSet rules() {
        return current.get();
    }

    @PostConstruct
    public void load() {
        reload();
        if (current.get() == EMPTY) {
            ColorLogger.error("KURAL", "Kural yok: tespit yapılmayacak (engelleme kararları yine uygulanır). "
                    + "Kuralları " + rulesDir + " klasörüne koyun; dakikada bir kontrol edilir.");
        }
    }

    @Scheduled(fixedDelayString = "${agent.rules-reload-ms:60000}", initialDelayString = "${agent.rules-reload-ms:60000}")
    public void reload() {
        RuleSet loaded;
        try {
            loaded = RuleLoader.fromDirectory(rulesDir);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (!message.equals(lastError)) {
                ColorLogger.error("KURAL", "Kurallar yüklenemedi, " + (current.get() == EMPTY ? "kural yok" : "eski set çalışıyor")
                        + ": " + message);
                lastError = message;
            }
            return;
        }
        boolean recovered = lastError != null;
        lastError = null;
        RuleSet previous = current.get();
        if (loaded.version().equals(previous.version())) {
            if (recovered) {
                ColorLogger.success("KURAL", "Kural hatası giderildi, çalışan set değişmedi (sürüm " + loaded.version().substring(0, 8) + ")");
            }
            return;
        }
        current.set(loaded);
        ColorLogger.success("KURAL", (previous == EMPTY ? "Kurallar yüklendi" : "Kurallar güncellendi") + ": "
                + loaded.parsers().size() + " parser, " + loaded.detectors().size() + " detector (sürüm "
                + loaded.version().substring(0, 8) + ") ← " + rulesDir);
    }
}
