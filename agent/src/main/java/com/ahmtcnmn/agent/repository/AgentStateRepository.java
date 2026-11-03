package com.ahmtcnmn.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ahmtcnmn.agent.model.AgentState;

public interface AgentStateRepository extends JpaRepository<AgentState, String> {
}
