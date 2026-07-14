# Mikroservis Mimarisine Geçiş — Plan

> **Bağlam:** `PLAN.md`'deki Faz 1-9 tamamlandı (katmanlı mimari → DDD/feature-based modüler monolit → Docker Compose +
> Actuator + Kafka). Bu doküman, o modüler monoliti gerçek, bağımsız deploy edilen mikroservislere bölmenin planı. Amaç
> yine öğrenmek: dağıtık transaction (Saga), servisler arası iletişim, ayrı veritabanları, Kubernetes.

---

## 1. Neden ve Ne Değişiyor

Modüler monolitte her bounded context kendi paketinde ama hâlâ **tek JVM, tek veritabanı, tek deploy birimi**.
Mikroservise geçince:

- Her context ayrı bir Spring Boot uygulaması (ayrı JAR, ayrı process, ayrı port) olacak.
- Her servisin **kendi** veritabanı olacak — başka bir servisin tablosuna asla doğrudan SQL ile erişilmeyecek.
- Servisler arası iletişim artık Java metod çağrısı değil, **HTTP (RestClient)** ve **Kafka event'leri**.
- En büyük problem: `buy()` gibi akışlar artık tek bir `@Transactional` içinde olamıyor → **Saga pattern** (elle
  yazılmış orkestrasyon + compensating transaction) gerekiyor.
- Sonunda **Kubernetes**'e deploy edilecek.

## 2. Kararlaştırılan Temel Seçimler

| Konu                             | Karar                                                                                          |
|----------------------------------|------------------------------------------------------------------------------------------------|
| Proje organizasyonu              | Tek git reposu, **multi-module Maven** (parent `pom.xml` + 6 child modül)                      |
| Servisler arası senkron iletişim | **REST** (Spring `RestClient`, Finnhub entegrasyonundaki gibi)                                 |
| Saga implementasyonu             | **Elle yazılmış orkestrasyon** (Axon/Eventuate gibi hazır framework yok — mantığı görmek için) |
| Deployment hedefi                | **Kubernetes** (yerel: minikube/kind)                                                          |

## 3. Servisler ve Veri Sahipliği

