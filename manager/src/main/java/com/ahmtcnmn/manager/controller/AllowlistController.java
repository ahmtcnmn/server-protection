package com.ahmtcnmn.manager.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ahmtcnmn.manager.dto.Allowlist.DtoAllowlistCreated;
import com.ahmtcnmn.manager.dto.Allowlist.DtoAllowlistRequest;
import com.ahmtcnmn.manager.dto.Allowlist.DtoAllowlistResponse;
import com.ahmtcnmn.manager.service.AllowlistService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Panel: asla banlanmayacak IP/CIDR'lar (JWT). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/allowlist")
public class AllowlistController {

    private final AllowlistService allowlistService;

    @GetMapping
    public List<DtoAllowlistResponse> list() {
        return allowlistService.list();
    }

    @PostMapping
    public DtoAllowlistCreated add(@Valid @RequestBody DtoAllowlistRequest request, Authentication auth) {
        return allowlistService.add(request, auth.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id, Authentication auth) {
        allowlistService.remove(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
