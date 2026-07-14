# Borsa Portfolio Uygulaması — Öğrenme Odaklı Proje Planı

> **Amaç:** Bu proje bir "portfolio yönetim uygulaması" gibi görünse de asıl hedef **Java ve Spring Boot'u,
bankacılık/finans şirketlerinde karşılaşılan gerçek dünya problemleriyle öğrenmek.** Domain (hisse alım-satımı) sadece
> bir bahane; asıl kazanım concurrency, caching, scheduling, auditing, security gibi konularda pratik yapmak.

---

## 1. Varsayımlar (Assumptions)

Aşağıdakileri netleştirilmemiş kabul edip varsayım olarak işaretliyorum — yanlışsa söyle, düzeltelim:

| Konu            | Varsayım                                                                                                                                                                       |
|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Stock price API | **Finnhub** (ücretsiz tier, gerçek zamanlıya yakın veri, kolay REST API). Alpha Vantage veya Twelve Data'ya geçmek istersen sadece `StockPriceClient` implementasyonu değişir. |
| Para birimi     | Tek para birimi (USD veya TRY), çoklu döviz desteği yok (scope dışı).                                                                                                          |
| Sanal bakiye    | Her kullanıcı kayıt olduğunda sabit bir başlangıç bakiyesi alır (örn. 100.000 birim sanal para).                                                                               |
| Deployment      | Local geliştirme (Docker Compose ile Postgres + Redis), production deployment şimdilik kapsam dışı.                                                                            |

---

## 2. Teknoloji Stack'i (Zaten pom.xml'de kurulu)

- **Java 21**, **Spring Boot 4.1.0**, Maven
- `spring-boot-starter-webmvc` — REST API
- `spring-boot-starter-data-jpa` + `postgresql` — kalıcı veri
- `spring-boot-starter-security` + `jjwt-*` (0.12.6) — JWT auth (access + refresh token)
- `spring-boot-starter-data-redis` + `spring-boot-starter-cache` — caching
- `spring-boot-starter-aspectj` — AOP (auditing/logging için, eski adıyla "aop starter")
- `spring-boot-starter-validation` — request validasyonu
- `lombok`, `spring-boot-devtools`
- `spring-boot-starter-liquibase` — veritabanı şema migration yönetimi (Hibernate `ddl-auto=update` yerine)

**Not:** `@Scheduled`/`@Async` için ekstra dependency gerekmiyor, `spring-context` zaten dahil.

---

## 3. Öğrenme Fazları (Roadmap)

Her faz bir öncekinin üzerine inşa edilir. Sırayı bozmadan ilerlemen öneriliyor — her faz kendi başına test edilebilir
bir kazanım.

### Faz 1 — Temel CRUD, katmanlı mimari ve Liquibase migration

- Entity → Repository → Service → Controller → DTO akışını kur
- `Stock`, `User` (auth olmadan basit) entity'leri
- Hibernate `ddl-auto=update` yerine Liquibase changelog'ları ile şema yönetimi (
  `db/changelog/db.changelog-master.yaml` + ilk tabloları oluşturan changeset'ler)
- **Verify:** `GET /api/stocks` çalışıyor, Postman/curl ile test edilebiliyor; uygulama başlarken Liquibase log'da
  changeset'lerin uygulandığını gör

### Faz 2 — Security & JWT Auth

- Register/login, access + refresh token akışı
- Role-based authorization (`USER`, `ADMIN`)
- **Verify:** Token olmadan korumalı endpoint 401 dönüyor, login sonrası token ile 200 dönüyor

### Faz 3 — External API Entegrasyonu

- Finnhub'dan gerçek fiyat çekme (`RestClient` ile, ekstra dependency gerekmez)
- Hata yönetimi: API timeout, rate limit, geçersiz sembol
- **Verify:** Gerçek bir sembol için güncel fiyat dönüyor, geçersiz sembol için anlamlı hata dönüyor

### Faz 4 — Redis Caching

- Fiyat verisini cache'leme (`@Cacheable`, TTL ile)
- Cache eviction stratejisi
- **Verify:** Aynı sembole art arda istek atınca ikinci istek external API'ye gitmiyor (log ile doğrula)

