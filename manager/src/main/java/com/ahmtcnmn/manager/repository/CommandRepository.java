package com.ahmtcnmn.manager.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ahmtcnmn.manager.model.Command;
import com.ahmtcnmn.manager.model.CommandStatus;


public interface CommandRepository extends JpaRepository<Command, Long> {
    List<Command> findByAgentIdAndStatus(Long agentId, CommandStatus status);
    Page<Command> findByAgentId(Long agentId, Pageable pageable);
    boolean existsByAgentIdAndCommandTypeAndPayloadAndStatus(Long agentId, String commandType, String payload, CommandStatus status);
}