package com.ahmtcnmn.agent.service.IpService;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ahmtcnmn.agent.Libs.ColorLogger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class IpBlockManager {
    private final Map<String, Instant> blockedIps= new ConcurrentHashMap<>();

    private final UfwFirewall ufwFirewall;

    // Engelin süresi; application.properties → agent.block-duration (örn. 1h, 2m, 30s)
    @Value("${agent.block-duration:1h}")
    private Duration blockDuration;

    public void recordBlock(String ip){
        blockedIps.put(ip, Instant.now());
    }

    public boolean isBlocked(String ip) {
        return blockedIps.containsKey(ip);
    }

    public String blockDurationText() {
        long seconds = blockDuration.toSeconds();
        if (seconds % 3600 == 0) return (seconds / 3600) + " saat";
        if (seconds % 60 == 0) return (seconds / 60) + " dk";
        return seconds + " sn";
    }

    // Süresi dolan engeller bu aralıkla kontrol edilir (ms); engel en geç blockDuration + bu süre kadar sonra kalkar
    @Scheduled(fixedRateString = "${agent.unblock-check-ms:5000}")
    public void unblockExpired(){
        // Engel süresi kadar öncesini temsil eden sınır zamanı
        Instant threshold = Instant.now().minus(blockDuration);

        // 📝 1. Adım: Güvenli silme işlemi için Iterator (Gezici) oluşturuyoruz
        Iterator<Map.Entry<String, Instant>> iterator = blockedIps.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            String ip = entry.getKey();
            Instant blockedAt = entry.getValue();

            // Eğer engellenme zamanı, süre sınırından daha eskiyse (threshold'dan önceyse)
            if (blockedAt.isBefore(threshold)) {
                
                // 📝 2. Adım: İşletim sisteminde komutu çalıştır (sudo -n ufw delete deny from ip)
                try {
                    UfwFirewall.Result result = ufwFirewall.unblock(ip);

                    // 📝 3. Adım: Başarılıysa map'ten sil
                    if (result.ok()) {
                        iterator.remove(); // Sadece o anki IP'yi Map'ten siler, döngü çökmeksizin devam eder
                        ColorLogger.success("ENGEL", ip + " engeli kaldırıldı (" + blockDurationText() + " doldu)");
                    } else {
                        ColorLogger.error("ENGEL", ip + " engeli kaldırılamadı (exit " + result.exitCode() + "): "
                                + ColorLogger.cut(result.output(), 80));
                    }
                    
                } catch (Exception e) {
                    ColorLogger.error("ENGEL", ip + " için ufw çalıştırılamadı: " + e.getMessage());
                }
            }
        }
    }
}
