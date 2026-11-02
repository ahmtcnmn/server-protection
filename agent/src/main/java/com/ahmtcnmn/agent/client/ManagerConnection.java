package com.ahmtcnmn.agent.client;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;

import javax.net.ssl.SSLException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import com.ahmtcnmn.agent.Libs.ColorLogger;

/**
 * Manager bağlantı durumunu tutar ve yalnız durum DEĞİŞTİĞİNDE log yazar.
 * Manager kapalıyken her 10 sn'de aynı hatanın tekrar tekrar basılmasını önler.
 */
@Component
public class ManagerConnection {

    private enum State { UNKNOWN, UP, DOWN }

    @Value("${manager.base-url}")
    private String managerBaseUrl;

    private State state = State.UNKNOWN;
    private String lastReason;

    public synchronized void up() {
        if (state == State.DOWN) {
            ColorLogger.success("MANAGER", "Bağlantı geri geldi: " + managerBaseUrl);
        } else if (state == State.UNKNOWN) {
            ColorLogger.success("MANAGER", "Bağlantı kuruldu: " + managerBaseUrl);
        }
        state = State.UP;
        lastReason = null;
    }

    public synchronized void down(Exception e) {
        String reason = describe(e);
        if (state != State.DOWN || !reason.equals(lastReason)) {
            ColorLogger.error("MANAGER", "Ulaşılamıyor: " + reason + " — olaylar kuyruğa alınacak");
        }
        state = State.DOWN;
        lastReason = reason;
    }

    public synchronized boolean isDown() {
        return state == State.DOWN;
    }

    /** RestClient hatasını kısa ve anlaşılır bir sebebe çevirir. */
    public static String describe(Exception e) {
        if (e instanceof RestClientResponseException r) {
            int code = r.getStatusCode().value();
            return switch (code) {
                case 401 -> "API key reddedildi (401)";
                case 403 -> "yetki yok (403)";
                case 404 -> "endpoint bulunamadı (404)";
                case 429 -> "rate limit aşıldı (429)";
                default -> code >= 500 ? "Manager iç hatası (" + code + ")" : "istek reddedildi (" + code + ")";
            };
        }
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ConnectException) return "bağlantı reddedildi (Manager kapalı mı?)";
            if (cause instanceof HttpTimeoutException) return "zaman aşımı";
            if (cause instanceof UnknownHostException) return "adres çözümlenemedi";
            if (cause instanceof SSLException) return "SSL/TLS hatası";
            cause = cause.getCause();
        }
        for (cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) return "bağlantı koptu (" + ColorLogger.cut(cause.getMessage(), 50) + ")";
        }
        return e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + ColorLogger.cut(e.getMessage(), 80) : "");
    }
}
