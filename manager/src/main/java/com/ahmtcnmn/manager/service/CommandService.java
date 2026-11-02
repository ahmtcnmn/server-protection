package com.ahmtcnmn.manager.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.ahmtcnmn.manager.common.exception.MessageType;
import com.ahmtcnmn.manager.common.exceptionController.ResourceNotFoundException;
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

    public boolean hasPendingCommand(Long agentId, String commandType, String payload) {
        return commandRepository.existsByAgentIdAndCommandTypeAndPayloadAndStatus(
                agentId, commandType, payload, CommandStatus.PENDING);
    }

    public List<Command> getPendingCommands(Long AgentId){
        return commandRepository.findByAgentIdAndStatus(AgentId, CommandStatus.PENDING);
    }


    public void updateCommandResult(Long commandId, DtoCommandResult commandResult){
        Command command = commandRepository.findById(commandId).orElseThrow(()-> new ResourceNotFoundException(MessageType.COMMAND_NOT_FOUND , "Command ID: " + commandId));
        command.setResult(commandResult.result());
        command.setStatus(CommandStatus.valueOf(commandResult.status()));
        command.setCompletedAt(Instant.now());
        commandRepository.save(command);
    }

    public Page<DtoCommandResponse> getCommandHistory(Long agentId, Pageable pageable){
        return commandRepository.findByAgentId(agentId, pageable).map(DtoCommandResponse::fromEntity);
    }
}