package com.ahmtcnmn.engine;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ahmtcnmn.engine.spec.ParserSpec;

/** Derlenmiş parser: desenler kural yüklenirken bir kez derlenir, her satırda değil. */
public final class CompiledParser {

    private final ParserSpec spec;
    private final Pattern regex;
    private final Map<String, Pattern> extract;
    private final DateTimeFormatter timeFormat;
    private final Set<String> fieldNames;
    // namedGroups() sırasız bir Map döner; alanlar regex'teki sırayla yazılsın
    private final java.util.List<String> groupsInOrder;

    CompiledParser(ParserSpec spec, Pattern regex, Map<String, Pattern> extract, DateTimeFormatter timeFormat, Set<String> fieldNames) {
        this.spec = spec;
        this.regex = regex;
        this.extract = extract;
        this.timeFormat = timeFormat;
        this.fieldNames = fieldNames;
        this.groupsInOrder = regex.namedGroups().entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }

    public String name() {
        return spec.name();
    }

    public ParserSpec spec() {
        return spec;
    }

    /** Bu parser'ın üretebileceği alanlar (named group'lar + extract anahtarları). */
    public Set<String> fieldNames() {
        return fieldNames;
    }

    /** Satır bu formata uymuyorsa boş döner. */
    public Optional<ParsedLine> parse(String line) {
        Matcher m = regex.matcher(line);
        if (!m.find()) {
            return Optional.empty();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String group : groupsInOrder) {
            String value = m.group(group);
            if (value != null) {
                fields.put(group, value);
            }
        }
        for (Map.Entry<String, Pattern> e : extract.entrySet()) {
            if (fields.containsKey(e.getKey())) continue; // ana regex'in bulduğu değer önceliklidir
            Matcher em = e.getValue().matcher(line);
            if (em.find() && em.group(1) != null) {
                fields.put(e.getKey(), em.group(1));
            }
        }
        // Sıra korunur (satırdaki sırayla): panelde ve JSON'da okunaklı olsun
        return Optional.of(new ParsedLine(name(), line, Collections.unmodifiableMap(fields), parseTime(fields.get("time"))));
    }

    private Instant parseTime(String value) {
        if (timeFormat == null || value == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, timeFormat).toInstant();
        } catch (DateTimeParseException e) {
            return null; // zaman çözülemedi diye satır atılmaz
        }
    }
}