### Faz 5 — Concurrency & Transaction Yönetimi (bankacılığın kalbi)

- Alım-satım işlemlerinde race condition senaryosu: aynı anda iki istek gelirse bakiye/holding tutarsız olabilir
- `@Version` ile optimistic locking (Wallet, Holding entity'lerinde)
- Kritik bakiye güncellemelerinde pessimistic locking (`@Lock(PESSIMISTIC_WRITE)`) alternatifi
- `@Transactional` isolation level tartışması
- **Verify:** Çoklu thread ile aynı anda satın alma testi yaz, bakiyenin negatife düşmediğini/tutarlı kaldığını doğrula

### Faz 6 — Scheduling & Async

- `@Scheduled` ile periyodik fiyat güncelleme job'ı (örn. her 1 dakikada aktif holding'lerin fiyatlarını yenile)
- `@Async` ile işlem sonrası bildirim gönderme (simülasyon, gerçek email şart değil)
- **Verify:** Job'ın loglarda periyodik çalıştığını gör, async metodun ana thread'i bloklamadığını doğrula

### Faz 7 — Auditing & Logging (AOP)

- Custom `@Audited` annotation + `@Around` aspect
- Her para hareketi (`buy`/`sell`/`deposit`) `AuditLog` tablosuna kim/ne zaman/ne değişti şeklinde kaydediliyor
- **Verify:** Bir alım işlemi sonrası `audit_log` tablosunda kayıt oluştuğunu gör

### Faz 8 — Hata Yönetimi & Test

- Global `@ControllerAdvice` + `ProblemDetail` (RFC 7807)
- Unit test (Mockito), integration test (Testcontainers ile gerçek Postgres/Redis)
- **Verify:** Test suite yeşil, hatalı senaryolar anlamlı HTTP status/body dönüyor

### Faz 9 — (Opsiyonel, ileri seviye)

- Docker Compose ile tüm stack'i ayağa kaldırma
- `spring-boot-starter-actuator` ile health check/metrics
- Kafka/RabbitMQ ile event-driven mimariye geçiş (gerçekten istersen)
- **Domain-Driven Design (DDD)'ye geçiş:** Şu anki layer-based paket yapısından (`entity`/`service`/`controller`/`dto`/
  `mapper`) feature-based'e geçip, DDD kavramlarını (Aggregate/Aggregate Root, Value Object, Domain vs Application
  Service, Bounded Context, Ubiquitous Language) uygulamaya çalışmak — örn. `Portfolio`'yu bir aggregate root yapıp
  `Holding`'lerin dışarıdan direkt değiştirilemeyeceği bir tutarlılık sınırı çizmek. Sadece klasör organizasyonu değil,
  gerçek bir tasarım değişikliği; projenin geri kalanı (Faz 1-8) oturduktan sonra yapılması daha sağlıklı

---

## 4. Domain Model (Entities)

| Entity         | Alanlar (özet)                                                                    | İlişki                      |
|----------------|-----------------------------------------------------------------------------------|-----------------------------|
| `User`         | id, email, passwordHash, role, walletBalance, createdAt                           | 1—1 Wallet, 1—N Transaction |
| `RefreshToken` | id, token, userId, expiryDate, revoked                                            | N—1 User                    |
| `Wallet`       | id, userId, balance, `@Version`                                                   | 1—1 User                    |
| `Stock`        | id, symbol, name, lastPrice, lastUpdated                                          | 1—N Holding                 |
| `Holding`      | id, userId, stockId, quantity, avgCost, `@Version`                                | N—1 User, N—1 Stock         |
| `Transaction`  | id, userId, stockId, type (BUY/SELL), quantity, price, timestamp                  | N—1 User, N—1 Stock         |
| `AuditLog`     | id, actorUserId, action, entityType, entityId, beforeState, afterState, timestamp | —                           |

---

## 5. API Endpoints

### Auth (`/api/auth`) — public

