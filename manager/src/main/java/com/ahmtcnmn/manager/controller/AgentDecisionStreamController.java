package com.ahmtcnmn.manager.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ahmtcnmn.manager.dto.Decision.DtoDecisionStream;
import com.ahmtcnmn.manager.model.Agent;
import com.ahmtcnmn.manager.service.DecisionService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Agent: ban kararlarındaki değişiklikleri çeker (X-API-KEY, ApiKeyInterceptor korur).
 * Yeni Agent endpoint'leri /api/agent/** altında (panel endpoint'leriyle auth çakışmasın).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/decisions")
public class AgentDecisionStreamController {

    private final DecisionService decisionService;

    @GetMapping("/stream")
    public DtoDecisionStream stream(
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "false") boolean startup,
            HttpServletRequest request) {
        Agent agent = (Agent) request.getAttribute("authenticatedAgent");
        return decisionService.stream(agent, after, startup);
    }
}
