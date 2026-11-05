package com.ahmtcnmn.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuleLoaderTest {

    /** Geçerli test setinden bir dosyayı değiştirip yüklemeyi dener; beklenen hata mesajını döner. */
    private static String errorWhen(Consumer<Map<String, String>> change) {
        Map<String, String> files = TestRules.files();
        change.accept(files);
        return assertThrows(RuleValidationException.class, () -> RuleLoader.fromFiles(files)).getMessage();
    }

    private static String detector(String body) {
        return "name: d\neventType: X_EVENT\nseverity: 3\nparsers: [nginx-access]\n" + body;
    }

    @Test
    void validSetLoads() {
        RuleSet rules = TestRules.ruleSet();
        assertEquals(2, rules.parsers().size());
        assertEquals(2, rules.detectors().size());
        assertEquals(64, rules.version().length());
    }

    @Test
    void invalidRegexIsRejectedWithFileName() {
        String msg = errorWhen(f -> f.put("detectors/bad.yaml", detector("patterns: ['(unclosed']")));
        assertTrue(msg.startsWith("detectors/bad.yaml"), msg);
        assertTrue(msg.contains("geçersiz regex"), msg);
    }

    @Test
    void unknownParserIsRejected() {
        String msg = errorWhen(f -> f.put("detectors/bad.yaml", detector("patterns: [x]").replace("nginx-access", "apache")));
        assertTrue(msg.contains("tanımsız parser 'apache'"), msg);
    }

    @Test
    void fieldMustBeProducedByEveryParser() {
        String msg = errorWhen(f -> f.put("detectors/bad.yaml", detector("field: query\npatterns: [x]")));
        assertTrue(msg.contains("'query' alanını üretmiyor"), msg);
    }

    @Test
    void unknownKeyIsRejected() {
        // yazım hatası sessizce yok sayılmasın: "pattern" yerine "patterns" olmalı
        String msg = errorWhen(f -> f.put("detectors/bad.yaml", detector("pattern: [x]")));
        assertTrue(msg.contains("bilinmeyen alan 'pattern'"), msg);
    }

    @Test
    void duplicateNamesAreRejected() {
        String msg = errorWhen(f -> f.put("detectors/copy.yaml", TestRules.SQLI));
        assertTrue(msg.contains("'sqli-test' adında başka bir detector var"), msg);
    }

    @Test
    void severityMustBeOneToFive() {
        String msg = errorWhen(f -> f.put("detectors/bad.yaml", detector("patterns: [x]").replace("severity: 3", "severity: 9")));
        assertTrue(msg.contains("severity 1 ile 5"), msg);
    }

    @Test
    void unknownTransformIsRejected() {
        String msg = errorWhen(f -> f.put("detectors/bad.yaml", detector("transforms: [base64]\npatterns: [x]")));
        assertTrue(msg.contains("bilinmeyen transform 'base64'"), msg);
    }

    @Test
    void javaObjectTagsAreRejected() {
        String msg = errorWhen(f -> f.put("detectors/bad.yaml", "!!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL [\"http://x\"]]]]"));
        assertTrue(msg.contains("YAML okunamadı"), msg);
    }

    @Test
    void timeFormatNeedsTimeField() {
        String msg = errorWhen(f -> f.put("parsers/raw-line.yaml", TestRules.RAW + "timeFormat: \"yyyy\"\n"));
        assertTrue(msg.contains("'time' alanı yok"), msg);
    }

    @Test
    void parserNeedsNamedGroup() {
        String msg = errorWhen(f -> f.put("parsers/raw-line.yaml", "name: raw-line\nregex: '^(.+)$'\n"));
        assertTrue(msg.contains("named group"), msg);
    }

    @Test
    void versionChangesWithContentButNotWithLineEndings() {
        Map<String, String> files = TestRules.files();
        String v1 = RuleLoader.fromFiles(files).version();
        files.replaceAll((k, v) -> v.replace("\n", "\r\n"));
        assertEquals(v1, RuleLoader.fromFiles(files).version());
        files.put("detectors/sqli-test.yaml", TestRules.SQLI.replace("severity: 5", "severity: 4"));
        assertNotEquals(v1, RuleLoader.fromFiles(files).version());
    }

    @Test
    void loadsFromDirectoryAndIgnoresOtherFolders(@TempDir Path dir) throws IOException {
        for (Map.Entry<String, String> e : TestRules.files().entrySet()) {
            Path p = dir.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue());
        }
        Files.createDirectories(dir.resolve("tests/sqli"));
        Files.writeString(dir.resolve("tests/sqli/expected.yaml"), "bu bir kural değil: [");
        Files.writeString(dir.resolve("detectors/README.txt"), "yok sayılır");

        RuleSet fromDir = RuleLoader.fromDirectory(dir);
        assertEquals(TestRules.ruleSet().version(), fromDir.version());
    }

    @Test
    void missingDirectoryIsReported(@TempDir Path dir) {
        assertThrows(RuleValidationException.class, () -> RuleLoader.fromDirectory(dir.resolve("yok")));
    }

    @Test
    void ruleFilePathMustStayInsideRulesFolders() {
        assertTrue(RuleLoader.isRuleFilePath("detectors/sqli-basic.yaml"));
        assertTrue(RuleLoader.isRuleFilePath("parsers/raw-line.yml"));
        for (String bad : new String[] {"../etc/cron.d/x.yaml", "detectors/../../x.yaml", "/etc/x.yaml",
                "detectors/sub/x.yaml", "tests/x.yaml", "detectors/x.sh", "detectors/.hidden.yaml", "detectors\\x.yaml"}) {
            org.junit.jupiter.api.Assertions.assertFalse(RuleLoader.isRuleFilePath(bad), bad);
        }
    }
}
