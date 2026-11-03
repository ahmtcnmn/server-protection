package com.ahmtcnmn.engine.spec;

import java.util.Map;

/**
 * rules/parsers/*.yaml dosyasının ham hali.
 * regex: satırın tamamına uygulanır, alanlar named group olarak yakalanır (ör. (?<sourceIp>\S+)).
 * extract: ana regex'ten sonra satırda aranan ek alanlar; her değerin ilk grubu alan değeri olur.
 * timeFormat: "time" alanını Instant'a çevirmek için DateTimeFormatter deseni (opsiyonel).
 */
public record ParserSpec(String name, String description, String regex, Map<String, String> extract, String timeFormat) {
}
