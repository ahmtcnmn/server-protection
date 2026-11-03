package com.ahmtcnmn.manager.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ahmtcnmn.manager.dto.Command.DtoCommandResponse;
import com.ahmtcnmn.manager.dto.Command.DtoCommandResult;
import com.ahmtcnmn.manager.model.Agent;
import com.ahmtcnmn.manager.service.CommandService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/commands")
@RequiredArgsConstructor
public class CommandPollController {

    private final CommandService commandService;

    @GetMapping("/pending")
    public List<DtoCommandResponse> getPendingCommands(HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("authenticatedAgent");
        return commandService.deliverPendingCommands(agent.getId()).stream()
            .map(DtoCommandResponse::fromEntity)
            .toList();
    }

    @PostMapping("/{id}/result")
    public void submitResult(@PathVariable Long id, @Valid @RequestBody DtoCommandResult result, HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("authenticatedAgent");
        commandService.updateCommandResult(agent, id, result);
    }
}