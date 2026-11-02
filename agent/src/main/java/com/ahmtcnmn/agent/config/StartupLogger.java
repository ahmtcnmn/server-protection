package com.ahmtcnmn.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ahmtcnmn.agent.Libs.ColorLogger;

import jakarta.annotation.PostConstruct;

/** Debug ayarını ColorLogger'a aktarır ve açılışta tek satırlık özet yazar. */
@Component
public class StartupLogger {

    @Value("${agent.log.debug:false}")
    private boolean debug;

    @Value("${server.port:8080}")
    private int port;

    @Value("${manager.base-url}")
    private String managerBaseUrl;

    @PostConstruct
    void applyLogSettings() {
        ColorLogger.setDebugEnabled(debug);
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        ColorLogger.success("BAŞLAT", "Agent hazır | port " + port + " | Manager " + managerBaseUrl
                + (debug ? " | debug açık" : ""));
    }
}
