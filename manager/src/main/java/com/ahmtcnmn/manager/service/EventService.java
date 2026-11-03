package com.ahmtcnmn.manager.service;

import java.time.Instant;
import java.util.List;

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
        event.setTimestamp(Instant.now());              
        
        eventsRepository.save(event);  
        
        if ("SQL_INJECTION".equals(event.getEventType())) {
            checkThreatThreshold(event);
        }
                        
        return "Event created successfully";
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
