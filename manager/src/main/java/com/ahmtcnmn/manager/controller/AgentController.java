package com.ahmtcnmn.manager.controller;

import com.ahmtcnmn.manager.service.CommandService;
import java.time.Instant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ahmtcnmn.manager.Libs.ColorLogger;
import com.ahmtcnmn.manager.common.RestBaseController;
import com.ahmtcnmn.manager.common.exception.MessageType;
import com.ahmtcnmn.manager.common.exceptionController.CommandExecutionException;
import com.ahmtcnmn.manager.common.exceptionController.RootEntity;
import com.ahmtcnmn.manager.controller.ImplementController.IAgentController;
import com.ahmtcnmn.manager.dto.Command.DtoCommandRequest;
import com.ahmtcnmn.manager.dto.Command.DtoCommandResponse;
import com.ahmtcnmn.manager.dto.dtoAgent.DtoAgentResponse;
import com.ahmtcnmn.manager.dto.dtoAgent.DtoRegisterRequest;
import com.ahmtcnmn.manager.dto.dtoAgent.DtoRegisterResponse;
import com.ahmtcnmn.manager.model.Agent;
import com.ahmtcnmn.manager.repository.AgentRepository;
import com.ahmtcnmn.manager.service.AgentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;



@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agents")
public class AgentController extends RestBaseController implements IAgentController {

    private final CommandService commandService;
    private final AgentService agentService;
    private final AgentRepository agentModelRepository;


    @GetMapping("/{id}")
    public DtoAgentResponse getAgent(@PathVariable Long id) {
        return agentService.getAgentById(id);
    }

    @Override
    @PostMapping("/register")
    public RootEntity<DtoRegisterResponse> registerAgent(@Valid @RequestBody DtoRegisterRequest request) {
        return ok(agentService.registerAgent(request));
    }

    @Override
    @PostMapping("/heartbeat")
    public RootEntity<Void> heartBeat(HttpServletRequest request) {
        Agent agent= (Agent) request.getAttribute("authenticatedAgent");
        if(agent==null){
            throw new CommandExecutionException(MessageType.COMMAND_EXECUTION_FAILED, "Agent not found");
        }
        agent.setLastSeen(Instant.now());
        agentModelRepository.save(agent);
        ColorLogger.info("Received heartbeat from agent: " + agent.getHostname() + " at isOnline : " + agent.isOnline());
        return ok();
    }


    @GetMapping
    public Page<DtoAgentResponse> listAgents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            Pageable pageable
    ) {
        return agentService.listAgents(search, status, pageable);
    }
    
    @PostMapping("/{id}/commands")
    public DtoCommandResponse sendCommand(@PathVariable Long id, @Valid @RequestBody DtoCommandRequest commandRequest) {
        return commandService.createCommand(id, commandRequest);
    }

    @GetMapping("/{id}/commands")
    public Page<DtoCommandResponse> getCommandHistory(@PathVariable Long id, Pageable pageable) {
        return commandService.getCommandHistory(id, pageable);
    }
    
    
    
    
    
    
}
