package com.ahmtcnmn.manager.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ahmtcnmn.manager.model.Command;
import com.ahmtcnmn.manager.model.CommandStatus;


public interface CommandRepository extends JpaRepository<Command, Long> {
    List<Command> findByAgentIdAndStatus(Long agentId, CommandStatus status);
    List<Command> findByStatusAndDeliveredAtBefore(CommandStatus status, Instant before);
    Page<Command> findByAgentId(Long agentId, Pageable pageable);
}