| Method | Path                 | Açıklama                                                  |
|--------|----------------------|-----------------------------------------------------------|
| POST   | `/api/auth/register` | Yeni kullanıcı kaydı (başlangıç bakiyesi otomatik atanır) |
| POST   | `/api/auth/login`    | Access + refresh token döner                              |
| POST   | `/api/auth/refresh`  | Refresh token ile yeni access token                       |
| POST   | `/api/auth/logout`   | Refresh token'ı invalidate eder (authenticated)           |

### Kullanıcı (`/api/users`) — authenticated

| Method | Path            | Açıklama          |
|--------|-----------------|-------------------|
| GET    | `/api/users/me` | Profil bilgisi    |
| PUT    | `/api/users/me` | Profil güncelleme |

### Cüzdan (`/api/wallet`) — authenticated

| Method | Path                  | Açıklama                                        |
|--------|-----------------------|-------------------------------------------------|
| GET    | `/api/wallet`         | Güncel bakiye                                   |
| POST   | `/api/wallet/deposit` | Sanal para yatırma (test amaçlı, prod'da olmaz) |

### Hisseler (`/api/stocks`) — authenticated

| Method | Path                   | Açıklama                                               |
|--------|------------------------|--------------------------------------------------------|
| GET    | `/api/stocks`          | Aranabilir/listelenen hisseler                         |
| GET    | `/api/stocks/{symbol}` | Güncel fiyat (Redis cache'den, yoksa external API'den) |

### Portfolio (`/api/portfolio`) — authenticated

| Method | Path                               | Açıklama                                   |
|--------|------------------------------------|--------------------------------------------|
| GET    | `/api/portfolio`                   | Tüm holding'ler + toplam değer + kâr/zarar |
| GET    | `/api/portfolio/holdings/{symbol}` | Tek holding detay                          |

### İşlemler (`/api/transactions`) — authenticated

| Method | Path                     | Açıklama                             |
|--------|--------------------------|--------------------------------------|
| POST   | `/api/transactions/buy`  | `{ symbol, quantity }` — sanal alım  |
| POST   | `/api/transactions/sell` | `{ symbol, quantity }` — sanal satım |
| GET    | `/api/transactions`      | İşlem geçmişi (pagination)           |
| GET    | `/api/transactions/{id}` | Tek işlem detay                      |

### Admin (`/api/admin`) — ADMIN rolü

| Method | Path                    | Açıklama                                |
|--------|-------------------------|-----------------------------------------|
| GET    | `/api/admin/audit-logs` | Tüm audit kayıtları (pagination/filter) |

---

## 6. Security Detayları

- **Access token:** kısa ömürlü (örn. 15 dk), her request header'da `Authorization: Bearer <token>`
- **Refresh token:** uzun ömürlü (örn. 7 gün), DB'de saklanır (`RefreshToken` tablosu), rotation uygulanır (her
  refresh'te eskisi invalidate edilir)
- **Password:** `BCryptPasswordEncoder`
- **Filter chain:** `JwtAuthenticationFilter` her request'te token'ı doğrular, `SecurityContext`'e authentication set
  eder
- Public endpoint'ler: `/api/auth/**`; geri kalan her şey authenticated

---

## 7. External API Entegrasyonu (Finnhub varsayımıyla)

- `StockPriceClient` interface + `FinnhubStockPriceClient` implementasyonu
- Spring 6.1+ `RestClient` kullanılır (senkron, ekstra dependency gerektirmez)
- API key `application.yml`'de değil, environment variable'dan okunur (`${FINNHUB_API_KEY}`)
- Hata senaryoları: timeout, 429 (rate limit), 404 (geçersiz sembol) → custom exception'lara map edilir

---

## 8. Caching Stratejisi

- `@Cacheable("stockPrices")` ile `StockPriceService.getPrice(symbol)` cache'lenir
- TTL: Redis'te 60 saniye (rate limit'e takılmamak için) — `RedisCacheConfiguration` ile ayarlanır
- Faz 6'daki scheduled job, aktif holding'lerin cache'ini proaktif olarak yeniler (`@CacheEvict` + yeniden fetch)

---

## 9. Concurrency & Transaction Yönetimi

