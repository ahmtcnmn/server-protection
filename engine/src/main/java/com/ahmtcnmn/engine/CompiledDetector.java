package com.ahmtcnmn.engine;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ahmtcnmn.engine.spec.DetectorSpec;

/** Derlenmiş detector: belirli bir alana (ya da ham satıra) desenleri sırayla uygular, ilk eşleşmeyi döner. */
public final class CompiledDetector {

    /** YAML'daki transforms listesinde kullanılabilecek dönüşümler. */
    public enum Transform {
        URL_DECODE("urlDecode"),
        LOWERCASE("lowercase");

        private final String yamlName;

        Transform(String yamlName) {
            this.yamlName = yamlName;
        }

        public String yamlName() {
            return yamlName;
        }

        static Optional<Transform> fromYaml(String name) {
            for (Transform t : values()) {
                if (t.yamlName.equals(name)) return Optional.of(t);
            }
            return Optional.empty();
        }

        String apply(String value) {
            return switch (this) {
                case URL_DECODE -> urlDecode(value);
                case LOWERCASE -> value.toLowerCase(Locale.ROOT);
            };
        }

        private static String urlDecode(String value) {
            try {
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return value; // bozuk %-kodlaması: ham haliyle incele
            }
        }
    }

    private final DetectorSpec spec;
    private final List<Transform> transforms;
    private final List<Pattern> patterns;

    CompiledDetector(DetectorSpec spec, List<Transform> transforms, List<Pattern> patterns) {
        this.spec = spec;
        this.transforms = transforms;
        this.patterns = patterns;
    }

    public String name() {
        return spec.name();
    }

    public DetectorSpec spec() {
        return spec;
    }

    public boolean appliesTo(String parser) {
        return spec.parsers().contains(parser);
    }

    public Optional<Detection> detect(ParsedLine line) {
        String value = spec.field() == null ? line.raw() : line.field(spec.field());
        if (value == null) {
            return Optional.empty(); // alan bu satırda yok (opsiyonel grup eşleşmedi)
        }
        for (Transform t : transforms) {
            value = t.apply(value);
        }
        if (value.length() > spec.maxLength()) {
            value = value.substring(0, spec.maxLength());
        }
        for (Pattern p : patterns) {
            Matcher m = p.matcher(value);
            if (m.find()) {
                return Optional.of(new Detection(spec.name(), spec.eventType(), spec.severity(),
                        spec.field(), p.pattern(), m.group()));
            }
        }
        return Optional.empty();
    }
}
