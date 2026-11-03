package com.ahmtcnmn.engine;

/** Kural dosyası geçersiz: mesaj hangi dosyada ve neyin yanlış olduğunu söyler. Yarım kural seti hiç yüklenmez. */
public class RuleValidationException extends RuntimeException {

    public RuleValidationException(String message) {
        super(message);
    }

    public RuleValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
