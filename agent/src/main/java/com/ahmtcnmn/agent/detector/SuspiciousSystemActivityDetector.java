package com.ahmtcnmn.agent.detector;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ahmtcnmn.agent.detector.Interface.Detector;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SuspiciousSystemActivityDetector implements Detector{
    
    private static final List<Pattern> PATTERNS = List.of(
        Pattern.compile("failed password", Pattern.CASE_INSENSITIVE),
        Pattern.compile("authentication failure", Pattern.CASE_INSENSITIVE),
        Pattern.compile("invalid user", Pattern.CASE_INSENSITIVE),
        Pattern.compile("session opened for user root", Pattern.CASE_INSENSITIVE),
        Pattern.compile("break-in attempt", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public boolean isSuspicious(String logLine) {
        return PATTERNS.stream().anyMatch(p -> p.matcher(logLine).find());
    }

    @Override
    public String eventType() {
        return "SUSPICIOUS_SYSTEM_ACTIVITY";
    }

    // SSH başarısız giriş vb.; tek satır saldırı kanıtı değil
    @Override
    public int severity() {
        return 3;
    }
}
