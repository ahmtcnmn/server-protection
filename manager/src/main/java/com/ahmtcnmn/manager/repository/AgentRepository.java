package com.ahmtcnmn.manager.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ahmtcnmn.manager.model.Agent;

import jakarta.validation.constraints.NotBlank;

public interface AgentRepository extends JpaRepository<Agent, Long>, JpaSpecificationExecutor<Agent> {
    
    Optional<Agent> findByApiKeyHash(String apiKeyHash);

    boolean existsByHostname(@NotBlank String hostname);
    

    long countByRulesVersion(String rulesVersion);
}
