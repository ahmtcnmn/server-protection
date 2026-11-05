package com.ahmtcnmn.agent.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;
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
 * Kaynak Manager'dır: heartbeat'te sürüm farkı görülünce yeni set çekilir (applyFromManager), doğrulanır,
 * klasöre yazılır ve referans tek seferde değişir. Klasör ayrıca dakikada bir okunur: Manager'a ulaşılamazken
 * elle yapılan değişiklik de devreye girer (Manager bağlanınca yine onun seti geçerli olur).
 * Yeni set geçersizse eski set çalışmaya devam eder (yarım kural seti hiç çalışmaz).
 */
@Service
public class RuleService {

    private static final RuleSet EMPTY = RuleLoader.fromFiles(Map.of());

    private final AtomicReference<RuleSet> current = new AtomicReference<>(EMPTY);
    private final Path rulesDir;

    // Aynı hatayı her dakika yeniden yazmamak için
    private String lastError;

    // Manager'ın seti diske yazılamadıysa klasör eski haliyle kalır: o durumda klasörden yeniden yüklenmez
    // (yoksa her dakika eski sete dönüp her heartbeat'te yeniden indirirdi)
    private boolean diskOutOfDate;

    public RuleService(@Value("${agent.rules-dir}") String rulesDir) {
        this.rulesDir = Path.of(rulesDir).toAbsolutePath().normalize();
    }

    public RuleSet rules() {
        return current.get();
    }

    /** Çalışan sürüm; hiç kural yüklenmediyse null. */
    public String version() {
        RuleSet r = current.get();
        return r == EMPTY ? null : r.version();
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
    public synchronized void reload() {
        if (diskOutOfDate) {
            return;
        }
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

    /**
     * Manager'dan gelen seti uygular. Önce tamamı doğrulanır; geçersizse hiçbir şey değişmez ve hata mesajı döner.
     * Geçerliyse önce bellekteki set değişir, sonra klasör güncellenir (yeniden başlatınca da bu set yüklensin).
     */
    public synchronized String applyFromManager(Map<String, String> files) {
        for (String path : files.keySet()) {
            if (!RuleLoader.isRuleFilePath(path)) {
                return "geçersiz dosya yolu: " + path;
            }
        }
        RuleSet loaded;
        try {
            loaded = RuleLoader.fromFiles(files);
        } catch (RuntimeException e) {
            return e.getMessage();
        }
        RuleSet previous = current.get();
        current.set(loaded);
        lastError = null;
        ColorLogger.success("KURAL", "Kurallar Manager'dan güncellendi: " + loaded.parsers().size() + " parser, "
                + loaded.detectors().size() + " detector (sürüm " + (previous == EMPTY ? "yok" : previous.version().substring(0, 8))
                + " → " + loaded.version().substring(0, 8) + ")");
        try {
            writeToDisk(files);
            diskOutOfDate = false;
        } catch (IOException | RuntimeException e) {
            diskOutOfDate = true;
            ColorLogger.warn("KURAL", "Kurallar " + rulesDir + " klasörüne yazılamadı (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "); bellekte çalışıyor, Agent yeniden başlarsa Manager'dan tekrar alınır");
        }
        return null;
    }

    /**
     * parsers/ ve detectors/ altındaki .yaml dosyalarını Manager'ın setiyle eşitler: her dosya önce geçici
     * dosyaya yazılıp yerine taşınır, sette olmayan .yaml dosyaları silinir. Diğer klasörlere (ör. tests/) dokunulmaz.
     */
    private void writeToDisk(Map<String, String> files) throws IOException {
        for (String sub : new String[] {RuleLoader.PARSERS_DIR, RuleLoader.DETECTORS_DIR}) {
            Path dir = rulesDir.resolve(sub);
            Files.createDirectories(dir);
            try (Stream<Path> existing = Files.list(dir)) {
                for (Path p : existing.toList()) {
                    String rel = sub + p.getFileName();
                    if (RuleLoader.isRuleFilePath(rel) && !files.containsKey(rel)) {
                        Files.delete(p);
                    }
                }
            }
        }
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path target = rulesDir.resolve(e.getKey()).normalize();
            if (!target.startsWith(rulesDir)) {
                throw new IOException("klasör dışına yazma engellendi: " + e.getKey());
            }
            if (Files.exists(target) && Files.readString(target, StandardCharsets.UTF_8).equals(e.getValue())) {
                continue;
            }
            Path tmp = target.resolveSibling("." + target.getFileName() + ".tmp");
            Files.writeString(tmp, e.getValue(), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
    }
}