| Servis                | Port (öneri) | Sahip olduğu tablo(lar)   | Sorumluluk                                                          |
|-----------------------|--------------|---------------------------|---------------------------------------------------------------------|
| `auth-service`        | 8081         | `users`, `refresh_tokens` | register/login/refresh/logout, JWT issue etme                       |
| `wallet-service`      | 8082         | `wallets`                 | bakiye sorgulama, withdraw/deposit                                  |
| `stock-service`       | 8083         | `stocks`                  | fiyat sorgulama, Finnhub entegrasyonu, fiyat güncelleme scheduler'ı |
| `portfolio-service`   | 8084         | `holdings`                | holding sorgulama, buy/sell sonucu holding güncelleme               |
| `transaction-service` | 8085         | `transactions`            | **buy/sell orkestrasyon merkezi (Saga)**, işlem geçmişi             |
| `audit-service`       | 8086         | `audit_logs`              | audit log yazma (Kafka event'iyle), admin görüntüleme               |

**Önemli kural:** Artık `Holding.user` gibi cross-service `@ManyToOne` ilişkiler yasak — her servis diğer servisin
entity'sini bilmez, sadece bir `Long userId` / `String email` gibi düz bir referans tutar. Kullanıcı detayına ihtiyaç
olursa (nadiren), ilgili servise REST çağrısı yapılır; çoğu durumda JWT'nin içindeki `email` claim'i zaten yeterli,
ekstra çağrı gerekmez.

## 4. `buy()` Saga Akışı (en kritik tasarım)

`transaction-service` orkestratör olur:

```
1. transaction-service: JWT'den email al, StockClient ile stock-service'ten fiyatı çek
2. transaction-service → wallet-service: POST /wallet/withdraw  { email, amount }
   - Başarısız (yetersiz bakiye) → 400 döner, transaction-service akışı burada durdurur, kullanıcıya hata döner
3. transaction-service → portfolio-service: POST /portfolio/holdings/buy  { email, symbol, quantity, price }
   - Başarısız (network hatası, beklenmeyen durum) → COMPENSATING ACTION:
     transaction-service → wallet-service: POST /wallet/deposit  { email, amount }  (parayı iade et)
     ardından kullanıcıya hata döner
4. Her ikisi de başarılı → transaction-service kendi Transaction kaydını yazar
5. transaction-service → Kafka: "transaction-events" (notification-service dinlemeye devam eder)
```

`sell()` simetriktir (önce holding düşürülür, başarısızsa telafi gerekmez çünkü wallet'a hiç dokunulmamıştır; wallet'a
para eklendikten sonra bir şey patlarsa wallet'tan geri düşülür).

## 5. Fazlar

### Faz M1 — Cross-context JPA ilişkilerini düz ID'ye indirgemek (monolit içinde, henüz modül yok)

- **Neden önce bu:** Modülleri fiziksel olarak bölmeden önce yapılmazsa, örn. `portfolio-service` modülü `Holding.user`
  alanı için `auth-service`'teki `User` class'ına derleme zamanı bağımlılığı ister — bu da mikroservislerin
  bağımsızlığını daha en baştan bozar.
- Değişecek 5 ilişki (kontrol ettim, kod tabanında bunlar var):
    - `Holding.user` (`User` → `Long userId`), `Holding.stock` (`Stock` → `Long stockId`)
    - `Transaction.user` (`User` → `Long userId`), `Transaction.stock` (`Stock` → `Long stockId`)
    - `Wallet.user` (`User` → `Long userId`)
- Değişmeyecek: `RefreshToken.user` — `RefreshToken` ve `User` ikisi de `auth-service`'te kalacağı için bu ilişki
  context sınırını aşmıyor, aynı kalabilir.
- Bu değişiklik repository sorgularını da etkiler (örn. `findByUserAndStock(User, Stock)` →
  `findByUserIdAndStockId(Long, Long)`), ve entity içindeki `this.getStock().getSymbol()` gibi navigasyonları da (artık
  mümkün değil, çağıran taraf gereken bilgiyi parametre olarak geçmeli)
- **Verify:** mevcut tüm testler (unit + integration) hâlâ geçiyor, hiçbir davranış değişmedi, sadece ilişkiler ID'ye
  indirgendi

### Faz M2 — Multi-module Maven iskeleti (fiziksel bölünme)

M1, sadece JPA ilişkilerini kapsıyordu. Modülleri fiziksel olarak bölmeden önce keşfedilen iki ek cross-context
bağımlılık daha var — bunlar da M2'nin bir parçası olarak, fiziksel bölünmeden **önce** (hâlâ monolit içinde) çözülüyor:

#### M2.1 — JWT'yi stateless hale getirmek

- **Sorun:** `JwtAuthenticationFilter` → `CustomUserDetailsService` → `UserRepository` zinciri var. Güvenlik her
  serviste gerekeceği için bu, her modülün `auth-service`'in `UserRepository`'sine illegal bağımlılığı anlamına gelirdi.
- **Çözüm:** JWT'ye `userId` claim'i eklenir (email ve role zaten vardı). `JwtAuthenticationFilter` artık
  `CustomUserDetailsService`/`UserRepository`'ye hiç gitmeden, `Authentication` nesnesini doğrudan JWT claim'lerinden (
  email + role + userId) kurar.
- Bu, Faz M6'da yapılması planlanan işin aynısı — sadece zamanı öne çekildi, tekrar yapılmayacak.
- **Verify:** login/register sonrası korumalı endpoint'lere erişim hâlâ çalışıyor, mevcut testler geçiyor.

#### M2.2 — AuditAspect'i Kafka event'ine çevirmek

- **Sorun:** `AuditAspect` (`@Audited` ile işaretli `WalletService.deposit()`, `TransactionService.buy()/sell()`
  üzerinde) hem `auth-service`'in `UserRepository`'sine (email'den userId bulmak için) hem `audit-service`'in
  `AuditLogRepository`'sine (log yazmak için) senkron, aynı-JVM içi erişiyor.
- **Çözüm:**
    - `userId` artık M2.1 sayesinde JWT'den okunuyor, `UserRepository`'ye gitmeye gerek kalmıyor.
    - `AuditAspect` artık `AuditLogWriter`/`AuditLogRepository`'yi doğrudan çağırmıyor; bir `AuditEvent` kaydı oluşturup
      `"audit-events"` Kafka topic'ine basıyor.
    - `audit-service` içinde yeni bir `@KafkaListener` consumer, bu event'i dinleyip `AuditLog.create(...)` ile kendi
      DB'sine yazıyor (mevcut `AuditLogWriter` mantığı buraya taşınır).
- Sonuç: `AuditAspect` artık hiçbir cross-context repository'ye ihtiyaç duymuyor, `common` modülüne güvenle konabilir.
- **Verify:** buy/sell/deposit sonrası `audit_logs` tablosunda satır oluşuyor (artık Kafka üzerinden, asenkron).

#### M2.3 — Ortak (`common`) modül

- Payşalıan/cross-cutting kod (hiçbir bounded context'e özgü olmayan) 7. bir Maven modülüne (`common`) çıkarılır, diğer
  6 servis buna compile-time dependency olarak bakar:
    - `security/JwtService`, `security/JwtAuthenticationFilter` (M2.1 sonrası stateless)
    - `config/SecurityConfig` (temel iskelet — her serviste küçük farklar M6'da eklenecek)
    - `exception/GlobalExceptionHandler`, `exception/InvalidCredentialsException`
    - `helper/RetryHelper`
    - `dto/response/Identifiable`
    - `audit/Audited`, `audit/AuditAspect`, `audit/event/AuditEvent` (M2.2 sonrası — hem event'i basan servisler hem
      `audit-service` consumer'ı bu event class'ına ihtiyaç duyuyor)
- `CustomUserDetailsService` — M2.1 uygulanırken tamamen silindi (sadece eski `JwtAuthenticationFilter` kullanıyordu,
  login/register akışında hiç kullanılmıyormuş). `auth-service`'e de taşınacak bir şey kalmadı.

#### M2.4 — Inter-service REST client'ları + Saga orkestrasyonu (hâlâ tek `app` modülü içinde)

- **Sorun (M2.3 sonrası fark edildi):** `common` modülü çıkarılıp fiziksel bölünmeye geçmeden önce kontrol edince,
  servislerin birbirinin repository'sini **doğrudan Java çağrısıyla** kullandığı görüldü — bu M1'in kapsamındaki JPA
  ilişkisi sorunundan farklı, ayrı bir cross-context bağımlılık türü:
    - `TransactionService` → `WalletRepository` (wallet), `HoldingRepository` (portfolio), `StockRepository`/
      `StockService` (stock)
    - `PortfolioService` → `StockRepository`/`StockService` (stock)
    - `AuthService` → `WalletRepository` (wallet — register'da cüzdan açarken)
    - `StockPriceRefreshScheduler` → `HoldingRepository` (portfolio)
- **Neden bu sıra:** Orijinal plan sırası (önce fiziksel bölünme, sonra REST client'lar, sonra Saga) aslında
  imkansızdı — REST client'lar olmadan `transaction-service` gibi modüller hiç derlenemez (illegal cross-module
  bağımlılık). M1'in JPA ilişkileri için yakaladığımız sıralama hatasının bir benzeri, bu sefer servis çağrıları için.
  Bu yüzden eski "Faz M4" (REST client'lar) ve "Faz M5" (Saga) buraya, fiziksel bölünmeden **önce** alındı.
- **Yapılacaklar (hâlâ tek `app` modülü, tek process — RestClient'lar kendine `localhost:8080` üzerinden HTTP atacak):**
    - Her ilgili context'e gerçek HTTP endpoint'leri eklenir: `POST /wallet/withdraw`, `POST /portfolio/holdings/buy`,
      `POST /portfolio/holdings/sell` (şu an sadece iç metod olarak var, dışa açık değiller)
    - `WalletClient`, `PortfolioClient`, `StockClient` (Spring `RestClient` ile) yazılır
    - `TransactionService.buy()`/`sell()` bu client'ları kullanacak şekilde yeniden yazılır, compensating action dahil (
      aşağıdaki Saga akışı)
    - `AuthService.register()` cüzdan açmak için `WalletClient`'a geçer
    - `StockPriceRefreshScheduler` `HoldingRepository`'ye direkt gitmek yerine ya `PortfolioClient` üzerinden holding'i
      olan stock'ları sorar, ya da (daha basit) artık sadece tüm stock'ları periyodik yeniler — cross-context sorguya
      hiç gerek kalmaz
- **Verify:** `TransactionServiceTest`/`PortfolioServiceTest`/`AuthServiceTest` yeni client mock'larıyla güncellenir,
  `portfolio-service`'i kasıtlı hata verecek şekilde simüle edip `wallet-service`'teki bakiyenin gerçekten iade edildiği
  doğrulanır

#### M2.5 — Fiziksel bölünme

- Artık cross-context Java bağımlılığı kalmadığı için bu adım gerçekten sadece dosya taşımaktan ibaret
- Root `pom.xml` → `<packaging>pom</packaging>`, `<modules>` listesi (`common` + 6 servis)
- 6 child modül, her birinde kendi `pom.xml`, kendi `@SpringBootApplication` sınıfı, kendi `application.properties` (
  farklı port)
- Mevcut feature paketlerindeki kod ilgili modüle taşınır (`transaction/` paketi → `transaction-service` modülü, vb.)
- RestClient'ların base URL'leri `localhost:8080` yerine gerçek servis portlarına çevrilir (
  `wallet-service.url=http://localhost:8082` gibi)
- Bu aşamada hâlâ **tek** paylaşılan veritabanına bağlanabilirler (DB ayrımı henüz yapılmadı, sırada)
- **Verify:** her modül tek başına `mvn spring-boot:run` ile ayağa kalkıyor, uçtan uca buy/sell akışı gerçek HTTP
  çağrılarıyla çalışıyor

### Faz M3 — Veritabanı ayrımı

- Her serviste kendi Postgres veritabanı (aynı Postgres instance'ında 6 farklı `CREATE DATABASE`, ya da 6 ayrı
  container — küçük başlarız, tek instance/çoklu DB yeterli)
- Liquibase changelog'ları modüllere bölünür (her modül sadece kendi tablosunu oluşturur)
- **Önemli:** M1'de Java tarafındaki `@ManyToOne`/`@OneToOne` ilişkilerini kaldırdık ama DB'deki FK constraint'lerine
  hiç dokunmadık — hâlâ duruyorlar: `fk_holdings_user`/`fk_holdings_stock` (holdings→users/stocks),
  `fk_transactions_user`/`fk_transactions_stock` (transactions→users/stocks), `fk_wallets_user` (wallets→users). Tek
  DB'de zararsızlar (ekstra bütünlük garantisi). Ama fiziksel olarak ayrı DB'lere bölününce Postgres cross-database FK
  desteklemediği için bu 5 constraint'i **drop eden bir Liquibase changeset** yazmamız gerekiyor.
  `fk_refresh_tokens_user` kalır (refresh_tokens + users ikisi de auth-service'te kalıyor).
- **Verify:** her servis kendi DB'sine bağlanıp CRUD yapabiliyor, migration'lar doğru çalışıyor, cross-context FK'ler
  kalkmış

### Faz M4 — Güvenlik her serviste

- Her serviste `JwtAuthenticationFilter` + `SecurityConfig` (sadece token **doğrulama**; token **issue etme** sadece
  `auth-service`'te kalır)
- JWT secret tüm servislerde ortak (paylaşılan env variable)
- **Verify:** token'sız istek her serviste 401 dönüyor

### Faz M5 — Docker

- Her modül için `Dockerfile`
- `docker-compose.yml` güncellenir: 6 servis + Postgres + Redis + Kafka
- **Verify:** `docker compose up` ile tüm sistem ayağa kalkıyor, uçtan uca bir `buy` akışı çalışıyor

### Faz M6 — Kubernetes

- Her servis için `Deployment` + `Service` (ClusterIP) manifest'i
- Postgres/Redis/Kafka için `Deployment`/`StatefulSet` + `Service`
- `ConfigMap` (uygulama ayarları), `Secret` (DB şifresi, JWT secret)
- Yerel test için minikube/kind
- **Verify:** `kubectl get pods` hepsi `Running`, port-forward ile uçtan uca akış test edilebiliyor

---

## 6. Sıradaki Adım

Faz M2.4 — Inter-service REST client'ları + Saga orkestrasyonu (hâlâ tek `app` modülü içinde). Hazır olduğunda birlikte
ilerleriz.
