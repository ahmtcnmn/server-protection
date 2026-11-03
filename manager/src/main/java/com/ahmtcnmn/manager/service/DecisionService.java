package com.ahmtcnmn.manager.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ahmtcnmn.manager.Libs.ColorLogger;
import com.ahmtcnmn.manager.Libs.IpValues;
import com.ahmtcnmn.manager.common.exception.MessageType;
import com.ahmtcnmn.manager.common.exceptionController.ResourceNotFoundException;
import com.ahmtcnmn.manager.common.exceptionController.ValidationException;
import com.ahmtcnmn.manager.dto.Decision.DtoAgentDecision;
import com.ahmtcnmn.manager.dto.Decision.DtoDecisionRequest;
import com.ahmtcnmn.manager.dto.Decision.DtoDecisionResponse;
import com.ahmtcnmn.manager.dto.Decision.DtoDecisionStream;
import com.ahmtcnmn.manager.model.Agent;
import com.ahmtcnmn.manager.model.Alert;
import com.ahmtcnmn.manager.model.Decision;
import com.ahmtcnmn.manager.model.DecisionChange;
import com.ahmtcnmn.manager.model.DecisionChangeAction;
import com.ahmtcnmn.manager.model.DecisionOrigin;
import com.ahmtcnmn.manager.model.DecisionScope;
import com.ahmtcnmn.manager.model.DecisionType;
import com.ahmtcnmn.manager.repository.AgentRepository;
import com.ahmtcnmn.manager.repository.AlertRepository;
import com.ahmtcnmn.manager.repository.EventsRepository;
import com.ahmtcnmn.manager.repository.DecisionChangeRepository;
import com.ahmtcnmn.manager.repository.DecisionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Ban kararlarının tek sahibi. Kararı oluşturur/uzatır/kaldırır ve her değişikliği decision_changes
 * günlüğüne yazar; Agent'lar bu günlüğü decision stream ile okuyup ufw'ye uygular.
 */
@Service
@RequiredArgsConstructor
public class DecisionService {

    // Tek istekte Agent'a gönderilecek en fazla değişiklik; fazlası bir sonraki poll'da gelir
    private static final int STREAM_BATCH = 500;

    private final DecisionRepository decisionRepository;
    private final DecisionChangeRepository decisionChangeRepository;
    private final AgentRepository agentRepository;
    private final AlertRepository alertRepository;
    private final EventsRepository eventsRepository;

    private final AllowlistCache allowlistCache;

    // Tespit kuralının (SQLi eşiği) verdiği ban süresi
    @Value("${security.ban-duration:1h}")
    private Duration banDuration;

    // ---------------------------------------------------------------- oluşturma

    /**
     * Bir tespit kuralı tetiklendi: alert oluşturur, ilgili event'leri ona bağlar ve ban kararı verir.
     * Aynı IP zaten banlıysa yeni alert açılmaz (aynı saldırı için alert yağmuru olmasın).
     */
    @Transactional
    public Optional<Decision> raiseScenario(String scenario, Agent agent, String ip, String eventType, Instant since, long count) {
        if (findActiveGlobal(ip).isPresent()) {
            return Optional.empty();
        }
        Alert alert = new Alert();
        alert.setScenario(scenario);
        alert.setSourceIp(ip);
        alert.setAgent(agent);
        Instant first = eventsRepository.findFirstTimestamp(ip, eventType, since);
        alert.setStartedAt(first != null ? first : since);
        alert.setStoppedAt(Instant.now());
        alert.setEventsCount((int) count);
        alert.setSimulated(false);
        alertRepository.save(alert);
        eventsRepository.linkToAlert(alert, ip, eventType, since);
        return banFromScenario(alert);
    }

    /** Alert için ban kararı. Aynı IP zaten banlıysa süre uzatılır. */
    private Optional<Decision> banFromScenario(Alert alert) {
        String value = alert.getSourceIp();
        if (allowlistCache.overlaps(value)) {
            ColorLogger.warn("IP " + value + " allowlist'te, ban kararı oluşturulmadı");
            return Optional.empty();
        }
        Instant expiresAt = Instant.now().plus(banDuration);
        Optional<Decision> existing = findActiveGlobal(value);
        if (existing.isPresent()) {
            return Optional.of(extend(existing.get(), expiresAt));
        }
        Decision d = newDecision(value, DecisionScope.GLOBAL, null, DecisionOrigin.SCENARIO, expiresAt);
        d.setScenario(alert.getScenario());
        d.setAlert(alert);
        save(d, DecisionChangeAction.NEW);
        ColorLogger.warn("Ban kararı #" + d.getId() + ": " + value + " (" + alert.getScenario() + ", "
                + alert.getEventsCount() + " olay, " + formatDuration(banDuration) + ")");
        return Optional.of(d);
    }