**Senaryo:** Kullanıcı aynı anda iki "buy" isteği gönderirse (çift tıklama, retry vb.), bakiye kontrolü ve düşürme
işlemi arasında race condition oluşabilir → bakiye negatife düşebilir veya holding tutarsız olabilir.

**Çözüm katmanları:**

1. `Wallet` ve `Holding` entity'lerinde `@Version` (optimistic locking) — çakışan güncellemede `OptimisticLockException`
   fırlatılır, service katmanında retry veya kullanıcıya hata dönülür
2. Kritik yollarda (örn. bakiye düşürme) `@Lock(LockModeType.PESSIMISTIC_WRITE)` alternatifi tartışılır
3. Tüm buy/sell akışı tek bir `@Transactional` metod içinde, uygun isolation level ile

---

## 10. Scheduling & Async

- `@Scheduled(fixedRate = 60000)` — aktif holding'i olan hisselerin fiyatını periyodik günceller
- `@Async` — işlem sonrası "bildirim" (log/console simülasyonu) ana request thread'ini bloklamadan çalışır
- `@EnableScheduling`, `@EnableAsync` ana application class'ına eklenir

---

## 11. Auditing & Logging (AOP)

- Custom `@Audited` annotation, para hareketi yapan service metodlarına (`buy`, `sell`, `deposit`) eklenir
- `@Aspect` sınıfı `@Around` advice ile bu metodları sarar, öncesi/sonrası state'i `AuditLog` tablosuna yazar
- Bankacılıkta audit trail'in "kim, ne zaman, neyi değiştirdi" sorusuna cevap vermesi kritik — bu yüzden AOP ile merkezi
  ve tutarlı loglama yapılır (her metoda manuel log satırı eklemek yerine)

---

## 12. Hata Yönetimi

- `@RestControllerAdvice` + `ProblemDetail` (RFC 7807) ile standart hata formatı
- Custom exception'lar: `InsufficientBalanceException`, `StockNotFoundException`, `InvalidTokenException`,
  `OptimisticLockConflictException`

---

## 13. Veritabanı Migration Stratejisi (Liquibase)

- `spring.jpa.hibernate.ddl-auto` **kullanılmaz** (en fazla `validate` — şema ile entity'lerin uyuştuğunu doğrulamak
  için); şemayı Hibernate değil Liquibase yönetir
- Changelog dosyaları: `src/main/resources/db/changelog/db.changelog-master.yaml` (master dosya, diğer changeset
  dosyalarını `include` ile toplar)
- Her yeni tablo/kolon değişikliği ayrı bir changeset dosyası olarak eklenir (örn. `001-create-user-table.yaml`,
  `002-create-stock-table.yaml`), böylece her değişikliğin geçmişi ve rollback'i takip edilebilir
- Format tercihi: **YAML** (XML'e göre daha okunaklı, JSON'dan daha az gürültülü)
- Liquibase, Spring Boot ile otomatik entegre olur — `spring-boot-starter-data-jpa` + `liquibase-core` dependency'si
  yeterli, uygulama başlarken changelog'lar otomatik uygulanır
- Rollback senaryoları için her changeset'te `rollback` bloğu tanımlanır (Liquibase'in Flyway'e göre öne çıkan özelliği)

---

## 14. Local Ortam Kurulumu

`docker-compose.yml` (Faz 9'da eklenecek, ama istersen şimdi de kurulabilir):

- `postgres:16` — portfolio DB
- `redis:7` — cache

`application.yml`'de datasource, redis, JWT secret gibi konfigürasyonlar environment variable ile okunur (secret'lar
repo'ya commit edilmez).

---

## 15. Test Stratejisi

- **Unit test:** Service katmanı, Mockito ile repository/external client mock'lanır
- **Integration test:** `@SpringBootTest` + Testcontainers (gerçek Postgres/Redis, mock değil)
- **Concurrency test:** Çoklu thread ile aynı endpoint'e paralel istek atıp tutarlılığı doğrulayan özel bir test
  senaryosu (Faz 5'in doğrulaması)

---

## 16. Sıradaki Adım

Faz 1'den başla: `Stock` ve `User` entity'lerini, temel repository/service/controller katmanlarını oluştur. Hazır
olduğunda birlikte ilerleriz.
