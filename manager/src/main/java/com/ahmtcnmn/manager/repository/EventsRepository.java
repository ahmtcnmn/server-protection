package com.ahmtcnmn.manager.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ahmtcnmn.manager.dto.EventTypeCount;
import com.ahmtcnmn.manager.model.Alert;
import com.ahmtcnmn.manager.model.Events;


public interface EventsRepository extends JpaRepository<Events, Long>, JpaSpecificationExecutor<Events> {
    
    // EventRepository içinde
    @Query("SELECT e.eventType as type, COUNT(e) as count FROM Events e WHERE e.agent.id = :agentId GROUP BY e.eventType")
    List<EventTypeCount> countByEventType(@Param("agentId") Long agentId);

    // Alert'i tetikleyen event'leri işaretler (panelde "bu ban hangi olaylardan çıktı" görünsün)
    @Modifying
    @Query("""
        update Events e set e.alert = :alert
        where e.sourceIp = :ip and e.eventType = :type and e.timestamp >= :since and e.alert is null
        """)
    int linkToAlert(@Param("alert") Alert alert, @Param("ip") String ip, @Param("type") String type, @Param("since") Instant since);

    @Query("select min(e.timestamp) from Events e where e.sourceIp = :ip and e.eventType = :type and e.timestamp >= :since")
    Instant findFirstTimestamp(@Param("ip") String ip, @Param("type") String type, @Param("since") Instant since);
}
