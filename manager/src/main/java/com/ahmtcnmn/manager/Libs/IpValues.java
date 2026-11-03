package com.ahmtcnmn.manager.Libs;

import java.util.Collection;
import java.util.regex.Pattern;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

/** Karar değerleri (IP ya da CIDR) için doğrulama ve eşleştirme. */
public final class IpValues {

    // Yalnız IP karakterleri: IpAddressMatcher'a hostname gitmesin (DNS sorgusu yapmasın)
    private static final Pattern IP_CHARS = Pattern.compile("^[0-9a-fA-F:.]{2,45}(/\\d{1,3})?$");

    // Bundan geniş aralıklar reddedilir: yanlışlıkla internetin büyük bölümünü banlamak olmasın
    private static final int MIN_PREFIX_V4 = 8;
    private static final int MIN_PREFIX_V6 = 32;

    private IpValues() {}

    /** Geçerliyse kırpılmış değeri döner, değilse IllegalArgumentException fırlatır. */
    public static String validate(String value) {
        String v = value == null ? "" : value.trim();
        if (!IP_CHARS.matcher(v).matches()) {
            throw new IllegalArgumentException("Geçersiz IP ya da CIDR: " + value);
        }
        boolean v6 = v.contains(":");
        if (!v6 && !v.matches("^(\\d{1,3}\\.){3}\\d{1,3}(/\\d{1,2})?$")) {
            throw new IllegalArgumentException("Geçersiz IPv4 adresi: " + value);
        }
        int slash = v.indexOf('/');
        if (slash >= 0) {
            int prefix = Integer.parseInt(v.substring(slash + 1));
            int max = v6 ? 128 : 32;
            int min = v6 ? MIN_PREFIX_V6 : MIN_PREFIX_V4;
            if (prefix > max) {
                throw new IllegalArgumentException("Geçersiz prefix: /" + prefix);
            }
            if (prefix < min) {
                throw new IllegalArgumentException("Aralık çok geniş (/" + prefix + "), en geniş /" + min + " kabul edilir");
            }
        }
        try {
            new IpAddressMatcher(v).matches(v6 ? "::1" : "127.0.0.1"); // ayrıştırılabiliyor mu
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Geçersiz IP ya da CIDR: " + value);
        }
        return v;
    }

    /**
     * Karar değeri whitelist'teki bir girişle çakışıyor mu? İki yönlü bakılır:
     * whitelist girişi kararı kapsıyor (10.0.0.0/8 ⊃ 10.1.2.3) ya da karar whitelist IP'sini kapsıyor (10.0.0.0/24 ∋ 10.0.0.5).
     */
    public static boolean overlapsAny(String value, Collection<String> whitelist) {
        for (String entry : whitelist) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            if (matches(e, hostOf(value)) || matches(value, hostOf(e))) {
                return true;
            }
        }
        return false;
    }

    private static String hostOf(String value) {
        int slash = value.indexOf('/');
        return slash >= 0 ? value.substring(0, slash) : value;
    }

    private static boolean matches(String cidrOrIp, String ip) {
        try {
            return new IpAddressMatcher(cidrOrIp).matches(ip);
        } catch (IllegalArgumentException e) {
            return false; // farklı aileler (v4/v6) ya da bozuk giriş
        }
    }
}
