package com.ahmtcnmn.agent.detector;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ahmtcnmn.agent.detector.Interface.Detector;
import com.ahmtcnmn.agent.service.RuleService;
import com.ahmtcnmn.engine.CompiledDetector;
import com.ahmtcnmn.engine.Detection;
import com.ahmtcnmn.engine.ParsedLine;

import lombok.RequiredArgsConstructor;

/** rules/detectors/*.yaml kurallarını çalıştırır: satırın parser'ına bağlı her kural ayrı ayrı denenir. */
@Component
@RequiredArgsConstructor
public class YamlDetector implements Detector {

    private final RuleService ruleService;

    @Override
    public List<Detection> detect(ParsedLine line) {
        List<Detection> result = new ArrayList<>();
        for (CompiledDetector d : ruleService.rules().detectors()) {
            if (d.appliesTo(line.parser())) {
                d.detect(line).ifPresent(result::add);
            }
        }
        return result;
    }
}
