package com.ahmtcnmn.agent.service.IpService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * ufw komutlarını çalıştırır.
 * - "sudo -n": sudo şifre sormak yerine hemen hata verir (scheduler thread'i kilitlenmez).
 *   Sunucuda yalnız ufw için NOPASSWD izni gerekir: "<kullanıcı> ALL=(root) NOPASSWD: /usr/sbin/ufw"
 * - "insert 1": kural listenin EN BAŞINA eklenir. Düz "ufw deny" sona ekler ve önce gelen
 *   "allow 80/8080" gibi kurallar yüzünden engel hiç çalışmaz.
 */
@Component
public class UfwFirewall {

    // Yalnız IPv4/IPv6 karakterleri; komuta başka bir şey gitmesin
    private static final Pattern IP = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private static final boolean RUNNING_AS_ROOT = "root".equals(System.getProperty("user.name"));

    public record Result(int exitCode, String output) {
        public boolean ok() { return exitCode == 0; }
    }

    public Result block(String ip) throws IOException, InterruptedException {
        validate(ip);
        Result result = run("sudo", "-n", "ufw", "insert", "1", "deny", "from", ip);
        // Hiç kural yokken (veya IPv6'da) "insert 1" → "Invalid position" verir; o zaman sona ekle
        if (!result.ok() && result.output().contains("Invalid position")) {
            result = run("sudo", "-n", "ufw", "deny", "from", ip);
        }
        return result;
    }

    /**
     * IP'nin açık TCP bağlantılarını keser. ufw yalnız YENİ bağlantıları engeller; engelden önce
     * açılmış keep-alive (HTTP/2) bağlantısı ESTABLISHED kuralıyla geçmeye devam eder.
     * Kernel'de CONFIG_INET_DIAG_DESTROY gerekir (Ubuntu'da açık).
     */
    public Result killConnections(String ip) throws IOException, InterruptedException {
        validate(ip);
        return run("sudo", "-n", "ss", "-K", "dst", ip);
    }

    public Result unblock(String ip) throws IOException, InterruptedException {
        validate(ip);
        return run("sudo", "-n", "ufw", "delete", "deny", "from", ip);
    }

    private void validate(String ip) {
        if (ip == null || !IP.matcher(ip.trim()).matches()) {
            throw new IllegalArgumentException("Geçersiz IP: " + ip);
        }
    }

    private Result run(String... command) throws IOException, InterruptedException {
        List<String> args = new ArrayList<>(List.of(command));
        // root olarak çalışıyorsa sudo gereksiz; ayrıca sudo her çağrıda journal'a 3 satır yazıyor
        if (RUNNING_AS_ROOT && "sudo".equals(args.get(0))) {
            args.subList(0, 2).clear(); // "sudo", "-n"
        }
        Process process = new ProcessBuilder(args)
                .redirectErrorStream(true)
                .start();
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return new Result(-1, "zaman aşımı (15 sn)");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        return new Result(process.exitValue(), output);
    }
}
