package com.ahmtcnmn.agent.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ahmtcnmn.agent.Dto.responder.DtoEventRequest;
import com.ahmtcnmn.agent.Libs.ColorLogger;
import com.ahmtcnmn.agent.client.ManagerConnection;
import com.ahmtcnmn.agent.model.PendingEvent;
import com.ahmtcnmn.agent.repository.PendingEventRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final PendingEventRepository pendingEventRepository;
    private final MyRestClient myRestClient;
    private final ObjectMapper objectMapper;

    public void savePendingRequest(String uri, Object payload) {
        try {
            PendingEvent pendingEvent = new PendingEvent();
            pendingEvent.setUri(uri);
            pendingEvent.setPayloadJson(objectMapper.writeValueAsString(payload));  // nesneyi JSON metnine çevir
            pendingEvent.setCreatedAt(Instant.now());
            pendingEventRepository.save(pendingEvent);
            ColorLogger.warn("KUYRUK", describe(uri, payload) + " kuyruğa alındı (bekleyen: "
                    + pendingEventRepository.count() + ")");
        } catch (Exception e) {
            ColorLogger.error("KUYRUK", "Kuyruğa kaydedilemedi, veri kayboldu: " + ColorLogger.cut(e.getMessage(), 80));
        }
    }

    @Transactional
    public void processPendingEvents() {
        List<PendingEvent> pendingEvents = pendingEventRepository.findAll();
        if (pendingEvents.isEmpty()) return;

        ColorLogger.info("KUYRUK", pendingEvents.size() + " bekleyen kayıt Manager'a gönderiliyor...");

        int sent = 0;
        String lastError = null;
        for (PendingEvent pendingEvent : pendingEvents) {
            try {
                myRestClient.postRawJson(pendingEvent.getUri(), pendingEvent.getPayloadJson());
                pendingEventRepository.delete(pendingEvent);
                sent++;
            } catch (Exception e) {
                lastError = ManagerConnection.describe(e);
            }
        }

        if (lastError == null) {
            ColorLogger.success("KUYRUK", sent + "/" + pendingEvents.size() + " gönderildi, kuyruk boş");
        } else {
            ColorLogger.warn("KUYRUK", sent + "/" + pendingEvents.size() + " gönderildi, "
                    + (pendingEvents.size() - sent) + " bekliyor (" + lastError + ")");
        }
    }

    
    private String describe(String uri, Object payload) {
        if (payload instanceof DtoEventRequest event) {
            return event.eventType() + " event'i (ip " + event.sourceIp() + ")";
        }
        if (uri.startsWith("/api/commands/")) {
            return "Komut #" + uri.replaceAll("\\D+", "") + " sonucu";
        }
        return uri;
    }
}
