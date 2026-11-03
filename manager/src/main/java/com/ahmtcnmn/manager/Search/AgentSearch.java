package com.ahmtcnmn.manager.Search;

import org.springframework.data.jpa.domain.Specification;

import com.ahmtcnmn.manager.model.Agent;

public class AgentSearch {
    public static Specification<Agent> hasHostname(String hostname) {
        return (root, query, criteriaBuilder) -> {
            if (hostname == null || hostname.isEmpty()) {
                return criteriaBuilder.conjunction(); // No filtering if hostname is null or empty
            }
            return criteriaBuilder.equal(root.get("hostname"), hostname);
        };
    }

    public static Specification<Agent> isOnline(Boolean isOnline) {
        return (root, query, criteriaBuilder) -> {
            if (isOnline == null) {
                return criteriaBuilder.conjunction(); // No filtering if isOnline is null
            }
            if (isOnline) {
                return criteriaBuilder.greaterThan(root.get("lastSeen"), java.time.Instant.now().minusSeconds(60));
            } else {
                // Hiç heartbeat göndermemiş (lastSeen = null) Agent'lar da offline sayılır
                return criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("lastSeen")),
                    criteriaBuilder.lessThanOrEqualTo(root.get("lastSeen"), java.time.Instant.now().minusSeconds(60)));
            }
        };
    }
}
