package com.ahmtcnmn.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Repo kökündeki gerçek kuralları (rules/) yükler ve rules/tests altındaki örneklerle doğrular.
 *   rules/tests/parsers/<parser>.yaml     → cases: line, fields, absent, time, parsed
 *   rules/tests/detectors/<detector>.yaml → match / noMatch / knownFalsePositives
 * Yeni bir kural eklendiğinde test dosyası da eklenmeli (allRulesHaveTests).
 */
class RulesDirectoryTest {

    private static final Path RULES = Path.of(System.getProperty("sentinel.rules.dir", "../rules"));
    private static final RuleSet RULE_SET = RuleLoader.fromDirectory(RULES);

    @Test
    void allRulesHaveTests() {
        List<String> missing = new ArrayList<>();
        RULE_SET.parsers().keySet().forEach(n -> {
            if (!Files.exists(RULES.resolve("tests/parsers/" + n + ".yaml"))) missing.add("tests/parsers/" + n + ".yaml");
        });
        RULE_SET.detectors().forEach(d -> {
            if (!Files.exists(RULES.resolve("tests/detectors/" + d.name() + ".yaml"))) missing.add("tests/detectors/" + d.name() + ".yaml");
        });
        assertTrue(missing.isEmpty(), "Test dosyası olmayan kurallar: " + missing);
    }

    // ---------------------------------------------------------------- parser testleri

    @TestFactory
    Stream<DynamicNode> parsers() throws IOException {
        return testFiles("parsers").map(file -> {
            Map<?, ?> doc = load(file);
            String parserName = (String) doc.get("parser");
            CompiledParser parser = RULE_SET.parser(parserName)
                    .orElseThrow(() -> new AssertionError(file + ": tanımsız parser " + parserName));
            List<DynamicTest> tests = new ArrayList<>();
            for (Map<?, ?> c : maps(doc.get("cases"))) {
                tests.add(DynamicTest.dynamicTest(str(c.get("name")), () -> checkParserCase(parser, c)));
            }
            return DynamicContainer.dynamicContainer("parser " + parserName, tests);
        });
    }

    private static void checkParserCase(CompiledParser parser, Map<?, ?> c) {
        String line = str(c.get("line"));
        Optional<ParsedLine> parsed = parser.parse(line);
        if (Boolean.FALSE.equals(c.get("parsed"))) {
            assertTrue(parsed.isEmpty(), "Satır parse edilmemeliydi: " + line);
            return;
        }
        assertTrue(parsed.isPresent(), "Satır parse edilemedi: " + line);
        ParsedLine p = parsed.get();
        if (c.get("fields") instanceof Map<?, ?> fields) {
            fields.forEach((k, v) -> assertEquals(str(v), p.field(str(k)), "alan " + k));
        }
        if (c.get("absent") != null) {
            for (Object k : (List<?>) c.get("absent")) {
                assertNull(p.field(str(k)), "alan olmamalıydı: " + k);
            }
        }
        if (c.get("time") != null) {
            Object t = c.get("time");
            Instant expected = t instanceof Date d ? d.toInstant() : Instant.parse(str(t));
            assertEquals(expected, p.time(), "time");
        }
    }

    // ---------------------------------------------------------------- detector testleri

    @TestFactory
    Stream<DynamicNode> detectors() throws IOException {
        return testFiles("detectors").map(file -> {
            Map<?, ?> doc = load(file);
            String name = (String) doc.get("detector");
            CompiledDetector detector = RULE_SET.detectors().stream().filter(d -> d.name().equals(name)).findFirst()
                    .orElseThrow(() -> new AssertionError(file + ": tanımsız detector " + name));
            List<DynamicTest> tests = new ArrayList<>();
            List<Map<?, ?>> match = maps(doc.get("match"));
            List<Map<?, ?>> noMatch = maps(doc.get("noMatch"));
            if (match.isEmpty() || noMatch.isEmpty()) {
                tests.add(DynamicTest.dynamicTest("en az bir match ve bir noMatch örneği",
                        () -> fail(file + ": her detector için en az bir match ve bir noMatch örneği gerekli")));
            }
            for (Map<?, ?> c : match) {
                tests.add(DynamicTest.dynamicTest("yakalamalı: " + str(c.get("name")), () -> {
                    Detection d = detect(detector, str(c.get("line")));
                    assertNotNull(d, "Yakalanmadı: " + c.get("line"));
                    if (c.get("matched") != null) {
                        assertEquals(str(c.get("matched")), d.matched(), "eşleşen metin");
                    }
                }));
            }
            for (Map<?, ?> c : noMatch) {
                tests.add(DynamicTest.dynamicTest("yakalamamalı: " + str(c.get("name")), () -> {
                    Detection d = detect(detector, str(c.get("line")));
                    assertNull(d, () -> "Yanlışlıkla yakalandı (" + d.pattern() + " → '" + d.matched() + "'): " + c.get("line"));
                }));
            }
            for (Map<?, ?> c : maps(doc.get("knownFalsePositives"))) {
                tests.add(DynamicTest.dynamicTest("bilinen false positive: " + str(c.get("name")), () ->
                        assertNotNull(detect(detector, str(c.get("line"))),
                                "Artık yakalanmıyor: örneği knownFalsePositives'ten noMatch'e taşıyın")));
            }
            return DynamicContainer.dynamicContainer("detector " + name, tests);
        });
    }

    /** Satırı detector'ın kendi parser'larından geçirir; bu detector'ın sonucunu döner (yoksa null). */
    private static Detection detect(CompiledDetector detector, String line) {
        LogEngine.Result r = LogEngine.process(RULE_SET, line, detector.spec().parsers());
        return r.detections().stream().filter(d -> d.rule().equals(detector.name())).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- yardımcılar

    private static Stream<Path> testFiles(String kind) throws IOException {
        Path dir = RULES.resolve("tests").resolve(kind);
        assertTrue(Files.isDirectory(dir), "Klasör yok: " + dir);
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
        }
        assertFalse(files.isEmpty(), "Test dosyası yok: " + dir);
        return files.stream();
    }

    private static Map<?, ?> load(Path file) {
        try {
            Object doc = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
            if (doc instanceof Map<?, ?> m) return m;
            throw new AssertionError(file + ": YAML nesnesi bekleniyordu");
        } catch (IOException e) {
            throw new AssertionError(file + " okunamadı", e);
        }
    }

    private static List<Map<?, ?>> maps(Object value) {
        if (value == null) return List.of();
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object o : (List<?>) value) {
            result.add((Map<?, ?>) o);
        }
        return result;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
