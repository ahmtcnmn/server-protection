package com.ahmtcnmn.agent.client;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ahmtcnmn.agent.Dto.responder.DtoEventRequest;
import com.ahmtcnmn.agent.Libs.ColorLogger;
import com.ahmtcnmn.agent.service.MyRestClient;
import com.ahmtcnmn.agent.service.QueueService;
import com.ahmtcnmn.engine.Detection;
import com.ahmtcnmn.engine.ParsedLine;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HeartbeatTask {

    // Event'e eklenen alan değerleri ve yakalanan metin bu uzunlukta kesilir
    private static final int MAX_FIELD = 2048;
    private static final int MAX_MATCHED = 200;

    private final MyRestClient myRestClient;
    private final RestClient restClient;
    private final QueueService queueService;
    private final ManagerConnection managerConnection;

    @Value("${manager.api-key}")
    private String apiKey;

    @Value("${manager.post-url}")
    private String managerPostUrl;

    @Value("${manager.event-url-sql}")
    private String managerEventSqlUrl;

    
    @Scheduled(fixedRate = 30000)          // her 30 saniyede bir
    public void sendHeartbeat() {
        try {
            restClient.post()
                .uri(managerPostUrl)
                .header("X-API-KEY", apiKey)
                .retrieve()
                .toBodilessEntity();
            managerConnection.up();
            ColorLogger.info("HEARTBEAT", "Gönderildi (her 30 sn)");
            queueService.processPendingEvents();
        } catch (Exception e) {
            // Manager erişilemez — logla, çökme (Faz 9'da offline kuyruk)
            if (managerConnection.isDown()) {
                ColorLogger.warn("HEARTBEAT", "Gönderilemedi, 30 sn sonra tekrar denenecek");
            }
            managerConnection.down(e);
        }
    }

    public boolean sendDetectedEvent(ParsedLine line, Detection detection, Instant observedAt) {
        String sourceIp = line.field("sourceIp");
        DtoEventRequest event = DtoEventRequest.builder()
            .eventType(detection.eventType())
            .severity(detection.severity())
            .rawData(line.raw())
            .sourceIp(sourceIp)
            .rule(detection.rule())
            .matchField(detection.field())
            .matched(cut(detection.matched(), MAX_MATCHED))
            .parser(line.parser())
            .fields(eventFields(line))
            .observedAt(observedAt)
            .queued(false)
            .build();
        try {
            myRestClient.postRestClient(managerEventSqlUrl, event);
            managerConnection.up();
            ColorLogger.success("MANAGER", "Event iletildi: " + detection.eventType() + " (ip " + sourceIp + ")");
            return true;
        } catch (Exception e) {
            managerConnection.down(e);
            // Geç teslim edilecek: Manager bunu eşiğe saymaz (eski saldırı için şimdi ban vermesin)
            queueService.savePendingRequest(managerEventSqlUrl, event.toBuilder().queued(true).build());
            return false;
        }
    }

    /** Parser alanları; ham satırın aynısı olan alan (raw-line'daki message) tekrar gönderilmez. */
    private static Map<String, String> eventFields(ParsedLine line) {
        Map<String, String> fields = new LinkedHashMap<>();
        line.fields().forEach((k, v) -> {
            if (!v.equals(line.raw())) fields.put(k, cut(v, MAX_FIELD));
        });
        return fields;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
