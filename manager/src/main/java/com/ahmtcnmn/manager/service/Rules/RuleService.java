package com.ahmtcnmn.manager.service.Rules;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ahmtcnmn.engine.RuleLoader;
import com.ahmtcnmn.engine.RuleSet;
import com.ahmtcnmn.manager.Libs.ColorLogger;

import jakarta.annotation.PostConstruct;

/**
 * Kuralların tek kaynağı. security.rules-dir klasörünü okur, engine ile doğrular ve Agent'lara dağıtılacak seti tutar.
 * Klasör dakikada bir kontrol edilir (panelden POST /api/rules/reload ile hemen de yüklenebilir).
 * Geçersiz set hiçbir Agent'a gitmez: son geçerli set dağıtılmaya devam eder, hata panelde görünür.
 */
@Service
public class RuleService {

    /** Dağıtılan set: doğrulanmış RuleSet + Agent'a olduğu gibi gönderilecek dosyalar. */
    public record Loaded(RuleSet rules, Map<String, String> files, Instant loadedAt) {}

    private final AtomicReference<Loaded> current = new AtomicReference<>();
    private final Path rulesDir;

    private volatile String lastError;
    private volatile Instant lastErrorAt;

    public RuleService(@Value("${security.rules-dir:../rules}") String rulesDir) {
        this.rulesDir = Path.of(rulesDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        reload();
        if (current.get() == null) {
            ColorLogger.warn("Kural seti yok: Agent'lar kendi kurallarıyla çalışmaya devam eder. Klasör: " + rulesDir);
        }
    }

    @Scheduled(fixedDelayString = "${security.rules-reload-ms:60000}", initialDelayString = "${security.rules-reload-ms:60000}")
    void scheduledReload() {
        reload();
    }

    /** Klasörü yeniden okur. Geçerliyse ve içerik değiştiyse yeni set dağıtılır. Hata mesajını döner (yoksa null). */
    public synchronized String reload() {
        Map<String, String> files;
        RuleSet rules;
        try {
            files = RuleLoader.readDirectory(rulesDir);
            rules = RuleLoader.fromFiles(files);
        } catch (RuntimeException e) {
            if (!e.getMessage().equals(lastError)) {
                ColorLogger.error("Kurallar yüklenemedi, " + (current.get() == null ? "dağıtılacak set yok" : "eski set dağıtılıyor")
                        + ": " + e.getMessage());
                lastErrorAt = Instant.now();
            }
            lastError = e.getMessage();
            return lastError;
        }
        lastError = null;
        lastErrorAt = null;
        Loaded previous = current.get();
        if (previous != null && previous.rules().version().equals(rules.version())) {
            return null;
        }
        current.set(new Loaded(rules, Map.copyOf(files), Instant.now()));
        ColorLogger.info((previous == null ? "Kurallar yüklendi: " : "Kurallar güncellendi, Agent'lar sıradaki heartbeat'te alacak: ")
                + rules.parsers().size() + " parser, " + rules.detectors().size() + " detector (sürüm "
                + rules.version().substring(0, 8) + ")");
        return null;
    }

    /** Dağıtılan set; hiç geçerli set yüklenmediyse null. */
    public Loaded current() {
        return current.get();
    }

    public String currentVersion() {
        Loaded l = current.get();
        return l == null ? null : l.rules().version();
    }

    public String lastError() {
        return lastError;
    }

    public Instant lastErrorAt() {
        return lastErrorAt;
    }

    public Path rulesDir() {
        return rulesDir;
    }
}
