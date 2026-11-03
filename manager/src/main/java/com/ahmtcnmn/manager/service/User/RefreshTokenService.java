package com.ahmtcnmn.manager.service.User;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ahmtcnmn.manager.Libs.ColorLogger;
import com.ahmtcnmn.manager.common.exception.MessageType;
import com.ahmtcnmn.manager.common.exceptionController.UnauthorizedException;
import com.ahmtcnmn.manager.model.RefreshToken;
import com.ahmtcnmn.manager.repository.RefreshTokenRepository;
import com.ahmtcnmn.manager.service.ApiKey.GenerateApiKey;

import lombok.RequiredArgsConstructor;

/**
 * Refresh token üretir, döndürür (rotation) ve iptal eder.
 * - Her refresh'te eski token iptal edilir, yenisi verilir.
 * - İptal edilmiş bir token tekrar gelirse token çalınmış olabilir: kullanıcının tüm oturumları kapatılır.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private final RefreshTokenRepository refreshTokenRepository;
    private final GenerateApiKey generateApiKey;

    @Value("${app.jwt.refresh-token-days:14}")
    private long refreshTokenDays;

    /** Ham token ile birlikte sahibinin adı (yeni access token üretmek için). */
    public record Issued(String username, String rawToken) {}

    public Duration lifetime() {
        return Duration.ofDays(refreshTokenDays);
    }

    @Transactional
    public String issue(String username) {
        String raw = generateApiKey.generateApiKey(); // 32 byte SecureRandom, URL-safe
        RefreshToken token = new RefreshToken();
        token.setUsername(username);
        token.setTokenHash(HashApiKey.hashApiKey(raw));
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(lifetime()));
        refreshTokenRepository.save(token);
        return raw;
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Issued rotate(String raw) {
        RefreshToken current = find(raw);
        if (current.getRevokedAt() != null) {
            // Birkaç saniye önce döndürülmüşse muhtemelen iki sekme aynı anda yeniledi: yalnız reddet
            if (current.getRevokedAt().isAfter(Instant.now().minus(REUSE_GRACE))) {
                throw invalid();
            }
            // Kullanılmış token tekrar geldi → çalınmış olabilir, bütün oturumları kapat
            refreshTokenRepository.revokeAllForUser(current.getUsername(), Instant.now());
            ColorLogger.warn("Kullanılmış refresh token tekrar denendi, " + current.getUsername()
                    + " kullanıcısının tüm oturumları kapatıldı");
            throw invalid();
        }
        if (current.getExpiresAt().isBefore(Instant.now())) {
            throw invalid();
        }
        current.setRevokedAt(Instant.now());
        return new Issued(current.getUsername(), issue(current.getUsername()));
    }

    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(HashApiKey.hashApiKey(raw))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(Instant.now()));
    }

    // Süresi dolmuş kayıtlar her gece temizlenir
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpired() {
        refreshTokenRepository.deleteExpired(Instant.now());
    }

    private RefreshToken find(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }
        return refreshTokenRepository.findByTokenHash(HashApiKey.hashApiKey(raw)).orElseThrow(this::invalid);
    }

    private UnauthorizedException invalid() {
        return new UnauthorizedException(MessageType.INVALID_REFRESH_TOKEN, "Oturum süresi doldu, tekrar giriş yapın");
    }
}
