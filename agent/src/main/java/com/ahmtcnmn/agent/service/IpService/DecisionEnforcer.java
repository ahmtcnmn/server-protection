package com.ahmtcnmn.agent.service.IpService;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ahmtcnmn.agent.Dto.Decision.DtoAgentDecision;
import com.ahmtcnmn.agent.Dto.responder.DtoEventRequest;
import com.ahmtcnmn.agent.Libs.ColorLogger;
import com.ahmtcnmn.agent.client.ManagerConnection;
import com.ahmtcnmn.agent.model.AppliedDecision;
import com.ahmtcnmn.agent.repository.AppliedDecisionRepository;
import com.ahmtcnmn.agent.service.MyRestClient;

import lombok.RequiredArgsConstructor;

/**
 * Manager'dan gelen ban kararlarını ufw'ye uygular ve H2'de (applied_decisions) kaydını tutar.
 * Aynı IP için birden fazla karar olabilir: ufw kuralı ilk kararla eklenir, son karar kalkınca silinir.
 */
@Service
@RequiredArgsConstructor
public class DecisionEnforcer {

    // Manager'a ulaşılamazsa süresi dolan kararı Agent kendisi kaldırır; Manager'ın DELETED'ına bu kadar pay verilir
    private static final Duration LOCAL_EXPIRY_GRACE = Duration.ofMinutes(2);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final UfwFirewall ufwFirewall;
    private final AppliedDecisionRepository appliedRepository;
    private final MyRestClient myRestClient;

    @Value("${manager.event-url-sql}")
    private String managerEventUrl;

    // Son emniyet: Manager hatayla bunları banlamak istese bile Agent uygulamaz
    @Value("${agent.never-block:127.0.0.1,::1}")
    private List<String> neverBlock;

    /** Yeni ya da güncellenen (süresi uzatılan) karar. ufw başarısız olursa false döner. */
    @Transactional
    public boolean apply(DtoAgentDecision d) {
        if (isNeverBlock(d.value())) {
            ColorLogger.warn("ENGEL", d.value() + " asla engellenmeyecekler listesinde, karar #" + d.id() + " uygulanmadı");
            return true;
        }
        AppliedDecision existing = appliedRepository.findById(d.id()).orElse(null);
        if (existing != null) {
            existing.setExpiresAt(d.expiresAt());
            ColorLogger.info("ENGEL", d.value() + " engel süresi uzatıldı → " + TIME.format(d.expiresAt()));
            return true;
        }
        boolean alreadyBlocked = appliedRepository.existsByValue(d.value());
        if (!alreadyBlocked && !block(d.value())) {
            return false; // ufw başarısız: kaydetme, çağıran tam senkronla yeniden dener
        }
        AppliedDecision a = new AppliedDecision();
        a.setDecisionId(d.id());
        a.setValue(d.value());
        a.setExpiresAt(d.expiresAt());
        a.setAppliedAt(Instant.now());
        appliedRepository.save(a);
        if (!alreadyBlocked) {
            ColorLogger.success("ENGEL", d.value() + " ufw ile engellendi (karar #" + d.id() + ", bitiş "
                    + TIME.format(d.expiresAt()) + ")");
            killOpenConnections(d.value());
            notifyBlocked(d);
        }
        return true;
    }

    /** Kaldırılan ya da süresi dolan karar. */
    @Transactional
    public void remove(DtoAgentDecision d, String why) {
        AppliedDecision a = appliedRepository.findById(d.id()).orElse(null);
        if (a == null) {
            return; // bu Agent'ta hiç uygulanmamış (ya da zaten yerelde kaldırılmış)
        }
        appliedRepository.delete(a);
        appliedRepository.flush();
        if (appliedRepository.existsByValue(a.getValue())) {
            return; // aynı IP için başka aktif karar var, ufw kuralı kalmalı
        }
        if (unblock(a.getValue())) {
            ColorLogger.success("ENGEL", a.getValue() + " engeli kaldırıldı (" + why + ")");
        }
    }

    /**
     * Manager'ın aktif karar listesini (startup senkronu) tek doğru kabul eder:
     * H2 kaydını bu listeyle değiştirir, ufw'deki sentinel kurallarını eşitler.
     */
    @Transactional
    public void replaceAll(Collection<DtoAgentDecision> active) {
        appliedRepository.deleteAllInBatch();
        Instant now = Instant.now();
        for (DtoAgentDecision d : active) {
            if (isNeverBlock(d.value())) continue;
            AppliedDecision a = new AppliedDecision();
            a.setDecisionId(d.id());
            a.setValue(d.value());
            a.setExpiresAt(d.expiresAt());
            a.setAppliedAt(now);
            appliedRepository.save(a);
        }
        reconcileUfw();
    }

