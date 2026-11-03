package com.ahmtcnmn.manager.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.ahmtcnmn.manager.Libs.ColorLogger;
import com.ahmtcnmn.manager.Search.EventSearch;
import com.ahmtcnmn.manager.common.exception.MessageType;
import com.ahmtcnmn.manager.common.exceptionController.AgentNotFoundException;
import com.ahmtcnmn.manager.dto.DtoEventTypeCount;
import com.ahmtcnmn.manager.dto.EventTypeCount;
import com.ahmtcnmn.manager.dto.Event.DtoEventRequest;
import com.ahmtcnmn.manager.dto.Event.DtoEventResponse;
import com.ahmtcnmn.manager.model.Agent;
import com.ahmtcnmn.manager.model.Events;
import com.ahmtcnmn.manager.repository.EventsRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

    private final DecisionService decisionService;

    private final EventsRepository eventsRepository;
    
    // Aynı IP'den 60 sn içinde bu kadar SQL_INJECTION gelirse BLOCK_IP üretilir
    @Value("${security.sqli-threshold:3}")
    private int sqliThreshold;

    public List<DtoEventTypeCount> getEventTypeBreakdown(Long agentId) {
        List<EventTypeCount> rawCounts = eventsRepository.countByEventType(agentId);

    long total = rawCounts.stream()
        .mapToLong(EventTypeCount::getCount)
        .sum();

    return rawCounts.stream()
        .map(row -> new DtoEventTypeCount(
            row.getType(),
            row.getCount(),
            total == 0 ? 0.0 : (row.getCount() * 100.0) / total
        ))
        .toList();
    }
    
    public String createEvent(DtoEventRequest eventRequest, HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("authenticatedAgent");
        if (agent == null) {
            throw new AgentNotFoundException(MessageType.AGENT_NOT_FOUND, "Agent not found");
        }

        Events event = new Events();
        event.setAgent(agent);                          
        event.setEventType(eventRequest.eventType());   
        event.setSeverity(eventRequest.severity());
        event.setRawData(eventRequest.rawData());
        event.setSourceIp(eventRequest.sourceIp());
        event.setRule(eventRequest.rule());
        event.setMatchField(eventRequest.matchField());
        event.setMatched(eventRequest.matched());
        event.setParser(eventRequest.parser());
        event.setFields(limitFields(eventRequest.fields()));
        event.setObservedAt(eventRequest.observedAt());
        event.setQueued(Boolean.TRUE.equals(eventRequest.queued()));
        event.setTimestamp(Instant.now());              
        
        eventsRepository.save(event);  
        
        // Kuyruktan geç gelen event'ler eşiğe sayılmaz: hepsi aynı anda gelip sahte bir "patlama" gibi görünür
        // ve saatler önceki bir saldırı için şimdi ban verilirdi (roadmap 1.5)
        if ("SQL_INJECTION".equals(event.getEventType()) && !event.getQueued()) {
            checkThreatThreshold(event);
        }
                        
        return "Event created successfully";
    }

    // Alan değerleri DB'de şişmesin (Agent zaten 2 KB'ta kesiyor; bu yalnız ikinci emniyet)
    private static final int MAX_FIELD_VALUE = 4096;

    private static Map<String, String> limitFields(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) return null;
        Map<String, String> result = new LinkedHashMap<>();
        fields.forEach((k, v) -> {
            if (k != null && v != null && k.length() <= 64) {
                result.put(k, v.length() > MAX_FIELD_VALUE ? v.substring(0, MAX_FIELD_VALUE) : v);
            }
        });
        return result;
    }

    public Page<DtoEventResponse> listEvents(
                                            Long agentId, String type, Integer severityMin,
                                            Integer severityMax, Instant from, Instant to,
                                            String search, Pageable pageable) {
        Specification<Events> spec = EventSearch.hasAgentId(null);
        spec = spec.and(EventSearch.hasAgentId(agentId));
        spec = spec.and(EventSearch.hasType(type));
        spec = spec.and(EventSearch.severityBetween(severityMin, severityMax));
        spec = spec.and(EventSearch.timestampBetween(from, to));
        spec = spec.and(EventSearch.rawDataContains(search));
        
        return eventsRepository.findAll(spec, pageable).map(DtoEventResponse::from);
    }

    public void checkThreatThreshold(Events savedEvent) {
        if (savedEvent.getSourceIp() == null || "unknown".equals(savedEvent.getSourceIp())) {
            return;
        }

        Instant since = Instant.now().minusSeconds(60);
        long recentCount = eventsRepository.count(
            EventSearch.hasSourceIp(savedEvent.getSourceIp())
                .and(EventSearch.hasType("SQL_INJECTION"))
                .and(EventSearch.timestampBetween(since, Instant.now()))
        );

        if (recentCount >= sqliThreshold) {
            // Whitelist, mükerrer ban ve süre kontrolü DecisionService'te
            decisionService.raiseScenario("http-sqli-threshold", savedEvent.getAgent(), savedEvent.getSourceIp(),
                    "SQL_INJECTION", since, recentCount);
        }
    }
}
