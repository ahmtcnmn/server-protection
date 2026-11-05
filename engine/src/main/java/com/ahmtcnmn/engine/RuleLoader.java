package com.ahmtcnmn.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import com.ahmtcnmn.engine.CompiledDetector.Transform;
import com.ahmtcnmn.engine.spec.DetectorSpec;
import com.ahmtcnmn.engine.spec.ParserSpec;

/**
 * Kural dosyalarını okur, doğrular ve derler. Herhangi bir dosya geçersizse RuleValidationException fırlatır;
 * kısmi set dönmez (çalışan eski set yerinde kalsın).
 *
 * Klasör düzeni: parsers/*.yaml ve detectors/*.yaml. Diğer klasörler (ör. tests/) yok sayılır.
 */
public final class RuleLoader {

    public static final String PARSERS_DIR = "parsers/";
    public static final String DETECTORS_DIR = "detectors/";
    public static final int DEFAULT_MAX_LENGTH = 4096;

    // Agent'ın diske yazacağı yollar: klasör dışına çıkamaz (../, mutlak yol, alt klasör yok)
    private static final Pattern SAFE_PATH = Pattern.compile("^(parsers|detectors)/[A-Za-z0-9][A-Za-z0-9._-]{0,100}\\.ya?ml$");
    private static final Pattern NAME = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
    private static final Pattern EVENT_TYPE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    // Java named group kuralı: harfle başlar, yalnız harf ve rakam
    private static final Pattern FIELD = Pattern.compile("^[a-zA-Z][a-zA-Z0-9]{0,63}$");

    private static final Set<String> PARSER_KEYS = Set.of("name", "description", "regex", "extract", "timeFormat");
    private static final Set<String> DETECTOR_KEYS = Set.of("name", "description", "eventType", "severity",
            "parsers", "field", "transforms", "maxLength", "patterns");

    private RuleLoader() {}

    /** rulesDir/parsers ve rulesDir/detectors altındaki .yaml/.yml dosyalarını yükler. */
    public static RuleSet fromDirectory(Path rulesDir) {
        return fromFiles(readDirectory(rulesDir));
    }

    /**
     * Kural dosyalarını okur (doğrulamadan): "parsers/x.yaml" → içerik. Manager bu haritayı Agent'lara olduğu gibi
     * gönderir; Agent fromFiles ile aynı seti (aynı sürümle) kurar.
     */
    public static Map<String, String> readDirectory(Path rulesDir) {
        if (!Files.isDirectory(rulesDir)) {
            throw new RuleValidationException("Kural klasörü bulunamadı: " + rulesDir.toAbsolutePath());
        }
        Map<String, String> files = new TreeMap<>();
        for (String sub : List.of(PARSERS_DIR, DETECTORS_DIR)) {
            Path dir = rulesDir.resolve(sub);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> paths = Files.list(dir)) {
                for (Path p : paths.filter(RuleLoader::isYaml).sorted().toList()) {
                    files.put(sub + p.getFileName(), Files.readString(p, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Kural klasörü okunamadı: " + dir, e);
            }
        }
        return files;
    }

    /** Kural dosyası yolu güvenli mi: yalnız parsers/ ya da detectors/ altında, düz bir .yaml/.yml dosya adı. */
    public static boolean isRuleFilePath(String path) {
        return SAFE_PATH.matcher(path).matches();
    }

    /**
     * Dosya yolu → içerik. Yol "parsers/x.yaml" ya da "detectors/y.yaml" biçiminde olmalı; diğerleri yok sayılır.
     * (Manager'dan gelen kural seti de bu yolla yüklenir.)
     */
    public static RuleSet fromFiles(Map<String, String> files) {
        Map<String, String> sorted = new TreeMap<>();
        files.forEach((path, content) -> {
            String p = path.replace('\\', '/');
            if ((p.startsWith(PARSERS_DIR) || p.startsWith(DETECTORS_DIR)) && isYaml(Path.of(p))) {
                sorted.put(p, content);
            }
        });

        Map<String, CompiledParser> parsers = new LinkedHashMap<>();
        List<Map.Entry<String, String>> detectorFiles = new ArrayList<>();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (e.getKey().startsWith(PARSERS_DIR)) {
                CompiledParser parser = compileParser(e.getKey(), e.getValue());
                if (parsers.putIfAbsent(parser.name(), parser) != null) {
                    throw new RuleValidationException(e.getKey() + ": '" + parser.name() + "' adında başka bir parser var");
                }
            } else {
                detectorFiles.add(e);
            }
        }

        List<CompiledDetector> detectors = new ArrayList<>();
        Set<String> detectorNames = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : detectorFiles) {
            CompiledDetector detector = compileDetector(e.getKey(), e.getValue(), parsers);
            if (!detectorNames.add(detector.name())) {
                throw new RuleValidationException(e.getKey() + ": '" + detector.name() + "' adında başka bir detector var");
            }
            detectors.add(detector);
        }
        return new RuleSet(parsers, detectors, version(sorted));
    }

