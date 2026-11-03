package com.ahmtcnmn.manager.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ahmtcnmn.manager.Libs.ColorLogger;
import com.ahmtcnmn.manager.Libs.IpValues;
import com.ahmtcnmn.manager.common.exception.MessageType;
import com.ahmtcnmn.manager.common.exceptionController.DuplicateResourceException;
import com.ahmtcnmn.manager.common.exceptionController.ResourceNotFoundException;
import com.ahmtcnmn.manager.common.exceptionController.ValidationException;
import com.ahmtcnmn.manager.dto.Allowlist.DtoAllowlistCreated;
import com.ahmtcnmn.manager.dto.Allowlist.DtoAllowlistRequest;
import com.ahmtcnmn.manager.dto.Allowlist.DtoAllowlistResponse;
import com.ahmtcnmn.manager.model.AllowlistEntry;
import com.ahmtcnmn.manager.repository.AllowlistRepository;

import lombok.RequiredArgsConstructor;

/** Panelden allowlist yönetimi. Yeni giriş eklenince onunla çakışan aktif banlar hemen kaldırılır. */
@Service
@RequiredArgsConstructor
public class AllowlistService {

    private final AllowlistRepository allowlistRepository;
    private final AllowlistCache allowlistCache;
    private final DecisionService decisionService;

    /** Önce config'teki (silinemeyen) girişler, sonra panelden eklenenler (yeniden eskiye). */
    @Transactional(readOnly = true)
    public List<DtoAllowlistResponse> list() {
        List<DtoAllowlistResponse> result = new ArrayList<>();
        allowlistCache.builtInValues().forEach(v -> result.add(DtoAllowlistResponse.builtIn(v)));
        Instant now = Instant.now();
        allowlistRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(e -> e.getExpiresAt() == null || e.getExpiresAt().isAfter(now))
                .map(DtoAllowlistResponse::from)
                .forEach(result::add);
        return result;
    }

    @Transactional
    public DtoAllowlistCreated add(DtoAllowlistRequest request, String username) {
        String value;
        try {
            value = IpValues.validate(request.value());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(MessageType.INVALID_ALLOWLIST_ENTRY, e.getMessage());
        }
        if (allowlistRepository.existsByValue(value) || allowlistCache.builtInValues().contains(value)) {
            throw new DuplicateResourceException(MessageType.ALLOWLIST_ENTRY_EXISTS, value + " zaten allowlist'te");
        }

        AllowlistEntry entry = new AllowlistEntry();
        entry.setValue(value);
        entry.setReason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());
        entry.setCreatedBy(username);
        entry.setCreatedAt(Instant.now());
        if (request.durationMinutes() != null) {
            entry.setExpiresAt(entry.getCreatedAt().plus(Duration.ofMinutes(request.durationMinutes())));
        }
        allowlistRepository.save(entry);
        allowlistCache.refresh();

        int revoked = decisionService.revokeOverlapping(value, username + " (allowlist)");
        ColorLogger.info("Allowlist'e eklendi: " + value + " (" + username + ")"
                + (revoked > 0 ? ", " + revoked + " aktif ban kaldırıldı" : ""));
        return new DtoAllowlistCreated(DtoAllowlistResponse.from(entry), revoked);
    }

    @Transactional
    public void remove(Long id, String username) {
        AllowlistEntry entry = allowlistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(MessageType.ALLOWLIST_ENTRY_NOT_FOUND, "Allowlist ID: " + id));
        allowlistRepository.delete(entry);
        allowlistCache.refresh();
        ColorLogger.info("Allowlist'ten çıkarıldı: " + entry.getValue() + " (" + username + ")");
    }
}
