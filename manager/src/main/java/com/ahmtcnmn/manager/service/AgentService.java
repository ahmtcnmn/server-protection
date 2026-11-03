package com.ahmtcnmn.manager.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.ahmtcnmn.manager.Search.AgentSearch;
import com.ahmtcnmn.manager.common.exception.MessageType;
import com.ahmtcnmn.manager.common.exceptionController.DuplicateResourceException;
import com.ahmtcnmn.manager.common.exceptionController.ResourceNotFoundException;
import com.ahmtcnmn.manager.dto.dtoAgent.DtoAgentResponse;
import com.ahmtcnmn.manager.dto.dtoAgent.DtoRegisterRequest;
import com.ahmtcnmn.manager.dto.dtoAgent.DtoRegisterResponse;
import com.ahmtcnmn.manager.model.Agent;
import com.ahmtcnmn.manager.repository.AgentRepository;
import com.ahmtcnmn.manager.service.ApiKey.GenerateApiKey;
import com.ahmtcnmn.manager.service.ImplementService.IAgentService;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentService implements IAgentService {

    private final AgentRepository agentRepository;
    private final GenerateApiKey generateApiKey;


    private String hashApiKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algoritması bulunamadı", e);
        }
    }

    public DtoAgentResponse getAgentById(Long id) {
        Agent agent = agentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(MessageType.AGENT_NOT_FOUND, "Agent with ID " + id + " not found"));
        return DtoAgentResponse.from(agent);

    }

    @Override
    public DtoRegisterResponse registerAgent(DtoRegisterRequest request) {
        if (agentRepository.existsByHostname(request.hostName().trim())) {
            throw new DuplicateResourceException(MessageType.AGENT_ALREADY_EXISTS, "Agent with the same host name already exists");
        }
        String rawApiKey = generateApiKey.generateApiKey();
        String hashedKey = hashApiKey(rawApiKey);
        Agent agent = new Agent();
        agent.setHostname(request.hostName().trim());
        agent.setApiKeyHash(hashedKey);
        // lastSeen boş kalır: ilk heartbeat gelene kadar Agent "online" görünmesin
        agent.setCreatedAt(Instant.now());
        Agent saved = agentRepository.save(agent);
        return new DtoRegisterResponse(saved.getId(), rawApiKey);
    }

    public Page<DtoAgentResponse> listAgents(String search, String status, Pageable pageable) {
        Specification<Agent> spec = AgentSearch.hasHostname(null); 
        if (search != null && !search.isEmpty()) {
            spec = spec.and(AgentSearch.hasHostname(search));
        }
        // "true"/"online" → çevrimiçi, "false"/"offline" → çevrimdışı; boş ya da "all" → filtre yok
        if (status != null) {
            switch (status.trim().toLowerCase()) {
                case "true", "online" -> spec = spec.and(AgentSearch.isOnline(true));
                case "false", "offline" -> spec = spec.and(AgentSearch.isOnline(false));
                default -> { }
            }
        }
        Page<Agent> agents = agentRepository.findAll(spec, pageable);
        return agents.map(DtoAgentResponse::from);   // Page<Agent> → Page<AgentResponse>
    }


}