package com.ahmtcnmn.agent.client;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ahmtcnmn.agent.Dto.responder.DtoEventRequest;
import com.ahmtcnmn.agent.Libs.ColorLogger;
import com.ahmtcnmn.agent.service.ExtractSourceIp;
import com.ahmtcnmn.agent.service.MyRestClient;
import com.ahmtcnmn.agent.service.QueueService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HeartbeatTask {

    private final MyRestClient myRestClient;
    private final RestClient restClient;
    private final QueueService queueService;
    private final ExtractSourceIp extractSourceIp;
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

    public boolean sendDetectedEvent(String rawData, String eventType) {
        String sourceIp = extractSourceIp.extractIp(rawData);
        DtoEventRequest event = DtoEventRequest.builder()
            .eventType(eventType)
            .severity(5)
            .rawData(rawData)
            .sourceIp(sourceIp)
            .build();
        try {
            myRestClient.postRestClient(managerEventSqlUrl, event);
            managerConnection.up();
            ColorLogger.success("MANAGER", "Event iletildi: " + eventType + " (ip " + sourceIp + ")");
            return true;
        } catch (Exception e) {
            managerConnection.down(e);
            queueService.savePendingRequest(managerEventSqlUrl, event);
            return false;
        }
    }
}
