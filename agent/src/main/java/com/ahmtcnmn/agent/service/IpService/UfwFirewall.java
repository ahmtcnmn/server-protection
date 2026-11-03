package com.ahmtcnmn.agent.service.IpService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * ufw komutlarını çalıştırır. Agent'ın eklediği her kural "sentinel:<ip>" yorumu taşır;
 * silme ve uzlaştırma (reconcile) yalnız bu yorumlu kurallara dokunur, yöneticinin elle
 * yazdığı kurallar asla silinmez.
 * - "sudo -n": sudo şifre sormak yerine hemen hata verir (scheduler thread'i kilitlenmez).
 *   Root değilse yalnız ufw ve ss için NOPASSWD izni gerekir.
 * - "insert 1": kural listenin EN BAŞINA eklenir. Düz "ufw deny" sona ekler ve önce gelen
 *   "allow 80/443" gibi kurallar yüzünden engel hiç çalışmaz.
 */
@Component
public class UfwFirewall {

    public static final String COMMENT_PREFIX = "sentinel:";

    // Yalnız IPv4/IPv6 karakterleri ve isteğe bağlı /prefix; komuta başka bir şey gitmesin
    private static final Pattern IP = Pattern.compile("^[0-9a-fA-F:.]{2,45}(/\\d{1,3})?$");

    // "[ 3] Anywhere   DENY IN   1.2.3.4   # sentinel:1.2.3.4"
    private static final Pattern NUMBERED_RULE = Pattern.compile("^\\[\\s*(\\d+)\\]\\s+(.*)$");
    private static final Pattern SENTINEL_COMMENT = Pattern.compile("#\\s*" + Pattern.quote(COMMENT_PREFIX) + "(\\S+)\\s*$");

    private static final boolean RUNNING_AS_ROOT = "root".equals(System.getProperty("user.name"));

    public record Result(int exitCode, String output) {
        public boolean ok() { return exitCode == 0; }
    }

    /** Ufw'deki bir sentinel kuralı: sıra numarası ve yorumdaki değer. */
    public record SentinelRule(int number, String value, boolean v6) {}

    public Result block(String value) throws IOException, InterruptedException {
        validate(value);
        String comment = COMMENT_PREFIX + value;
        Result result = run("sudo", "-n", "ufw", "insert", "1", "deny", "from", value, "comment", comment);
        if (!result.ok() && result.output().contains("Invalid position")) {
            // IPv6 kuralı v4 kurallarının önüne eklenemez → ilk v6 kuralının sırasına ekle;
            // hiç kural yoksa (ya da hiç v6 kuralı yoksa) sona eklemek güvenli
            Integer firstV6 = value.contains(":") ? firstV6RuleNumber() : null;
            result = firstV6 != null
                    ? run("sudo", "-n", "ufw", "insert", String.valueOf(firstV6), "deny", "from", value, "comment", comment)
                    : run("sudo", "-n", "ufw", "deny", "from", value, "comment", comment);
        }
        return result;
    }

    /** Bu değer için eklenmiş tüm sentinel kurallarını siler (v4 ve v6 kopyaları dahil). */
    public Result unblock(String value) throws IOException, InterruptedException {
        validate(value);
        List<Integer> numbers = listSentinelRules().stream()
                .filter(r -> r.value().equals(value))
                .map(SentinelRule::number)
                .sorted(Comparator.reverseOrder()) // sondan sil: öndeki numaralar kaymasın
                .toList();
        if (numbers.isEmpty()) {
            return new Result(0, "kural zaten yok");
        }
        StringBuilder out = new StringBuilder();
        for (int n : numbers) {
            Result r = run("sudo", "-n", "ufw", "--force", "delete", String.valueOf(n));
            if (!r.ok()) return r;
            out.append(r.output()).append(' ');
        }
        return new Result(0, out.toString().trim());
    }

    /** "sentinel:" yorumlu kurallar. ufw okunamazsa IOException fırlatır (uzlaştırma yanlış veriyle yapılmasın). */
    public List<SentinelRule> listSentinelRules() throws IOException, InterruptedException {
        Result status = run("sudo", "-n", "ufw", "status", "numbered");
        if (!status.ok()) {
            throw new IOException("ufw status okunamadı (exit " + status.exitCode() + "): " + status.output());
        }
        List<SentinelRule> rules = new ArrayList<>();
        for (String line : status.output().split("\\R")) {
            Matcher m = NUMBERED_RULE.matcher(line.trim());
            if (!m.matches()) continue;
            Matcher c = SENTINEL_COMMENT.matcher(m.group(2));
            if (c.find()) {
                rules.add(new SentinelRule(Integer.parseInt(m.group(1)), c.group(1), m.group(2).contains("(v6)")));
            }
        }
        return rules;
    }

    /** Değer başına kural sayısı (uzlaştırma için). */
    public Map<String, Integer> sentinelValues() throws IOException, InterruptedException {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (SentinelRule r : listSentinelRules()) {
            values.merge(r.value(), 1, Integer::sum);
        }
        return values;
    }

    /**
     * Hedefin açık TCP bağlantılarını keser. ufw yalnız YENİ bağlantıları engeller; engelden önce
     * açılmış keep-alive (HTTP/2) bağlantısı ESTABLISHED kuralıyla geçmeye devam eder.
     * Kernel'de CONFIG_INET_DIAG_DESTROY gerekir (Ubuntu'da açık).
     */
    public Result killConnections(String value) throws IOException, InterruptedException {
        validate(value);
        return run("sudo", "-n", "ss", "-K", "dst", value);
    }

    private Integer firstV6RuleNumber() throws IOException, InterruptedException {
        Result status = run("sudo", "-n", "ufw", "status", "numbered");
        for (String line : status.output().split("\\R")) {
            Matcher m = NUMBERED_RULE.matcher(line.trim());
            if (m.matches() && m.group(2).contains("(v6)")) {
                return Integer.parseInt(m.group(1));
            }
        }
        return null;
    }

    private void validate(String value) {
        if (value == null || !IP.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Geçersiz IP: " + value);
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
        // Çıktı ayrı thread'de okunur: uzun "ufw status" çıktısı pipe'ı doldurup süreci bekletmesin
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                return "";
            }
        });
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return new Result(-1, "zaman aşımı (15 sn)");
        }
        try {
            return new Result(process.exitValue(), output.get(5, TimeUnit.SECONDS));
        } catch (ExecutionException | TimeoutException e) {
            return new Result(process.exitValue(), "");
        }
    }
}