    /** Panelden elle ban. */
    @Transactional
    public DtoDecisionResponse banManually(DtoDecisionRequest request, String username) {
        String value;
        try {
            value = IpValues.validate(request.value());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(MessageType.INVALID_DECISION, e.getMessage());
        }
        if (allowlistCache.overlaps(value)) {
            throw new ValidationException(MessageType.INVALID_DECISION,
                    value + " allowlist'teki bir adresle çakışıyor, banlanamaz");
        }
        DecisionScope scope = DecisionScope.valueOf(request.scope());
        Agent target = null;
        if (scope == DecisionScope.AGENT) {
            if (request.agentId() == null) {
                throw new ValidationException(MessageType.INVALID_DECISION, "AGENT scope için agentId gerekli");
            }
            target = agentRepository.findById(request.agentId())
                    .orElseThrow(() -> new ResourceNotFoundException(MessageType.AGENT_NOT_FOUND, "Agent ID: " + request.agentId()));
        }
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(request.durationMinutes()));
        Decision d = newDecision(value, scope, target, DecisionOrigin.MANUAL, expiresAt);
        d.setReason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());
        d.setCreatedBy(username);
        save(d, DecisionChangeAction.NEW);
        ColorLogger.warn("Elle ban kararı #" + d.getId() + ": " + value + " (" + username + ", "
                + request.durationMinutes() + " dk)");
        return DtoDecisionResponse.from(d);
    }

    // ---------------------------------------------------------------- kaldırma

    @Transactional
    public DtoDecisionResponse revoke(Long id, String username) {
        Decision d = decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(MessageType.DECISION_NOT_FOUND, "Karar ID: " + id));
        if (d.isActive()) {
            close(d, username);
            ColorLogger.info("Ban kararı #" + d.getId() + " kaldırıldı: " + d.getValue() + " (" + username + ")");
        }
        return DtoDecisionResponse.from(d);
    }

    /** Allowlist'e yeni eklenen değerle çakışan aktif kararları kapatır. Kapatılan karar sayısını döner. */
    @Transactional
    public int revokeOverlapping(String allowValue, String by) {
        int count = 0;
        for (Decision d : decisionRepository.findByRevokedAtIsNull()) {
            if (IpValues.overlapsAny(d.getValue(), List.of(allowValue))) {
                close(d, by);
                ColorLogger.info("Ban kararı #" + d.getId() + " allowlist nedeniyle kaldırıldı: " + d.getValue());
                count++;
            }
        }
        return count;
    }

    /** Süresi dolan kararları kapatır; Agent'lar DELETED değişikliğini görüp ufw kuralını kaldırır. */
    @Scheduled(fixedRateString = "${security.decision-expiry-check-ms:5000}")
    @Transactional
    public void expireDecisions() {
        for (Decision d : decisionRepository.findByRevokedAtIsNullAndExpiresAtBefore(Instant.now())) {
            close(d, Decision.REVOKED_BY_SYSTEM);
            ColorLogger.info("Ban kararı #" + d.getId() + " süresi doldu: " + d.getValue());
        }
    }

    // ---------------------------------------------------------------- stream

    /**
     * Agent'ın uygulaması gereken değişiklikler.
     * startup = true → aktif kararların tamamı (Agent ilk açılışta ufw'yi bununla eşitler).
     */
    @Transactional(readOnly = true)
    public DtoDecisionStream stream(Agent agent, long after, boolean startup) {
        if (startup) {
            // Önce imleci al, sonra listeyi: arada gelen değişiklik bir sonraki poll'da tekrar gelir, kaybolmaz
            long last = decisionChangeRepository.findMaxId();
            List<DtoAgentDecision> active = decisionRepository.findActiveForAgent(agent).stream()
                    .map(DtoAgentDecision::from).toList();
            return new DtoDecisionStream(last, active, List.of());
        }

        List<DecisionChange> changes = decisionChangeRepository.findAfter(after, Limit.of(STREAM_BATCH));
        // Aynı karar için batch içinde birden fazla değişiklik varsa yalnız sonuncusu önemli
        Map<Long, DecisionChange> latest = new LinkedHashMap<>();
        long last = after;
        for (DecisionChange c : changes) {
            last = c.getId();
            if (appliesTo(c.getDecision(), agent)) {
                latest.remove(c.getDecision().getId());
                latest.put(c.getDecision().getId(), c);
            }
        }
        List<DtoAgentDecision> added = new ArrayList<>();
        List<DtoAgentDecision> deleted = new ArrayList<>();
        for (DecisionChange c : latest.values()) {
            DtoAgentDecision dto = DtoAgentDecision.from(c.getDecision());
            if (c.getAction() == DecisionChangeAction.NEW) added.add(dto); else deleted.add(dto);
        }
        return new DtoDecisionStream(last, added, deleted);
    }

    // ---------------------------------------------------------------- panel listesi

    @Transactional(readOnly = true)
    public Page<DtoDecisionResponse> list(String status, String value, Pageable pageable) {
        Specification<Decision> spec = (root, q, cb) -> cb.conjunction();
        if ("active".equalsIgnoreCase(status)) {
            spec = spec.and((root, q, cb) -> cb.isNull(root.get("revokedAt")));
        } else if ("inactive".equalsIgnoreCase(status)) {
            spec = spec.and((root, q, cb) -> cb.isNotNull(root.get("revokedAt")));
        }
        if (value != null && !value.isBlank()) {
            String like = "%" + value.trim() + "%";
            spec = spec.and((root, q, cb) -> cb.like(root.get("value"), like));
        }
        return decisionRepository.findAll(spec, pageable).map(DtoDecisionResponse::from);
    }

    @Transactional(readOnly = true)
    public long countActive() {
        return decisionRepository.countByRevokedAtIsNull();
    }

    // ---------------------------------------------------------------- yardımcılar

    @Transactional(readOnly = true)
    public Optional<Decision> findActiveGlobal(String value) {
        return decisionRepository.findByValueAndRevokedAtIsNull(value).stream()
                .filter(d -> d.getScope() == DecisionScope.GLOBAL && !d.isSimulated())
                .findFirst();
    }

    private Decision extend(Decision d, Instant expiresAt) {
        if (expiresAt.isAfter(d.getExpiresAt())) {
            d.setExpiresAt(expiresAt);
            // NEW tekrar yazılır: Agent kararı günceller (yeni expiresAt'ı öğrenir), ufw'ye ikinci kural eklemez
            save(d, DecisionChangeAction.NEW);
            ColorLogger.info("Ban kararı #" + d.getId() + " uzatıldı: " + d.getValue());
        }
        return d;
    }

    private void close(Decision d, String by) {
        d.setRevokedAt(Instant.now());
        d.setRevokedBy(by);
        save(d, DecisionChangeAction.DELETED);
    }

    private Decision newDecision(String value, DecisionScope scope, Agent target, DecisionOrigin origin, Instant expiresAt) {
        Decision d = new Decision();
        d.setValue(value);
        d.setScope(scope);
        d.setTargetAgent(target);
        d.setType(DecisionType.BAN);
        d.setOrigin(origin);
        d.setCreatedAt(Instant.now());
        d.setExpiresAt(expiresAt);
        d.setSimulated(false);
        return d;
    }

    private void save(Decision d, DecisionChangeAction action) {
        decisionRepository.save(d);
        decisionChangeRepository.save(new DecisionChange(d, action));
    }

    private boolean appliesTo(Decision d, Agent agent) {
        if (d.isSimulated()) return false;
        return d.getScope() == DecisionScope.GLOBAL
                || (d.getTargetAgent() != null && d.getTargetAgent().getId().equals(agent.getId()));
    }

    private static String formatDuration(Duration d) {
        long s = d.toSeconds();
        if (s % 3600 == 0) return (s / 3600) + " saat";
        if (s % 60 == 0) return (s / 60) + " dk";
        return s + " sn";
    }
}
