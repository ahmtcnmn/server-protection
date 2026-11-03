package com.ahmtcnmn.agent.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ahmtcnmn.agent.model.AppliedDecision;

public interface AppliedDecisionRepository extends JpaRepository<AppliedDecision, Long> {

    boolean existsByValue(String value);

    List<AppliedDecision> findByExpiresAtBefore(Instant time);
}
