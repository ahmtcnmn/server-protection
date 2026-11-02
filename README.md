# 🛡️ Obsidian Sentinel

Sunucu loglarını canlı izleyip saldırıları (SQL Injection vb.) tespit eden ve saldırganın IP'sini **otomatik olarak firewall'da (ufw) engelleyen** bir Manager–Agent güvenlik sistemi.

- **Agent:** İzlenen sunucuda çalışır. Logları okur, şüpheli satırı Manager'a bildirir, gelen komutları (ör. IP engelleme) uygular.
- **Manager:** Merkezdir. Event'leri saklar, eşik aşılınca engelleme kararı verir, panel için API sunar.
- **Panel:** Event'leri, Agent'ları ve komutları izlemek için web arayüzü.

## Nasıl çalışır

```
Saldırgan ──► nginx ──► access.log ──► AGENT ── event ──► MANAGER (MySQL)
                                         ▲                   │ eşik aşıldı mı?
                                         │                   ▼
                        ufw insert 1 deny ◄── BLOCK_IP komutu (Agent 10 sn'de bir sorar)
```

1. Agent log satırındaki istek URL'ini (URL-decode edilmiş halde) SQLi desenleriyle karşılaştırır.
2. Eşleşme olursa event'i kaynak IP ile birlikte Manager'a gönderir.
3. Manager aynı IP'den 60 sn içinde `security.sqli-threshold` kadar SQLi gelirse `BLOCK_IP` komutu üretir (whitelist'teki IP'ler hariç).
4. Agent komutu alır, IP'yi ufw kural listesinin **en başına** ekler ve açık bağlantılarını keser (`ss -K`).
5. `agent.block-duration` dolunca engel kendiliğinden kalkar.

**Tasarım notları**
- **Polling:** Bağlantıyı her zaman Agent başlatır, Agent tarafında port açmak gerekmez.
- **Kuyruk:** Manager'a ulaşılamazsa event'ler Agent'taki H2 kuyruğunda bekler, bağlantı gelince gönderilir.
- **Kimlik doğrulama:** Agent ↔ Manager trafiği HTTPS + API key ile, panel JWT ile korunur.

## Teknolojiler

| Bileşen | Teknolojiler |
|---|---|
| Manager | Java 21, Spring Boot 4.1, Spring Security + JWT, MySQL, Bucket4j (rate limit) |
| Agent | Java 21, Spring Boot 4.1, Apache Commons IO (Tailer), H2, ufw |
| Panel | React 19, TypeScript, Vite, Tailwind CSS, Axios |

## Çalıştırma (yerel)

Gereksinimler: Java 21, MySQL, Node.js 18+.

```bash
# 1) Manager → https://localhost:8443
mysql -u root -p -e "CREATE DATABASE manager;"
cd manager && ./mvnw spring-boot:run

# 2) Panel → http://localhost:5173 (admin hesabıyla giriş)
cd frontend && npm install && npm run dev

# 3) Agent → test log dosyalarını izler
touch /tmp/test-access.log /tmp/test-db.log /tmp/test-system.log
cd agent && ./mvnw spring-boot:run
```

**Yeni Agent kaydı:** `POST /api/agents/register` isteği `{"hostName": "..."}` gövdesiyle atılır. Dönen `apiKey` değeri Agent'taki `manager.api-key` ayarına yazılır.

**Yerel test:** Agent'ın izlediği dosyaya bir saldırı satırı ekleyin:

```bash
echo '1.2.3.4 - - [01/Jan/2026:00:00:00 +0000] "GET /login?id=1-- HTTP/1.1" 200 0 "-" "curl"' >> /tmp/test-access.log
```

Agent bu satırı yakalar. Gerçek engelleme yalnızca ufw olan Linux sunucuda çalışır; Mac'te komut başarısız olur ve bu beklenen davranıştır.

## Önemli ayarlar

| Ayar | Nerede | Varsayılan | Açıklama |
|---|---|---|---|
| `security.sqli-threshold` | Manager | `3` | 60 sn'de kaç SQLi event'inden sonra engellenir |
| `security.whitelisted-ips` | Manager | `127.0.0.1,::1` | Asla engellenmeyecek IP'ler |
| `app.jwt.expirationMs` | Manager | `900000` | Panel oturum süresi (15 dk) |
| `manager.base-url` | Agent | `https://localhost:8443` | Manager adresi |
| `agent.log-file-path` | Agent | `/tmp/test-access.log` | İzlenen web access log'u |
| `agent.block-duration` | Agent | `1h` | Engel süresi (`30s`, `5m`, `1h`) |
| `agent.unblock-check-ms` | Agent | `5000` | Süresi dolan engellerin kontrol aralığı |
| `agent.log.debug` | Agent | `false` | `true` olunca her log satırı ve HTTP detayları da görünür |

## Sunucuya kurulum (Agent)

Agent'ın gerçekten engelleme yapabilmesi için ufw olan bir Linux sunucuda çalışması gerekir.

1. Jar'ı üretin (`cd agent && ./mvnw package -DskipTests`) ve sunucuda `/opt/sentinel/agent.jar` olarak kopyalayın.
2. Aynı klasöre, jar'daki ayarları ezen bir `application.properties` koyun (`manager.base-url`, `agent.log-file-path` vb.).
3. Agent'ı root olarak, bir `systemd` servisi şeklinde çalıştırın. Root değil ayrı bir kullanıcıyla çalışacaksa yalnız ufw için sudo izni verin: `<kullanıcı> ALL=(root) NOPASSWD: /usr/sbin/ufw`.
4. Manager evdeki bilgisayarda (NAT arkasında) çalışıyorsa ters SSH tüneli açın: `ssh -N -R 8443:localhost:8443 <kullanıcı>@<sunucu>`.

## Proje yapısı

```
agent/      Agent (Spring Boot)    detector/, watcher/, client/, service/IpService/
manager/    Manager (Spring Boot)  controller/, service/, security/, Search/
frontend/   Panel (React + TS)     pages/, api/, components/, hooks/
```

## Bilinen kısıtlar

- Engel kayıtları Agent belleğinde tutulur. Agent süre dolmadan yeniden başlatılırsa ufw kuralı kendiliğinden kalkmaz; `ufw delete deny from <ip>` ile elle silinmelidir.
- Yalnız URL'deki saldırılar tespit edilir. POST gövdesi access log'a yazılmadığı için form içindeki saldırılar görülmez.
- Panelde token yenileme (refresh token) yoktur. Oturum süresi dolunca tekrar giriş istenir.