    /** ufw'deki sentinel kurallarını H2'deki kararlarla eşitler. Yalnız "sentinel:" yorumlu kurallara dokunur. */
    @Transactional(readOnly = true)
    public void reconcileUfw() {
        Set<String> desired = appliedRepository.findAll().stream()
                .map(AppliedDecision::getValue).collect(Collectors.toCollection(HashSet::new));
        Map<String, Integer> present;
        try {
            present = ufwFirewall.sentinelValues();
        } catch (Exception e) {
            ColorLogger.error("ENGEL", "ufw kuralları okunamadı, eşitleme atlandı: " + ColorLogger.cut(e.getMessage(), 80));
            return;
        }
        int added = 0, removed = 0;
        for (String value : present.keySet()) {
            if (!desired.contains(value) && unblock(value)) removed++;
        }
        for (String value : desired) {
            if (!present.containsKey(value) && block(value)) added++;
        }
        ColorLogger.info("ENGEL", "ufw eşitlendi: " + desired.size() + " aktif engel"
                + (added + removed > 0 ? " (+" + added + " eklendi, -" + removed + " kaldırıldı)" : ""));
    }

    /** Manager'dan DELETED gelmediyse (ulaşılamıyorsa) süresi çoktan dolmuş kararları yerelde kaldırır. */
    @Transactional
    public void expireLocally(ManagerConnection connection) {
        Instant limit = Instant.now().minus(LOCAL_EXPIRY_GRACE);
        for (AppliedDecision a : appliedRepository.findByExpiresAtBefore(limit)) {
            remove(new DtoAgentDecision(a.getDecisionId(), a.getValue(), null, a.getExpiresAt()),
                    connection.isDown() ? "süre doldu, Manager'a ulaşılamadığı için yerelde" : "süre doldu");
        }
    }

    // ---------------------------------------------------------------- ufw

    private boolean block(String value) {
        try {
            UfwFirewall.Result r = ufwFirewall.block(value);
            if (r.ok()) return true;
            ColorLogger.error("ENGEL", value + " engellenemedi: ufw exit " + r.exitCode() + ": " + ColorLogger.cut(r.output(), 80));
        } catch (Exception e) {
            ColorLogger.error("ENGEL", value + " için ufw çalıştırılamadı: " + ColorLogger.cut(e.getMessage(), 80));
        }
        return false;
    }

    private boolean unblock(String value) {
        try {
            UfwFirewall.Result r = ufwFirewall.unblock(value);
            if (r.ok()) return true;
            ColorLogger.error("ENGEL", value + " engeli kaldırılamadı: ufw exit " + r.exitCode() + ": " + ColorLogger.cut(r.output(), 80));
        } catch (Exception e) {
            ColorLogger.error("ENGEL", value + " için ufw çalıştırılamadı: " + ColorLogger.cut(e.getMessage(), 80));
        }
        return false;
    }

    // Engel konduktan sonra açık kalan bağlantıları keser; başarısız olsa da engel geçerli kalır
    private void killOpenConnections(String value) {
        try {
            UfwFirewall.Result kill = ufwFirewall.killConnections(value);
            if (kill.ok()) {
                // ss -K çıktısı: başlık satırı + kesilen her bağlantı için bir satır
                long count = kill.output().lines().skip(1).filter(l -> !l.isBlank()).count();
                if (count > 0) ColorLogger.success("ENGEL", value + " için açık bağlantı kesildi: " + count);
            } else {
                ColorLogger.warn("ENGEL", "Açık bağlantılar kesilemedi (exit " + kill.exitCode() + "): "
                        + ColorLogger.cut(kill.output(), 60));
            }
        } catch (Exception e) {
            ColorLogger.warn("ENGEL", "Açık bağlantılar kesilemedi: " + ColorLogger.cut(e.getMessage(), 60));
        }
    }

    private void notifyBlocked(DtoAgentDecision d) {
        try {
            myRestClient.postRestClient(managerEventUrl, DtoEventRequest.builder()
                .eventType("IP_BLOCKED")
                .severity(3)
                .rawData("ufw ile engellendi: " + d.value() + " (karar #" + d.id() + ")")
                .sourceIp(d.value())
                .observedAt(java.time.Instant.now())
                .queued(false)
                .build());
        } catch (Exception e) {
            ColorLogger.debug("ENGEL", "IP_BLOCKED bildirimi gönderilemedi: " + ManagerConnection.describe(e));
        }
    }

    private boolean isNeverBlock(String value) {
        return neverBlock.stream().map(String::trim).anyMatch(value::equals);
    }
}
