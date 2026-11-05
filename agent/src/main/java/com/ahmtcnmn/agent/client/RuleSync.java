package com.ahmtcnmn.agent.client;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ahmtcnmn.agent.Dto.Rules.DtoRuleBundle;
import com.ahmtcnmn.agent.Libs.ColorLogger;
import com.ahmtcnmn.agent.service.RuleService;

import lombok.RequiredArgsConstructor;

/**
 * Heartbeat yanıtındaki kural sürümü çalışan sürümden farklıysa yeni seti Manager'dan çeker.
 * Bağlantıyı yine Agent başlatır (polling); Manager Agent'a hiç bağlanmaz.
 */
@Component
@RequiredArgsConstructor
public class RuleSync {

    private final RestClient restClient;
    private final RuleService ruleService;

    @Value("${manager.api-key}")
    private String apiKey;

    // Agent'ta doğrulanamayan sürüm (ör. Manager'daki kural Agent'ın motor sürümünde olmayan bir özellik kullanıyor):
    // aynı sürüm için her 30 sn yeniden indirip aynı hatayı yazmayalım; Manager'da sürüm değişince tekrar denenir
    private volatile String rejectedVersion;

    public void sync(String managerVersion) {
        if (managerVersion == null || managerVersion.equals(ruleService.version())
                || managerVersion.equals(rejectedVersion)) {
            return;
        }
        DtoRuleBundle bundle;
        try {
            bundle = restClient.get()
                    .uri("/api/agent/rules")
                    .header("X-API-KEY", apiKey)
                    .retrieve()
                    .body(DtoRuleBundle.class);
        } catch (Exception e) {
            ColorLogger.warn("KURAL", "Yeni kurallar indirilemedi, sonraki heartbeat'te tekrar denenecek: "
                    + ManagerConnection.describe(e));
            return;
        }
        if (bundle == null || bundle.files() == null) {
            return;
        }
        String error = ruleService.applyFromManager(bundle.files());
        if (error != null) {
            rejectedVersion = bundle.version();
            ColorLogger.error("KURAL", "Manager'ın kural seti reddedildi (sürüm " + cut(bundle.version())
                    + "), çalışan set korunuyor: " + error);
            return;
        }
        rejectedVersion = null;
        if (!Objects.equals(ruleService.version(), bundle.version())) {
            ColorLogger.warn("KURAL", "Kural sürümü Manager'ınkiyle eşleşmiyor (" + cut(ruleService.version())
                    + " ≠ " + cut(bundle.version()) + "); motor sürümleri farklı olabilir");
        }
    }

    private static String cut(String version) {
        return version == null ? "yok" : version.substring(0, Math.min(8, version.length()));
    }
}
