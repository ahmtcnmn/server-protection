package com.ahmtcnmn.agent.Libs;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

/**
 * Tek satırlık, renkli konsol logu.
 * Format: SAAT SEVİYE [ETİKET] mesaj
 * Örnek : 12:33:48 OK    [MANAGER]   Event iletildi: SQL_INJECTION (ip 10.0.0.5)
 */
public class ColorLogger {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    // agent.log.debug=true ise her log satırı ve HTTP detayları da yazılır
    private static volatile boolean debugEnabled = false;

    static {
        // Jansi'yi aktif ediyoruz
        AnsiConsole.systemInstall();
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void info(String tag, String message) {
        print(Ansi.ansi().fgBrightCyan(), "INFO ", tag, message);
    }

    public static void success(String tag, String message) {
        print(Ansi.ansi().fgBrightGreen(), "OK   ", tag, message);
    }

    public static void warn(String tag, String message) {
        print(Ansi.ansi().fgBrightYellow(), "UYARI", tag, message);
    }

    public static void error(String tag, String message) {
        print(Ansi.ansi().fgBrightRed(), "HATA ", tag, message);
    }

    public static void debug(String tag, String message) {
        if (!debugEnabled) return;
        print(Ansi.ansi().fgBrightBlack(), "DEBUG", tag, message);
    }

    /** Uzun log satırlarını ekrana sığacak şekilde kısaltır. */
    public static String cut(String text, int max) {
        if (text == null) return "-";
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max - 3) + "...";
    }

    private static void print(Ansi color, String level, String tag, String message) {
        String time = LocalTime.now().format(TIME);
        String label = String.format("%-11s", "[" + tag + "]");
        System.out.println(
                color.a(time).a(" ").a(level).a(" ").a(label).a(" ").a(message).reset()
        );
    }
}
