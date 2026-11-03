# 🛡️ Obsidian Sentinel

Sunucu loglarını canlı izleyip saldırıları (SQL Injection vb.) tespit eden ve saldırganın IP'sini **otomatik olarak firewall'da (ufw) engelleyen** bir Manager–Agent güvenlik sistemi.

- **Agent:** İzlenen sunucuda çalışır. Logları okur, şüpheli satırı Manager'a bildirir, gelen komutları (ör. IP engelleme) uygular.
- **Manager:** Merkezdir. Event'leri saklar, eşik aşılınca engelleme kararı verir, panel için API sunar.
- **Panel:** Event'leri, Agent'ları, engelleri, allowlist'i ve komutları yönetmek için web arayüzü.

## Nasıl çalışır

```
Saldırgan ──► nginx ──► access.log ──► AGENT ── event ──► MANAGER (MySQL)
                                         ▲                   │ eşik aşıldı → Alert + Decision (ban)
                                         │                   ▼
                        ufw insert 1 deny ◄── decision stream (Agent 10 sn'de bir sorar)
```

1. Agent log satırını **parser** ile alanlara ayırır (kaynak IP, method, url, status...) ve **detector** kurallarını ilgili alana uygular (ör. SQLi yalnız URL-decode edilmiş `url` alanına bakar). Kurallar koddan bağımsız YAML dosyalarıdır (`rules/`).
2. Eşleşme olursa event'i kaynak IP, eşleşen kural ve yakalanan metinle birlikte Manager'a gönderir.
3. Manager aynı IP'den 60 sn içinde `security.sqli-threshold` kadar SQLi gelirse bir **alert** ve süreli bir **ban kararı (decision)** oluşturur (allowlist'teki IP'ler hariç). Panelden elle de ban verilebilir.
4. Agent'lar kararları **decision stream** ile çeker: IP'yi ufw kural listesinin **en başına** `sentinel:<ip>` yorumuyla ekler ve açık bağlantılarını keser (`ss -K`).
5. Süre dolunca (ya da panelden "Unblock" denince) Manager kararı kapatır, Agent'lar ufw kuralını kaldırır.

