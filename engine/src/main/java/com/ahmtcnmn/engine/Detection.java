package com.ahmtcnmn.engine;

/**
 * Bir detector'ın bir satırda bulduğu eşleşme.
 * field: incelenen alan (null → ham satır); matched: desenin yakaladığı metin (dönüşümlerden sonra).
 */
public record Detection(String rule, String eventType, int severity, String field, String pattern, String matched) {
}
