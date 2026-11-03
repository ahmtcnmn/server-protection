package com.ahmtcnmn.agent.detector;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ahmtcnmn.agent.detector.Interface.Detector;

@Component
public class DbAuthFailureDetector implements Detector {

    private static final List<Pattern> PATTERNS = List.of(
        Pattern.compile("access denied", Pattern.CASE_INSENSITIVE),
        Pattern.compile("authentication failed", Pattern.CASE_INSENSITIVE)
        // 📝 kendi eklemek istediğiniz paternler
    );


    @Override
    public boolean isSuspicious(String line) {
        return PATTERNS.stream().anyMatch(p -> p.matcher(line).find());

    }

    @Override
    public String eventType() {
        return "DB_AUTH_FAILURE";
    }

    // Tek başına saldırı sayılmaz (yanlış şifre de olabilir)
    @Override
    public int severity() {
        return 3;
    }
    
}