    // ---------------------------------------------------------------- parser

    private static CompiledParser compileParser(String file, String content) {
        Fields f = new Fields(file, loadYaml(file, content), PARSER_KEYS);
        String name = f.name();
        String regexSource = f.requiredString("regex");
        Pattern regex = compile(file, "regex", regexSource);

        Set<String> fieldNames = new LinkedHashSet<>(regex.namedGroups().keySet());
        if (fieldNames.isEmpty()) {
            throw new RuleValidationException(file + ": regex en az bir named group içermeli, ör. (?<sourceIp>\\S+)");
        }

        Map<String, Pattern> extract = new LinkedHashMap<>();
        Map<String, String> extractSpec = f.stringMap("extract");
        for (Map.Entry<String, String> e : extractSpec.entrySet()) {
            checkField(file, "extract", e.getKey());
            Pattern p = compile(file, "extract." + e.getKey(), e.getValue());
            if (p.matcher("").groupCount() < 1) {
                throw new RuleValidationException(file + ": extract." + e.getKey() + " en az bir grup içermeli; ilk grup alan değeri olur");
            }
            extract.put(e.getKey(), p);
            fieldNames.add(e.getKey());
        }

        String timeFormatSource = f.optionalString("timeFormat");
        DateTimeFormatter timeFormat = null;
        if (timeFormatSource != null) {
            if (!fieldNames.contains("time")) {
                throw new RuleValidationException(file + ": timeFormat verilmiş ama 'time' alanı yok");
            }
            try {
                timeFormat = DateTimeFormatter.ofPattern(timeFormatSource, Locale.ENGLISH);
            } catch (IllegalArgumentException e) {
                throw new RuleValidationException(file + ": geçersiz timeFormat '" + timeFormatSource + "': " + e.getMessage());
            }
        }

        ParserSpec spec = new ParserSpec(name, f.optionalString("description"), regexSource, extractSpec, timeFormatSource);
        return new CompiledParser(spec, regex, Map.copyOf(extract), timeFormat, Set.copyOf(fieldNames));
    }

    // ---------------------------------------------------------------- detector

    private static CompiledDetector compileDetector(String file, String content, Map<String, CompiledParser> parsers) {
        Fields f = new Fields(file, loadYaml(file, content), DETECTOR_KEYS);
        String name = f.name();

        String eventType = f.requiredString("eventType");
        if (!EVENT_TYPE.matcher(eventType).matches()) {
            throw new RuleValidationException(file + ": eventType büyük harf ve alt çizgi olmalı (ör. SQL_INJECTION): " + eventType);
        }

        int severity = f.integer("severity", null);
        if (severity < 1 || severity > 5) {
            throw new RuleValidationException(file + ": severity 1 ile 5 arasında olmalı: " + severity);
        }

        List<String> parserNames = f.stringList("parsers");
        if (parserNames.isEmpty()) {
            throw new RuleValidationException(file + ": parsers boş olamaz");
        }
        String field = f.optionalString("field");
        for (String p : parserNames) {
            CompiledParser parser = parsers.get(p);
            if (parser == null) {
                throw new RuleValidationException(file + ": tanımsız parser '" + p + "'");
            }
            if (field != null && !parser.fieldNames().contains(field)) {
                throw new RuleValidationException(file + ": '" + p + "' parser'ı '" + field + "' alanını üretmiyor "
                        + "(var olanlar: " + parser.fieldNames() + ")");
            }
        }

        List<Transform> transforms = new ArrayList<>();
        for (String t : f.stringList("transforms")) {
            transforms.add(Transform.fromYaml(t).orElseThrow(() -> new RuleValidationException(
                    file + ": bilinmeyen transform '" + t + "' (urlDecode, lowercase)")));
        }

        int maxLength = f.integer("maxLength", DEFAULT_MAX_LENGTH);
        if (maxLength < 16 || maxLength > 65536) {
            throw new RuleValidationException(file + ": maxLength 16 ile 65536 arasında olmalı: " + maxLength);
        }

        List<String> patternSources = f.stringList("patterns");
        if (patternSources.isEmpty()) {
            throw new RuleValidationException(file + ": patterns boş olamaz");
        }
        List<Pattern> patterns = new ArrayList<>();
        for (int i = 0; i < patternSources.size(); i++) {
            patterns.add(compile(file, "patterns[" + i + "]", patternSources.get(i)));
        }

        DetectorSpec spec = new DetectorSpec(name, f.optionalString("description"), eventType, severity,
                List.copyOf(parserNames), field, f.stringList("transforms"), maxLength, List.copyOf(patternSources));
        return new CompiledDetector(spec, List.copyOf(transforms), List.copyOf(patterns));
    }

