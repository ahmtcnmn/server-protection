package com.ahmtcnmn.manager.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ahmtcnmn.manager.common.exception.MessageType;
import com.ahmtcnmn.manager.common.exceptionController.ResourceNotFoundException;
import com.ahmtcnmn.manager.common.exceptionController.ValidationException;
import com.ahmtcnmn.manager.Libs.ColorLogger;
import com.ahmtcnmn.manager.dto.Command.DtoCommandRequest;
import com.ahmtcnmn.manager.dto.Command.DtoCommandResponse;
import com.ahmtcnmn.manager.dto.Command.DtoCommandResult;
import com.ahmtcnmn.manager.model.Agent;
import com.ahmtcnmn.manager.model.Command;
import com.ahmtcnmn.manager.model.CommandStatus;
import com.ahmtcnmn.manager.repository.AgentRepository;
import com.ahmtcnmn.manager.repository.CommandRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommandService {

    // Agent komutu aldıktan sonra bu süre içinde sonuç göndermezse komut FAILED sayılır
    private static final Duration RESULT_TIMEOUT = Duration.ofMinutes(10);
    
    private final CommandRepository commandRepository;
    private final AgentRepository agentRepository;

    public DtoCommandResponse createCommand(Long agentId, DtoCommandRequest commandRequest){
        Agent agent = agentRepository.findById(agentId).orElseThrow(()-> new ResourceNotFoundException(MessageType.AGENT_NOT_FOUND , "Agent ID: " + agentId));
        
        Command command = new Command();
        command.setAgent(agent);
        command.setCommandType(commandRequest.commandType());
        command.setPayload(commandRequest.payload());
        command.setStatus(CommandStatus.PENDING);
        command.setCreatedAt(Instant.now());
        commandRepository.save(command);
        return DtoCommandResponse.fromEntity(command);
    }

    /**
     * Agent'a teslim edilecek komutlar. Teslim edilenler DELIVERED olur; böylece bir sonraki poll'da
     * aynı komut tekrar gönderilmez (eskiden sonuç gelene kadar her 10 sn yeniden çalışıyordu).
     */
    @Transactional
    public List<Command> deliverPendingCommands(Long agentId){
        List<Command> pending = commandRepository.findByAgentIdAndStatus(agentId, CommandStatus.PENDING);
        Instant now = Instant.now();
        for (Command command : pending) {
            command.setStatus(CommandStatus.DELIVERED);
            command.setDeliveredAt(now);
        }
        return commandRepository.saveAll(pending);
    }

    /** Agent'ın gönderdiği sonuç. Komut başka bir Agent'a aitse yokmuş gibi davranılır. */
    @Transactional
    public void updateCommandResult(Agent agent, Long commandId, DtoCommandResult commandResult){
        Command command = commandRepository.findById(commandId)
            .filter(c -> c.getAgent().getId().equals(agent.getId()))
            .orElseThrow(()-> new ResourceNotFoundException(MessageType.COMMAND_NOT_FOUND , "Command ID: " + commandId));
        CommandStatus status;
        try {
            status = CommandStatus.valueOf(commandResult.status());
        } catch (IllegalArgumentException e) {
            status = null;
        }
        if (status != CommandStatus.COMPLETED && status != CommandStatus.FAILED) {
            throw new ValidationException(MessageType.INVALID_COMMAND_PARAMETERS, "Status COMPLETED ya da FAILED olmalı");
        }
        // Zaman aşımıyla FAILED sayılmış komutun sonucu sonradan (Agent kuyruğundan) gelirse yine yazılır
        command.setResult(commandResult.result());
        command.setStatus(status);
        command.setCompletedAt(Instant.now());
        commandRepository.save(command);
    }

    /** Teslim edilmiş ama sonucu hiç gelmemiş komutlar (Agent çöktü vb.) sonsuza kadar DELIVERED kalmasın. */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void failTimedOutCommands() {
        for (Command command : commandRepository.findByStatusAndDeliveredAtBefore(CommandStatus.DELIVERED, Instant.now().minus(RESULT_TIMEOUT))) {
            command.setStatus(CommandStatus.FAILED);
            command.setResult("Agent'tan sonuç gelmedi (zaman aşımı)");
            command.setCompletedAt(Instant.now());
            ColorLogger.warn("Komut #" + command.getId() + " zaman aşımına uğradı");
        }
    }

    public Page<DtoCommandResponse> getCommandHistory(Long agentId, Pageable pageable){
        return commandRepository.findByAgentId(agentId, pageable).map(DtoCommandResponse::fromEntity);
    }
}