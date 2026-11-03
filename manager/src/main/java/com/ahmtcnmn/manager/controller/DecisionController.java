package com.ahmtcnmn.manager.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ahmtcnmn.manager.dto.Decision.DtoDecisionRequest;
import com.ahmtcnmn.manager.dto.Decision.DtoDecisionResponse;
import com.ahmtcnmn.manager.service.DecisionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Panel: ban kararlarını listeleme, elle ban ve ban kaldırma (JWT). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/decisions")
public class DecisionController {

    private final DecisionService decisionService;

    /** status: active | inactive | (boş = hepsi); value: IP içinde arama */
    @GetMapping
    public Page<DtoDecisionResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String value,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return decisionService.list(status, value, pageable);
    }

    @PostMapping
    public DtoDecisionResponse create(@Valid @RequestBody DtoDecisionRequest request, Authentication auth) {
        return decisionService.banManually(request, auth.getName());
    }

    @DeleteMapping("/{id}")
    public DtoDecisionResponse revoke(@PathVariable Long id, Authentication auth) {
        return decisionService.revoke(id, auth.getName());
    }
}
