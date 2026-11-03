package com.ahmtcnmn.manager.service;

import com.ahmtcnmn.manager.Search.AgentSearch;
import com.ahmtcnmn.manager.Search.EventSearch;
import com.ahmtcnmn.manager.dto.Dashboard.DtoDashboardSummary;
import com.ahmtcnmn.manager.dto.Dashboard.DtoTimelinePoint;
import com.ahmtcnmn.manager.model.Events;
import com.ahmtcnmn.manager.repository.AgentRepository;
import com.ahmtcnmn.manager.repository.DecisionRepository;
import com.ahmtcnmn.manager.repository.EventsRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AgentRepository agentRepository;
    private final EventsRepository eventsRepository;
    private final DecisionRepository decisionRepository;

    public DtoDashboardSummary getSummary() {
        long totalAgents = agentRepository.count();
        long onlineAgents = agentRepository.count(AgentSearch.isOnline(true));

        Instant startOfToday = LocalDate.now(ZoneOffset.UTC)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();

        long eventsToday = eventsRepository.count(
            EventSearch.timestampBetween(startOfToday, Instant.now())
        );

        long criticalEventsToday = eventsRepository.count(
            EventSearch.timestampBetween(startOfToday, Instant.now())
                .and(EventSearch.severityBetween(5, 5))
        );

        return new DtoDashboardSummary(totalAgents, onlineAgents, eventsToday, criticalEventsToday,
                decisionRepository.countByRevokedAtIsNull());
    }

    public List<DtoTimelinePoint> getEventsTimeline(int hours){
        Instant from= Instant.now().minus(hours, ChronoUnit.HOURS);
        List<Events> events= eventsRepository.findAll(EventSearch.timestampBetween(from, Instant.now()));
        Map<Instant,Long> timelineData= events.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getTimestamp().truncatedTo(ChronoUnit.HOURS),
                        Collectors.counting()
                ));
        Map<Instant, Map<Integer, Long>> severityTimelineData= events.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getTimestamp().truncatedTo(ChronoUnit.HOURS),
                        Collectors.groupingBy(
                                Events::getSeverity,
                                Collectors.counting()
                        )
                ));
        Map<Instant, DtoTimelinePoint> timelinePoints= timelineData.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            Instant hour= entry.getKey();
                            long low = severityTimelineData.getOrDefault(hour, Map.of()).getOrDefault(1, 0L)
                                    + severityTimelineData.getOrDefault(hour, Map.of()).getOrDefault(2, 0L);
                            long medium = severityTimelineData.getOrDefault(hour, Map.of()).getOrDefault(3, 0L);
                            long high = severityTimelineData.getOrDefault(hour, Map.of()).getOrDefault(4, 0L);
                            long critical = severityTimelineData.getOrDefault(hour, Map.of()).getOrDefault(5, 0L);
                            return new DtoTimelinePoint(hour.toString(), low, medium, high, critical);
                        }
                ));
        return timelinePoints.values().stream()
                .sorted((p1, p2) -> p1.hour().compareTo(p2.hour()))
                .toList();
    }

}