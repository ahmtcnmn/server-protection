package com.ahmtcnmn.manager.service.ApiKey;

import java.util.Optional;


import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ahmtcnmn.manager.model.Agent;
import com.ahmtcnmn.manager.repository.AgentRepository;
import com.ahmtcnmn.manager.service.User.HashApiKey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ApiKeyInterceptor implements HandlerInterceptor {
    
    private final AgentRepository agentModelRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String rawKey = request.getHeader("X-API-KEY");
        // Header yoksa hash'leme (null → NullPointerException → 500 dönüyordu)
        if (rawKey == null || rawKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        Optional<Agent> agentOptional = agentModelRepository.findByApiKeyHash(HashApiKey.hashApiKey(rawKey));
        if (agentOptional.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        request.setAttribute("authenticatedAgent", agentOptional.get());
        return true;
    }
}
