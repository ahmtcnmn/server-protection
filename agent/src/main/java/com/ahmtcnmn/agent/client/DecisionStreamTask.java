package com.ahmtcnmn.agent.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ahmtcnmn.agent.Dto.Decision.DtoAgentDecision;
import com.ahmtcnmn.agent.Dto.Decision.DtoDecisionStream;
import com.ahmtcnmn.agent.Libs.ColorLogger;
import com.ahmtcnmn.agent.model.AgentState;
import com.ahmtcnmn.agent.repository.AgentStateRepository;
import com.ahmtcnmn.agent.service.IpService.DecisionEnforcer;

import lombok.RequiredArgsConstructor;

/**
 * Manager'daki ban kararlarını çeker (decision stream, CrowdSec modeli).
 * - Açılışta: önce H2'deki kayıtla ufw eşitlenir (Manager kapalı olsa bile engeller yerinde kalır),
 *   sonra Manager'dan tam liste (startup=true) alınır.
 * - Sonra her 10 sn: yalnız son görülen değişiklikten (cursor) sonrakiler alınır.
 */
@Component
@RequiredArgsConstructor
public class DecisionStreamTask {

    private static final String STREAM_URI = "/api/agent/decisions/stream";

    private final RestClient restClient;
    private final DecisionEnforcer enforcer;
    private final AgentStateRepository stateRepository;
    private final ManagerConnection managerConnection;

    @Value("${manager.api-key}")
    private String apiKey;

    // ufw başarısız olursa tam senkron bu kadar sonra yeniden denenir (her 10 sn hata basılmasın)
    private static final long RESYNC_DELAY_MS = 60_000;

    // Manager'la tam senkron yapıldı mı? (her açılışta bir kez gerekir)
    private volatile boolean synced = false;
    private volatile long nextFullSyncAt = 0;

    @EventListener(ApplicationReadyEvent.class)
    public void restoreOnStartup() {
        enforcer.reconcileUfw();
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 3000)
    public void poll() {
        try {
            if (!synced) {
                if (System.currentTimeMillis() < nextFullSyncAt) return;
                fullSync();
            } else {
                incremental();
            }
            managerConnection.up();
        } catch (Exception e) {
            managerConnection.down(e);
        }
        enforcer.expireLocally(managerConnection);
    }

    private void fullSync() {
        DtoDecisionStream s = fetch("?startup=true");
        enforcer.replaceAll(s.added());
        saveCursor(s.lastChangeId());
        synced = true;
        ColorLogger.info("KARAR", "Manager ile senkron: " + s.added().size() + " aktif karar");
    }

    private void incremental() {
        long cursor = readCursor();
        DtoDecisionStream s = fetch("?after=" + cursor);
        for (DtoAgentDecision d : s.added()) {
            ColorLogger.info("KARAR", "#" + d.id() + " yeni ban: " + d.value());
            if (!enforcer.apply(d)) {
                scheduleResync();
            }
        }
        for (DtoAgentDecision d : s.deleted()) {
            ColorLogger.info("KARAR", "#" + d.id() + " kaldırıldı: " + d.value());
            enforcer.remove(d, "karar kaldırıldı");
        }
        if (s.lastChangeId() != cursor) {
            saveCursor(s.lastChangeId());
        }
    }

    private void scheduleResync() {
        synced = false;
        nextFullSyncAt = System.currentTimeMillis() + RESYNC_DELAY_MS;
        ColorLogger.warn("KARAR", "Karar uygulanamadı, 1 dk sonra Manager ile tam senkron yeniden denenecek");
    }

    private DtoDecisionStream fetch(String query) {
        DtoDecisionStream s = restClient.get()
                .uri(STREAM_URI + query)
                .header("X-API-KEY", apiKey)
                .retrieve()
                .body(DtoDecisionStream.class);
        if (s == null) {
            throw new IllegalStateException("Decision stream boş yanıt döndü");
        }
        return s;
    }

    private long readCursor() {
        return stateRepository.findById(AgentState.DECISION_CURSOR)
                .map(st -> Long.parseLong(st.getValue()))
                .orElse(0L);
    }

    private void saveCursor(long id) {
        stateRepository.save(new AgentState(AgentState.DECISION_CURSOR, String.valueOf(id)));
    }
}
