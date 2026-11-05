package com.ahmtcnmn.manager.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ahmtcnmn.engine.CompiledDetector;
import com.ahmtcnmn.engine.CompiledParser;
import com.ahmtcnmn.manager.Libs.ColorLogger;
import com.ahmtcnmn.manager.common.exception.MessageType;
import com.ahmtcnmn.manager.common.exceptionController.ValidationException;
import com.ahmtcnmn.manager.dto.Rules.DtoRulesStatus;
import com.ahmtcnmn.manager.repository.AgentRepository;
import com.ahmtcnmn.manager.service.Rules.RuleService;

import lombok.RequiredArgsConstructor;

/** Panel: kural setini görüntüleme ve klasörü hemen yeniden yükleme (JWT). Kurallar panelden düzenlenmez (V2). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rules")
public class RulesController {

    private final RuleService ruleService;
    private final AgentRepository agentRepository;

    @GetMapping
    public DtoRulesStatus status() {
        RuleService.Loaded loaded = ruleService.current();
        String version = ruleService.currentVersion();
        long total = agentRepository.count();
        long inSync = version == null ? 0 : agentRepository.countByRulesVersion(version);
        if (loaded == null) {
            return new DtoRulesStatus(null, null, ruleService.rulesDir().toString(), ruleService.lastError(),
                    ruleService.lastErrorAt(), java.util.List.of(), java.util.List.of(), total, inSync);
        }
        Map<String, String> fileOf = fileNames(loaded);
        return new DtoRulesStatus(
                version,
                loaded.loadedAt(),
                ruleService.rulesDir().toString(),
                ruleService.lastError(),
                ruleService.lastErrorAt(),
                loaded.rules().parsers().values().stream()
                        .sorted(java.util.Comparator.comparing(CompiledParser::name))
                        .map(p -> new DtoRulesStatus.Parser(p.name(), p.spec().description(), fileOf.get("p:" + p.name())))
                        .toList(),
                loaded.rules().detectors().stream()
                        .map(CompiledDetector::spec)
                        .map(d -> new DtoRulesStatus.Detector(d.name(), d.description(), d.eventType(), d.severity(),
                                d.parsers(), d.field(), d.transforms(), d.patterns(), fileOf.get("d:" + d.name())))
                        .toList(),
                total,
                inSync);
    }

    @PostMapping("/reload")
    public DtoRulesStatus reload(Authentication auth) {
        ColorLogger.info("Kural klasörü yeniden yükleniyor (" + auth.getName() + ")");
        String error = ruleService.reload();
        if (error != null) {
            throw new ValidationException(MessageType.INVALID_RULES, error);
        }
        return status();
    }

    /** "p:<parser>" / "d:<detector>" → dosya yolu (panelde hangi dosyada olduğu görünsün). */
    private static Map<String, String> fileNames(RuleService.Loaded loaded) {
        Map<String, String> result = new java.util.HashMap<>();
        loaded.files().forEach((path, content) -> {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^name:\\s*['\"]?([a-z0-9-]+)").matcher(content);
            if (m.find()) {
                result.put((path.startsWith("parsers/") ? "p:" : "d:") + m.group(1), path);
            }
        });
        return result;
    }
}
