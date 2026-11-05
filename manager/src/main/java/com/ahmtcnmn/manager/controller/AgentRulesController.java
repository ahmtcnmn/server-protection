package com.ahmtcnmn.manager.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ahmtcnmn.manager.common.exception.MessageType;
import com.ahmtcnmn.manager.common.exceptionController.ResourceNotFoundException;
import com.ahmtcnmn.manager.dto.Rules.DtoRuleBundle;
import com.ahmtcnmn.manager.service.Rules.RuleService;

import lombok.RequiredArgsConstructor;

/** Agent: güncel kural setini çeker (X-API-KEY). Yalnız heartbeat'te sürüm farkı görünce çağrılır. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/rules")
public class AgentRulesController {

    private final RuleService ruleService;

    @GetMapping
    public DtoRuleBundle rules() {
        RuleService.Loaded loaded = ruleService.current();
        if (loaded == null) {
            throw new ResourceNotFoundException(MessageType.RULES_NOT_LOADED, "Manager'da geçerli kural seti yok");
        }
        return new DtoRuleBundle(loaded.rules().version(), loaded.files());
    }
}
