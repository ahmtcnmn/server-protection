# 🛡️ Obsidian Sentinel — Güvenlik İzleme ve Müdahale Sistemi

Ubuntu sunucularda çalışan **Agent**'lardan güvenlik olaylarını (SQL Injection, şüpheli sistem aktivitesi, veritabanı kimlik doğrulama hataları) gerçek zamanlı toplayan, bunları merkezi bir **Manager** üzerinde yöneten, otomatik tehdit tespiti ve IP engelleme yapabilen ve **web tabanlı bir dashboard** ile izleme imkânı sunan uçtan uca güvenlik izleme platformu.

## 📑 İçindekiler

- [Mimari Genel Bakış](#-mimari-genel-bakış)
- [Teknoloji Stack](#-teknoloji-stack)
- [Agent Modülü](#-agent-modülü)
- [Manager Modülü](#-manager-modülü)
- [Frontend (Dashboard)](#-frontend-dashboard)
- [Güvenlik Mekanizmaları](#-güvenlik-mekanizmaları)
- [Otomatik Tehdit Müdahale Akışı](#-otomatik-tehdit-müdahale-akışı)
- [API Endpoint'leri](#-api-endpointleri)
- [Veritabanı Şeması](#-veritabanı-şeması)
- [Kurulum ve Çalıştırma](#-kurulum-ve-çalıştırma)
- [Yapılandırma](#-yapılandırma)
- [Proje Yapısı](#-proje-yapısı)

---

## 🏗️ Mimari Genel Bakış

```
┌──────────────────────────────┐                         ┌──────────────────────────────┐
│   AGENT (Ubuntu VDS)         │                         │  MANAGER (Merkezi Sunucu)    │
│   [Spring Boot 4.1 / Java 21]│   HTTPS / REST          │  [Spring Boot 4.1 / Java 21] │
│                              │ ──────────────────────► │                              │
│  • Nginx/Apache log takibi   │   Heartbeat (30sn)      │  • REST API Controller'lar   │
│  • MySQL/PostgreSQL log      │   Event raporlama       │  • JPA + MySQL               │
│  • journalctl / syslog       │                         │  • Spring Security + JWT     │
│  • SQL Injection tespiti     │ ◄────────────────────── │  • Komut kuyruğu             │
│  • Offline olay kuyruğu     │   Komut polling (10sn)  │  • Rate Limiting (Bucket4j)  │
│  • IP engelleme (UFW)        │                         │  • Dashboard API             │
└──────────────────────────────┘                         └──────────────────────────────┘
                                                                      │
                                                                      │ REST API
                                                                      ▼
                                                         ┌──────────────────────────────┐
                                                         │  FRONTEND (Dashboard)        │
                                                         │  [React 19 + Vite + TS]      │
                                                         │                              │
                                                         │  • Gerçek zamanlı dashboard  │
                                                         │  • Agent yönetimi            │
                                                         │  • Event listeleme/filtreleme│
                                                         │  • Komut gönderme            │
                                                         └──────────────────────────────┘
```

**İletişim modeli:** Agent → Manager yönünde **polling** yapılır. Agent periyodik olarak Manager'a bağlanır; bu sayede Agent tarafında inbound port açma zorunluluğu ortadan kalkar.

---

## 🧰 Teknoloji Stack

| Katman | Teknoloji | Versiyon |
|--------|-----------|----------|
| **Backend** | Java | 21 (LTS) |
| **Framework** | Spring Boot | 4.1.0 |
| **Build** | Maven | — |
| **Manager DB** | MySQL | — |
| **Agent DB** | H2 (Embedded) | — |
| **Güvenlik** | Spring Security + JWT (jjwt) | 0.12.6 |
| **Rate Limiting** | Bucket4j | 8.10.1 |
| **Log İzleme** | Apache Commons IO (Tailer) | 2.16.1 |
| **Frontend** | React + TypeScript | 19.x |
| **Frontend Build** | Vite | 8.x |
| **Frontend CSS** | Tailwind CSS | 4.x |
| **HTTP Client (Frontend)** | Axios | 1.19.x |
| **Routing** | React Router DOM | 7.11 |

---

## 🕵️ Agent Modülü

Agent, izlenmek istenen sunuculara deploy edilen hafif bir Spring Boot uygulamasıdır. Temel görevi: **logları canlı izlemek**, **şüpheli aktiviteleri tespit etmek** ve bunları Manager'a raporlamaktır.

### Temel Bileşenler

#### 🔍 Detektörler (Detector Pattern)

Tüm detektörler `Detector` arayüzünü uygular:

```java
public interface Detector {
    boolean isSuspicious(String logLine);
    String eventType();
}
```

| Detektör | Event Tipi | Tespit Ettiği Kalıplar |
|----------|------------|------------------------|
| `SqliDetector` | `SQL_INJECTION` | `' OR 1=1`, `UNION SELECT`, SQL yorum satırları (`--`, `#`, `/*`), `DROP TABLE`, `xp_cmdshell`, `SLEEP()` |
| `DbAuthFailureDetector` | `DB_AUTH_FAILURE` | `access denied`, `authentication failed` |
| `SuspiciousSystemActivityDetector` | `SUSPICIOUS_SYSTEM_ACTIVITY` | `failed password`, `authentication failure`, `invalid user`, `session opened for user root`, `break-in attempt` |

#### 📡 Log İzleyiciler (Watcher)

| Watcher | Mekanizma | Kullanım |
|---------|-----------|----------|
| `FileLogWatcher` | Apache Commons IO `Tailer` ile dosya kuyruğu izleme | Nginx/Apache access log, DB log dosyaları |
| `ProcessLogWatcher` | `ProcessBuilder` ile harici süreç çıktısı izleme | `journalctl`, `syslog`, `tail -f` gibi komut çıktıları |

Her iki watcher da yapılandırma ile hangi detektörü kullanacağını bilir ve eşleşme durumunda otomatik olarak Manager'a event gönderir.

#### 💓 Heartbeat Mekanizması

- **30 saniyede** bir Manager'a heartbeat sinyali gönderir
- Manager bu sinyal ile Agent'ın çevrimiçi/çevrimdışı durumunu takip eder
- Heartbeat sırasında kuyrukta bekleyen (offline dönemde biriken) event'ler de gönderilir

#### 📋 Komut Polling

- **10 saniyede** bir Manager'dan bekleyen komutları sorgular
- Gelen komutları uygun `CommandExecutor` ile çalıştırır ve sonucu Manager'a raporlar

```java
public interface CommandExecutor {
    boolean supports(String commandType);
    String execute(String command) throws Exception;
}
```

#### 🚫 IP Engelleme

- `BlockIpCommandExecutor`: Manager'dan gelen `BLOCK_IP` komutunu `ufw deny from <IP>` ile çalıştırır
- `IpBlockManager`: Engellenen IP'leri hafızada tutar ve **1 saat sonra** otomatik olarak engeli kaldırır (`ufw delete deny from <IP>`)
- Engelleme sonrası Manager'a `IP_BLOCKED` event'i gönderir

#### 📦 Offline Kuyruk (Queue Service)

Manager'a ulaşılamadığı durumlarda event'ler **H2 embedded veritabanına** kaydedilir. Manager tekrar erişilebilir olduğunda (heartbeat başarılı olduğunda) kuyruk işlenir ve event'ler gönderilir.

---

## 🖥️ Manager Modülü

Manager, tüm Agent'lardan gelen verileri merkezi olarak toplayan, saklayan ve yönetim API'si sunan ana uygulamadır.

### Temel Bileşenler

#### 📊 Controller'lar

| Controller | Path | Görev |
|------------|------|-------|
| `AuthController` | `/api/auth` | JWT tabanlı kullanıcı girişi |
| `AgentController` | `/api/agents` | Agent kayıt, listeleme, heartbeat, komut gönderme |
| `EventController` | `/api/events` | Event oluşturma, listeleme, filtreleme |
| `CommandPollController` | `/api/commands` | Agent'ların bekleyen komutları sorgulaması ve sonuç raporlaması |
| `DashboardController` | `/api/dashboard` | Dashboard özet verileri ve event zaman çizelgesi |
| `HealtyController` | `/api/health` | Sağlık durumu kontrolü |

#### 🔧 Servisler

| Servis | Görev |
|--------|-------|
| `AgentService` | Agent kaydı (API key üretimi + SHA-256 hash), listeleme, Specification tabanlı filtreleme |
| `EventService` | Event oluşturma, filtreleme (agent, tip, severity, tarih aralığı, arama), **otomatik tehdit eşik kontrolü** |
| `CommandService` | Komut oluşturma, bekleyen komut listeleme, sonuç güncelleme |
| `DashboardService` | Dashboard KPI hesaplama (toplam agent, çevrimiçi agent, günlük event, kritik event), saatlik timeline |
| `CustomUserDetailsService` | Spring Security için kullanıcı detay servisi |
| `RateLimiterService` | Bucket4j tabanlı IP başına istek sınırlama |

#### 🔐 Güvenlik Katmanı

- **JWT Authentication**: Dashboard kullanıcıları için JWT token tabanlı kimlik doğrulama
- **API Key Authentication**: Agent'lar için SHA-256 ile hashlenmiş API key doğrulama
- **Rate Limiting**: Bucket4j ile IP başına istek sınırlama (login endpoint'i için özel sınırlama)
- **IP Whitelist**: Güvenilen IP'lerin otomatik engelleme dışında tutulması
- **HTTPS/TLS**: Manager PKCS12 keystore ile SSL üzerinden çalışır

#### ⚠️ Exception Handling

Merkezi hata yönetimi (`GlobalExceptionHandler`) ile aşağıdaki özel exception'lar tanımlıdır:

| Exception | Açıklama |
|-----------|----------|
| `AgentNotFoundException` | Agent bulunamadığında |
| `AgentOfflineException` | Çevrimdışı Agent'a komut gönderildiğinde |
| `DuplicateResourceException` | Aynı hostname ile tekrar kayıt denendiğinde |
| `CommandExecutionException` | Komut çalıştırma hatalarında |
| `ValidationException` | Doğrulama hatalarında |
| `ForbiddenException` | Yetkisiz erişimde |
| `UnauthorizedException` | Kimlik doğrulama başarısız olduğunda |

---

## 🖼️ Frontend (Dashboard)

React 19 + TypeScript + Tailwind CSS ile geliştirilmiş modern, karanlık temalı güvenlik izleme paneli.

### Sayfalar

| Sayfa | Route | Görev |
|-------|-------|-------|
| `DashboardPage` | `/` | Toplam agent, çevrimiçi agent, günlük event, kritik event KPI'ları; saatlik event timeline grafiği; agent durum listesi; son event'ler tablosu |
| `AgentsListPage` | `/agents` | Tüm agent'ların listelenmesi, arama ve filtreleme |
| `AgentDetailPage` | `/agents/:agentId` | Tek agent'ın detayları, event geçmişi, event tipi dağılımı, komut geçmişi, komut gönderme |
| `EventsListPage` | `/events` | Tüm event'lerin listelenmesi, çoklu filtre (agent, tip, severity, tarih aralığı, arama) |

### Bileşenler

- `Layout` — Sidebar + Topbar yerleşimi
- `Sidebar` — Navigasyon menüsü
- `Topbar` — Üst bilgi çubuğu
- `StatusDot` — Agent/event durumu göstergesi (online, offline, high-load, unresponsive)
- `SeverityBadge` — Severity seviyesi renk kodlaması

---

## 🔄 Otomatik Tehdit Müdahale Akışı

Sistem, SQL Injection saldırıları tespit edildiğinde **tamamen otomatik** bir müdahale zinciri çalıştırır:

```
1. [Agent] Nginx access log'da şüpheli SQL Injection kalıbı tespit edilir
       │
       ▼
2. [Agent] Event, Manager'a POST /api/events/create ile gönderilir
       │          (sourceIp log satırından regex ile çıkarılır)
       ▼
3. [Manager] Event veritabanına kaydedilir
       │
       ▼
4. [Manager] SQL_INJECTION event'i ise → checkThreatThreshold() çalışır
       │          Son 60 saniyede aynı IP'den ≥1 SQLi event'i var mı?
       │
       ├── IP whitelist'te → engellenmez, loglanır
       │
       └── IP whitelist'te değil → BLOCK_IP komutu oluşturulur
              │
              ▼
5. [Agent] 10sn içinde BLOCK_IP komutunu polling ile alır
       │
       ▼
6. [Agent] `ufw deny from <IP>` komutu çalıştırılır
       │
       ▼
7. [Agent] Engelleme sonucu Manager'a raporlanır + IP_BLOCKED event'i gönderilir
       │
       ▼
8. [Agent] IpBlockManager 1 saat sonra engeli otomatik kaldırır
```

---

## 📡 API Endpoint'leri

### Kimlik Doğrulama
| Method | Endpoint | Açıklama | Yetki |
|--------|----------|----------|-------|
| `POST` | `/api/auth/login` | JWT token al | Public |

### Agent Yönetimi
| Method | Endpoint | Açıklama | Yetki |
|--------|----------|----------|-------|
| `POST` | `/api/agents/register` | Yeni agent kaydı | Public |
| `POST` | `/api/agents/heartbeat` | Heartbeat sinyali | API Key |
| `GET` | `/api/agents` | Agent listesi (search, status, pageable) | JWT |
| `GET` | `/api/agents/{id}` | Tek agent detayı | JWT |
| `POST` | `/api/agents/{id}/commands` | Agent'a komut gönder | JWT |
| `GET` | `/api/agents/{id}/commands` | Komut geçmişi | JWT |

### Event Yönetimi
| Method | Endpoint | Açıklama | Yetki |
|--------|----------|----------|-------|
| `POST` | `/api/events/create` | Yeni event oluştur | API Key |
| `GET` | `/api/events` | Event listesi (çoklu filtre + pageable) | JWT |
| `GET` | `/api/events/{id}/events` | Agent'a ait event'ler | JWT |
| `GET` | `/api/events/{id}/event-type-breakdown` | Event tipi dağılımı | JWT |

### Komut Polling (Agent)
| Method | Endpoint | Açıklama | Yetki |
|--------|----------|----------|-------|
| `GET` | `/api/commands/pending` | Bekleyen komutları al | API Key |
| `POST` | `/api/commands/{id}/result` | Komut sonucunu raporla | API Key |

### Dashboard
| Method | Endpoint | Açıklama | Yetki |
|--------|----------|----------|-------|
| `GET` | `/api/dashboard/summary` | KPI özeti | JWT |
| `GET` | `/api/dashboard/events-timeline` | Saatlik event zaman çizelgesi | JWT |

---

## 🗄️ Veritabanı Şeması

### Manager (MySQL)

```
┌─────────────┐       ┌──────────────┐       ┌──────────────┐
│   agents     │       │   events     │       │   commands   │
├─────────────┤       ├──────────────┤       ├──────────────┤
│ id (PK)     │──┐    │ id (PK)      │       │ id (PK)      │
│ hostname    │  │    │ agent_id(FK) │◄──┐   │ agent_id(FK) │◄──┐
│ api_key_hash│  ├───►│ event_type   │   │   │ command_type │   │
│ last_seen   │  │    │ timestamp    │   │   │ payload      │   │
│ created_at  │  │    │ severity     │   │   │ status       │   │
└─────────────┘  │    │ raw_data     │   │   │ result       │   │
                 │    │ source_ip    │   │   │ created_at   │   │
                 │    └──────────────┘   │   │ delivered_at │   │
                 │                       │   │ completed_at │   │
                 └───────────────────────┘   └──────────────┘   │
                 └──────────────────────────────────────────────┘
```

### Agent (H2 Embedded — Offline Kuyruk)

```
┌──────────────────┐
│  pending_events  │
├──────────────────┤
│ id (PK)          │
│ uri              │
│ payload_json     │
│ created_at       │
└──────────────────┘
```

---

## 🚀 Kurulum ve Çalıştırma

### Gereksinimler

- **Java 21+**
- **Maven**
- **MySQL** (Manager için)
- **Node.js 18+** ve **npm** (Frontend için)

### 1. Manager

```bash
# MySQL veritabanı oluşturun
mysql -u root -p -e "CREATE DATABASE manager;"

# Manager'ı çalıştırın
cd manager
./mvnw spring-boot:run
```

Manager `https://localhost:8443` adresinde HTTPS üzerinden çalışır.

### 2. Agent

```bash
# İzlemek istediğiniz log dosyalarını oluşturun (test için)
touch /tmp/test-access.log
touch /tmp/test-db.log
touch /tmp/test-system.log

# Agent'ı çalıştırın
cd agent
./mvnw spring-boot:run
```

Agent `http://localhost:8091` adresinde çalışır.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend `http://localhost:5173` adresinde çalışır ve API isteklerini Vite proxy ile Manager'a yönlendirir.

---

## ⚙️ Yapılandırma

### Agent (`agent/src/main/resources/application.properties`)

| Ayar | Açıklama | Varsayılan |
|------|----------|------------|
| `server.port` | Agent port | `8091` |
| `manager.api-key` | Manager ile iletişim API anahtarı | — |
| `manager.base-url` | Manager adresi | `https://localhost:8443` |
| `agent.log-file-path` | Nginx/Apache log dosya yolu | `/tmp/test-access.log` |
| `agent.db-log-file-path` | Veritabanı log dosya yolu | `/tmp/test-db.log` |
| `agent.process-command` | Sistem log izleme komutu | `tail -f /tmp/test-system.log` |

### Manager (`manager/src/main/resources/application.properties`)

| Ayar | Açıklama | Varsayılan |
|------|----------|------------|
| `server.port` | Manager port | `8443` |
| `server.ssl.*` | HTTPS/TLS yapılandırması | PKCS12 keystore |
| `spring.datasource.*` | MySQL bağlantı bilgileri | `127.0.0.1:3306/manager` |
| `app.jwt.secretKey` | JWT imzalama anahtarı | — |
| `app.jwt.expirationMs` | JWT token süresi | `900000` (15dk) |
| `admin.initial-username` | Varsayılan admin kullanıcı | `admin` |
| `admin.initial-password` | Varsayılan admin şifresi | — |
| `security.whitelisted-ips` | Otomatik engelleme dışı IP'ler | `127.0.0.1,::1` |

---

## 📁 Proje Yapısı

```
manageragent/
├── agent/                              # Agent Spring Boot uygulaması
│   └── src/main/java/com/ahmtcnmn/agent/
│       ├── client/                     # HeartbeatTask, CommandPollTask
│       ├── config/                     # WatcherConfig, RestClientConfig
│       ├── detector/                   # SqliDetector, DbAuthFailureDetector, SuspiciousSystemActivityDetector
│       │   └── Interface/             # Detector, CommandExecutor arayüzleri
│       ├── Dto/                        # Request/Response DTO'ları
│       ├── model/                      # PendingEvent (offline kuyruk)
│       ├── repository/                 # PendingEventRepository
│       ├── service/                    # QueueService, ExtractSourceIp, MyRestClient
│       │   └── IpService/             # BlockIpCommandExecutor, IpBlockManager
│       └── watcher/                    # FileLogWatcher, ProcessLogWatcher
│
├── manager/                            # Manager Spring Boot uygulaması
│   └── src/main/java/com/ahmtcnmn/manager/
│       ├── common/                     # RestBaseController, GlobalExceptionHandler, Exceptions
│       ├── config/                     # JacksonConfig, WebConfig
│       ├── controller/                 # Auth, Agent, Event, Command, Dashboard, Health
│       ├── dto/                        # Request/Response DTO'ları
│       ├── model/                      # Agent, Events, Command, CommandStatus, User
│       ├── repository/                 # JPA Repository'ler
│       ├── Search/                     # JPA Specification tabanlı dinamik sorgular
│       ├── security/                   # SecurityConfig, JwtAuthFilter, EntryPoint
│       └── service/                    # AgentService, EventService, CommandService, DashboardService
│           ├── bucket4j/              # RateLimiterService, Interceptor'lar
│           └── User/                  # JwtUtil, CustomUserDetailsService
│
├── frontend/                           # React + TypeScript Dashboard
│   └── src/
│       ├── api/                        # Axios API client'ları
│       ├── components/                 # Layout, Sidebar, Topbar, StatusDot, SeverityBadge
│       ├── lib/                        # Yardımcı mapper fonksiyonları
│       ├── pages/                      # Dashboard, AgentsList, AgentDetail, EventsList
│       └── types/                      # TypeScript tip tanımları
│
└── RoadMap/                            # Geliştirme yol haritası ve kılavuzlar
    ├── Guvenlik_Sistemi_SpringBoot_Yol_Haritasi.md
    ├── Detayli_Uygulama_Kilavuzu.md
    ├── Exception_Katmani_Kullanim_Kilavuzu.md
    ├── Exception_Katmani_Rehberi.md
    └── Veritabani_Iliskileri_Ornekler.md
```

