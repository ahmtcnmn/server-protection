package com.ahmtcnmn.agent.detector;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ahmtcnmn.agent.detector.Interface.Detector;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SqliDetector implements Detector  {

    private static final List<Pattern> PATTERNS = List.of(
        Pattern.compile("('|\")\\s*(or|and)\\s*('|\")?\\d+('|\")?\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("union\\s+(all\\s+)?select", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(--|#|/\\*)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(drop\\s+table|xp_cmdshell|sleep\\s*\\()", Pattern.CASE_INSENSITIVE)
    );

    
    private static final Pattern REQUEST_LINE = Pattern.compile("\"[A-Z]+ (\\S+) HTTP/[\\d.]+\"");

    @Override
    public boolean isSuspicious(String logLine) {
        String target = requestTarget(logLine);
        return PATTERNS.stream().anyMatch(p -> p.matcher(target).find());
    }

    
    private String requestTarget(String logLine) {
        Matcher m = REQUEST_LINE.matcher(logLine);
        if (!m.find()) {
            return logLine;
        }
        String target = m.group(1);
        try {
            return URLDecoder.decode(target, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return target;
        }
    }

    @Override
    public String eventType() {
        return "SQL_INJECTION";
    }

    // Eşik aşılınca ban kararına giden saldırı
    @Override
    public int severity() {
        return 5;
    }


}
