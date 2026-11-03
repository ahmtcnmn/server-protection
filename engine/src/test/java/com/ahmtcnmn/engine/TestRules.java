package com.ahmtcnmn.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/** Testlerde kullanılan küçük kural seti (gerçek kurallar repo kökündeki rules/ klasöründe). */
final class TestRules {

    static final String NGINX = """
            name: nginx-access
            regex: '^(?<sourceIp>\\S+) \\S+ \\S+ \\[(?<time>[^\\]]+)\\] "(?<method>[A-Z]+) (?<url>\\S+)(?: HTTP/[\\d.]+)?" (?<status>\\d{3}) \\S+(?: "(?<referer>[^"]*)" "(?<userAgent>[^"]*)")?'
            timeFormat: "dd/MMM/yyyy:HH:mm:ss Z"
            """;

    static final String RAW = """
            name: raw-line
            regex: '^(?<message>.+)$'
            extract:
              sourceIp: '\\b((?:\\d{1,3}\\.){3}\\d{1,3})\\b'
            """;

    static final String SQLI = """
            name: sqli-test
            eventType: SQL_INJECTION
            severity: 5
            parsers: [nginx-access]
            field: url
            transforms: [urlDecode]
            maxLength: 64
            patterns:
              - '(?i)union\\s+(all\\s+)?select'
              - "(?i)('|\\")\\\\s*(or|and)\\\\s*('|\\")?\\\\d+('|\\")?\\\\s*="
            """;

    static final String DB_AUTH = """
            name: db-auth-test
            eventType: DB_AUTH_FAILURE
            severity: 3
            parsers: [raw-line]
            patterns:
              - '(?i)access denied'
            """;

    private TestRules() {}

    static Map<String, String> files() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("parsers/nginx-access.yaml", NGINX);
        files.put("parsers/raw-line.yaml", RAW);
        files.put("detectors/sqli-test.yaml", SQLI);
        files.put("detectors/db-auth-test.yaml", DB_AUTH);
        return files;
    }

    static RuleSet ruleSet() {
        return RuleLoader.fromFiles(files());
    }

    static String nginxLine(String ip, String target, String referer) {
        return ip + " - - [03/Nov/2026:14:00:00 +0300] \"GET " + target + " HTTP/1.1\" 200 512 \"" + referer + "\" \"Mozilla/5.0\"";
    }
}
