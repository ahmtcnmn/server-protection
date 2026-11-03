package com.ahmtcnmn.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class LogEngineTest {

    private final RuleSet rules = TestRules.ruleSet();
    private static final List<String> WEB = List.of("nginx-access");

    @Test
    void parserFillsFieldsAndTime() {
        var r = LogEngine.process(rules, TestRules.nginxLine("203.0.113.7", "/urun?id=5", "-"), WEB);
        assertTrue(r.parsedOk());
        assertEquals("203.0.113.7", r.parsed().field("sourceIp"));
        assertEquals("GET", r.parsed().field("method"));
        assertEquals("/urun?id=5", r.parsed().field("url"));
        assertEquals("200", r.parsed().field("status"));
        assertEquals("Mozilla/5.0", r.parsed().field("userAgent"));
        assertEquals(Instant.parse("2026-11-03T11:00:00Z"), r.parsed().time());
        assertTrue(r.detections().isEmpty());
    }

    @Test
    void ipv6SourceIpIsTakenFromParser() {
        var r = LogEngine.process(rules, TestRules.nginxLine("2001:db8::7", "/", "-"), WEB);
        assertEquals("2001:db8::7", r.parsed().field("sourceIp"));
    }

    @Test
    void optionalGroupsAreLeftOutWhenMissing() {
        String line = "203.0.113.7 - - [03/Nov/2026:14:00:00 +0300] \"GET / HTTP/1.1\" 200 512";
        var r = LogEngine.process(rules, line, WEB);
        assertTrue(r.parsedOk());
        assertFalse(r.parsed().fields().containsKey("userAgent"));
    }

    @Test
    void urlEncodedAttackIsDetectedInUrlField() {
        var r = LogEngine.process(rules, TestRules.nginxLine("203.0.113.7", "/urun?id=1%27%20OR%201=1--", "-"), WEB);
        assertEquals(1, r.detections().size());
        Detection d = r.detections().get(0);
        assertEquals("sqli-test", d.rule());
        assertEquals("SQL_INJECTION", d.eventType());
        assertEquals(5, d.severity());
        assertEquals("url", d.field());
        assertEquals("' OR 1=", d.matched());
    }

    @Test
    void attackInRefererIsIgnoredBecauseDetectorLooksAtUrlOnly() {
        // Ekim'deki false positive: sayfanın css/js istekleri Referer'da saldırı URL'ini taşıyordu
        String line = TestRules.nginxLine("203.0.113.7", "/style.css", "https://site/x?id=1 UNION SELECT 1");
        assertTrue(LogEngine.process(rules, line, WEB).detections().isEmpty());
    }

    @Test
    void textBeyondMaxLengthIsNotInspected() {
        String padding = "a".repeat(80); // test kuralında maxLength 64
        var r = LogEngine.process(rules, TestRules.nginxLine("203.0.113.7", "/x?p=" + padding + "+UNION+SELECT+1", "-"), WEB);
        assertTrue(r.detections().isEmpty());
    }

    @Test
    void firstMatchingParserWinsAndExtractFillsMissingFields() {
        var r = LogEngine.process(rules, "2026-11-03 Access denied for user 'root'@'192.0.2.45'", List.of("nginx-access", "raw-line"));
        assertEquals("raw-line", r.parsed().parser());
        assertEquals("192.0.2.45", r.parsed().field("sourceIp"));
        assertNull(r.parsed().time());
        assertEquals(List.of("db-auth-test"), r.detections().stream().map(Detection::rule).toList());
    }

    @Test
    void detectorsOnlyRunOnTheirOwnParsers() {
        // raw-line'a bağlı db-auth kuralı nginx satırında çalışmaz (satırda "access denied" geçse bile)
        var r = LogEngine.process(rules, TestRules.nginxLine("203.0.113.7", "/access%20denied", "-"), WEB);
        assertTrue(r.detections().isEmpty());
    }

    @Test
    void unparsedLineGivesNoResult() {
        var r = LogEngine.process(rules, "bu bir nginx satırı değil", WEB);
        assertFalse(r.parsedOk());
        assertTrue(r.detections().isEmpty());
    }

    @Test
    void unknownParserNameIsAProgrammingError() {
        assertThrows(IllegalArgumentException.class, () -> LogEngine.process(rules, "x", List.of("yok")));
    }
}