**Tasarım notları**
- **Polling:** Bağlantıyı her zaman Agent başlatır, Agent tarafında port açmak gerekmez.
- **Kuyruk:** Manager'a ulaşılamazsa event'ler Agent'taki H2 kuyruğunda bekler, bağlantı gelince gönderilir. Geç gelen bu event'ler kaydedilir ama ban eşiğine sayılmaz.
- **Kurallar YAML'da:** Yeni tespit kuralı için derleme gerekmez; Agent kural klasörünü dakikada bir kontrol eder, değişikliği yeniden başlatmadan uygular. Hatalı bir dosya gelirse eski set çalışmaya devam eder.
- **Kararların sahibi Manager:** Agent uyguladığı kararları H2'de tutar. Yeniden başlayınca ufw'yi önce bu kayıtla, Manager'a ulaşınca da Manager'ın listesiyle eşitler. Yalnız `sentinel:` yorumlu kurallara dokunur; elle yazılmış ufw kuralları silinmez.
- **Ban kapsamı:** Varsayılan `GLOBAL` (bir sunucuya saldıran IP tüm Agent'larda engellenir); elle ban tek bir Agent'a da verilebilir.
- **Allowlist:** Panelden süreli ya da süresiz IP/CIDR eklenir. Eklenen adresi kapsayan aktif banlar hemen kaldırılır; config'teki `security.whitelisted-ips` her zaman geçerlidir ve silinemez.
- **Kimlik doğrulama:** Agent ↔ Manager trafiği HTTPS + API key ile, panel JWT ile korunur. Agent yalnız Manager'ın kendi sertifikasına güvenir (`manager-cert.pem`).

## Teknolojiler

| Bileşen | Teknolojiler |
|---|---|
| Manager | Java 21, Spring Boot 4.1, Spring Security + JWT, MySQL, Bucket4j (rate limit) |
| Agent | Java 21, Spring Boot 4.1, Apache Commons IO (Tailer), H2, ufw |
| Panel | React 19, TypeScript, Vite, Tailwind CSS, Axios |

## Çalıştırma (yerel)

Gereksinimler: Java 21, MySQL, Node.js 18+.

```bash
# 0) Ortak kural motoru: Agent ve Manager'dan önce bir kez (engine değişince tekrar) kurulur
cd engine && ./mvnw install

# 1) Manager → https://localhost:8443
mysql -u root -p -e "CREATE DATABASE manager;"
cd manager && ./mvnw spring-boot:run

# 2) Panel → http://localhost:5173 (admin hesabıyla giriş)
cd frontend && npm install && npm run dev

# 3) Agent → test log dosyalarını izler, kuralları repo kökündeki rules/ klasöründen okur
touch /tmp/test-access.log /tmp/test-db.log /tmp/test-system.log
cd agent && ./mvnw spring-boot:run
```

## Tespit kuralları (`rules/`)

```
rules/parsers/     Log formatı → alanlar (regex named group), ör. nginx-access, raw-line
rules/detectors/   Kural: hangi parser, hangi alan, hangi desenler, eventType, severity
rules/tests/       Her kural için "yakalamalı / yakalamamalı" örnekleri
```

```yaml
# rules/detectors/sqli-basic.yaml (kısaltılmış)
name: sqli-basic
eventType: SQL_INJECTION
severity: 5
parsers: [nginx-access]
field: url               # yalnız istek URL'i (Referer/User-Agent değil)
transforms: [urlDecode]
patterns:
  - '(?i)union\s+(all\s+)?select'
```

Yeni kural eklerken `rules/tests/detectors/<kural>.yaml` dosyası da eklenir; testler `cd engine && ./mvnw test` ile çalışır (test dosyası olmayan kural testi kırar).

**Yeni Agent kaydı:** Panelde *Agents → Add agent*. Oluşan API key yalnız bir kez gösterilir; Agent'taki `manager.api-key` ayarına yazılır.

**Yerel test:** Agent'ın izlediği dosyaya bir saldırı satırı ekleyin:

```bash
echo '1.2.3.4 - - [01/Jan/2026:00:00:00 +0000] "GET /login?id=1-- HTTP/1.1" 200 0 "-" "curl"' >> /tmp/test-access.log
```

Agent bu satırı yakalar. Gerçek engelleme yalnızca ufw olan Linux sunucuda çalışır; Mac'te komut başarısız olur ve bu beklenen davranıştır.

## Önemli ayarlar

| Ayar | Nerede | Varsayılan | Açıklama |
|---|---|---|---|
| `security.sqli-threshold` | Manager | `3` | 60 sn'de kaç SQLi event'inden sonra engellenir |
| `security.whitelisted-ips` | Manager | `127.0.0.1,::1` | Silinemeyen allowlist girişleri; diğerleri panelden eklenir |
| `security.ban-duration` | Manager | `1h` | Otomatik ban süresi (`30s`, `5m`, `1h`) |
| `app.jwt.expirationMs` | Manager | `900000` | Access token süresi (15 dk); panel refresh token ile sessizce yeniler |
| `app.jwt.refresh-token-days` | Manager | `14` | Refresh token (httpOnly cookie) süresi |
| `manager.base-url` | Agent | `https://localhost:8443` | Manager adresi |
| `agent.log-file-path` | Agent | `/tmp/test-access.log` | İzlenen web access log'u |
| `spring.ssl.bundle.pem.manager.truststore.certificate` | Agent | `classpath:manager-cert.pem` | Güvenilen Manager sertifikası (Manager'ın keystore'u değişirse güncellenir) |
| `agent.rules-dir` | Agent | `../rules` | Kural klasörü (sunucuda `/opt/sentinel/rules`) |
| `agent.log-parsers` | Agent | `nginx-access` | Access log'un parser'ları (DB ve sistem logu için `agent.db-log-parsers`, `agent.process-parsers`: `raw-line`) |
| `agent.never-block` | Agent | `127.0.0.1,::1` | Manager istese bile Agent'ın asla engellemeyeceği IP'ler (son emniyet) |
| `agent.log.debug` | Agent | `false` | `true` olunca her log satırı ve HTTP detayları da görünür |

## Sunucuya kurulum (Agent)

Agent'ın gerçekten engelleme yapabilmesi için ufw olan bir Linux sunucuda çalışması gerekir.

1. Jar'ı üretin (`cd engine && ./mvnw install`, sonra `cd agent && ./mvnw package -DskipTests`) ve sunucuda `/opt/sentinel/agent.jar` olarak kopyalayın. `rules/` klasörünü de `/opt/sentinel/rules` olarak kopyalayın (kurallar jar'ın içinde değildir).
2. Aynı klasöre, jar'daki ayarları ezen bir `application.properties` koyun (`manager.base-url`, `agent.log-file-path` vb.).
3. Agent'ı root olarak, bir `systemd` servisi şeklinde çalıştırın. Root değil ayrı bir kullanıcıyla çalışacaksa yalnız ufw ve ss için sudo izni verin: `<kullanıcı> ALL=(root) NOPASSWD: /usr/sbin/ufw, /usr/bin/ss`.
4. Manager evdeki bilgisayarda (NAT arkasında) çalışıyorsa ters SSH tüneli açın: `ssh -N -R 8443:localhost:8443 <kullanıcı>@<sunucu>`.
5. Manager'ın sertifikası `CN=localhost` olduğu için `manager.base-url` `https://localhost:8443` kalmalıdır (tünel zaten bunu sağlar); başka bir adres hostname doğrulamasından geçmez.

## Proje yapısı

```
engine/     Kural motoru (saf Java) YAML okuyucu, parser, detector; Agent ve Manager kullanır
rules/      Tespit kuralları       parsers/, detectors/, tests/
agent/      Agent (Spring Boot)    detector/, watcher/, client/, service/IpService/
manager/    Manager (Spring Boot)  controller/, service/, security/, Search/
frontend/   Panel (React + TS)     pages/, api/, components/, hooks/
```

## Bilinen kısıtlar

- Yalnız URL'deki saldırılar tespit edilir. POST gövdesi access log'a yazılmadığı için form içindeki saldırılar görülmez.