    // ---------------------------------------------------------------- yardımcılar

    private static boolean isYaml(Path p) {
        String n = p.getFileName().toString();
        return n.endsWith(".yaml") || n.endsWith(".yml");
    }

    private static Pattern compile(String file, String key, String source) {
        try {
            return Pattern.compile(source);
        } catch (PatternSyntaxException e) {
            throw new RuleValidationException(file + ": " + key + " geçersiz regex: " + e.getDescription() + " → " + source);
        }
    }

    private static void checkField(String file, String key, String field) {
        if (!FIELD.matcher(field).matches()) {
            throw new RuleValidationException(file + ": " + key + " alan adı harfle başlamalı ve yalnız harf/rakam içermeli: " + field);
        }
    }

    /** Güvenli YAML: yalnız düz map/list/string/sayı; Java nesnesi oluşturan etiketler (!!java...) reddedilir. */
    private static Map<?, ?> loadYaml(String file, String content) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(10);
        options.setCodePointLimit(1024 * 1024);
        Object doc;
        try {
            doc = new Yaml(new SafeConstructor(options)).load(content);
        } catch (YAMLException e) {
            throw new RuleValidationException(file + ": YAML okunamadı: " + e.getMessage(), e);
        }
        if (!(doc instanceof Map<?, ?> map)) {
            throw new RuleValidationException(file + ": dosya bir YAML nesnesi (anahtar: değer) olmalı");
        }
        return map;
    }

    private static String version(Map<String, String> sortedFiles) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sortedFiles.forEach((path, content) -> {
                sha.update(path.getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
                sha.update(content.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
            });
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** YAML map'inden tip kontrollü okuma; bilinmeyen anahtar (yazım hatası) reddedilir. */
    private static final class Fields {
        private final String file;
        private final Map<?, ?> map;

        Fields(String file, Map<?, ?> map, Set<String> allowed) {
            this.file = file;
            this.map = map;
            for (Object key : map.keySet()) {
                if (!(key instanceof String k) || !allowed.contains(k)) {
                    throw new RuleValidationException(file + ": bilinmeyen alan '" + key + "' (geçerli alanlar: " + new java.util.TreeSet<>(allowed) + ")");
                }
            }
        }

        String name() {
            String name = requiredString("name");
            if (!NAME.matcher(name).matches()) {
                throw new RuleValidationException(file + ": name küçük harf, rakam ve tire olmalı: " + name);
            }
            return name;
        }

        String requiredString(String key) {
            String v = optionalString(key);
            if (v == null) {
                throw new RuleValidationException(file + ": '" + key + "' zorunlu");
            }
            return v;
        }

        String optionalString(String key) {
            Object v = map.get(key);
            if (v == null) return null;
            if (!(v instanceof String s)) {
                throw new RuleValidationException(file + ": '" + key + "' metin olmalı");
            }
            return s.isBlank() ? null : s;
        }

        int integer(String key, Integer defaultValue) {
            Object v = map.get(key);
            if (v == null) {
                if (defaultValue == null) throw new RuleValidationException(file + ": '" + key + "' zorunlu");
                return defaultValue;
            }
            if (!(v instanceof Integer i)) {
                throw new RuleValidationException(file + ": '" + key + "' tam sayı olmalı");
            }
            return i;
        }

        List<String> stringList(String key) {
            Object v = map.get(key);
            if (v == null) return List.of();
            if (!(v instanceof List<?> list)) {
                throw new RuleValidationException(file + ": '" + key + "' liste olmalı");
            }
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof String s) || s.isEmpty()) {
                    throw new RuleValidationException(file + ": '" + key + "' yalnız boş olmayan metinler içermeli");
                }
                result.add(s);
            }
            return result;
        }

        Map<String, String> stringMap(String key) {
            Object v = map.get(key);
            if (v == null) return Map.of();
            if (!(v instanceof Map<?, ?> m)) {
                throw new RuleValidationException(file + ": '" + key + "' anahtar: değer listesi olmalı");
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!(e.getKey() instanceof String k) || !(e.getValue() instanceof String val)) {
                    throw new RuleValidationException(file + ": '" + key + "' içindeki anahtar ve değerler metin olmalı");
                }
                result.put(k, val);
            }
            return result;
        }
    }
}
