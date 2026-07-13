# Mikroservis Mimarisine Geçiş — Plan

> **Bağlam:** `PLAN.md`'deki Faz 1-9 tamamlandı (katmanlı mimari → DDD/feature-based modüler monolit → Docker Compose + Actuator + Kafka). Bu doküman, o modüler monoliti gerçek, bağımsız deploy edilen mikroservislere bölmenin planı. Amaç yine öğrenmek: dağıtık transaction (Saga), servisler arası iletişim, ayrı veritabanları, Kubernetes.

---

## 1. Neden ve Ne Değişiyor

Modüler monolitte her bounded context kendi paketinde ama hâlâ **tek JVM, tek veritabanı, tek deploy birimi**. Mikroservise geçince:
- Her context ayrı bir Spring Boot uygulaması (ayrı JAR, ayrı process, ayrı port) olacak.
- Her servisin **kendi** veritabanı olacak — başka bir servisin tablosuna asla doğrudan SQL ile erişilmeyecek.
- Servisler arası iletişim artık Java metod çağrısı değil, **HTTP (RestClient)** ve **Kafka event'leri**.
- En büyük problem: `buy()` gibi akışlar artık tek bir `@Transactional` içinde olamıyor → **Saga pattern** (elle yazılmış orkestrasyon + compensating transaction) gerekiyor.
- Sonunda **Kubernetes**'e deploy edilecek.

## 2. Kararlaştırılan Temel Seçimler

| Konu | Karar |
|---|---|
| Proje organizasyonu | Tek git reposu, **multi-module Maven** (parent `pom.xml` + 6 child modül) |
| Servisler arası senkron iletişim | **REST** (Spring `RestClient`, Finnhub entegrasyonundaki gibi) |
| Saga implementasyonu | **Elle yazılmış orkestrasyon** (Axon/Eventuate gibi hazır framework yok — mantığı görmek için) |
| Deployment hedefi | **Kubernetes** (yerel: minikube/kind) |

## 3. Servisler ve Veri Sahipliği

| Servis | Port (öneri) | Sahip olduğu tablo(lar) | Sorumluluk |
|---|---|---|---|
| `auth-service` | 8081 | `users`, `refresh_tokens` | register/login/refresh/logout, JWT issue etme |
| `wallet-service` | 8082 | `wallets` | bakiye sorgulama, withdraw/deposit |
| `stock-service` | 8083 | `stocks` | fiyat sorgulama, Finnhub entegrasyonu, fiyat güncelleme scheduler'ı |
| `portfolio-service` | 8084 | `holdings` | holding sorgulama, buy/sell sonucu holding güncelleme |
| `transaction-service` | 8085 | `transactions` | **buy/sell orkestrasyon merkezi (Saga)**, işlem geçmişi |
| `audit-service` | 8086 | `audit_logs` | audit log yazma (Kafka event'iyle), admin görüntüleme |

**Önemli kural:** Artık `Holding.user` gibi cross-service `@ManyToOne` ilişkiler yasak — her servis diğer servisin entity'sini bilmez, sadece bir `Long userId` / `String email` gibi düz bir referans tutar. Kullanıcı detayına ihtiyaç olursa (nadiren), ilgili servise REST çağrısı yapılır; çoğu durumda JWT'nin içindeki `email` claim'i zaten yeterli, ekstra çağrı gerekmez.

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

`sell()` simetriktir (önce holding düşürülür, başarısızsa telafi gerekmez çünkü wallet'a hiç dokunulmamıştır; wallet'a para eklendikten sonra bir şey patlarsa wallet'tan geri düşülür).

## 5. Fazlar

### Faz M1 — Cross-context JPA ilişkilerini düz ID'ye indirgemek (monolit içinde, henüz modül yok)
- **Neden önce bu:** Modülleri fiziksel olarak bölmeden önce yapılmazsa, örn. `portfolio-service` modülü `Holding.user` alanı için `auth-service`'teki `User` class'ına derleme zamanı bağımlılığı ister — bu da mikroservislerin bağımsızlığını daha en baştan bozar.
- Değişecek 5 ilişki (kontrol ettim, kod tabanında bunlar var):
  - `Holding.user` (`User` → `Long userId`), `Holding.stock` (`Stock` → `Long stockId`)
  - `Transaction.user` (`User` → `Long userId`), `Transaction.stock` (`Stock` → `Long stockId`)
  - `Wallet.user` (`User` → `Long userId`)
- Değişmeyecek: `RefreshToken.user` — `RefreshToken` ve `User` ikisi de `auth-service`'te kalacağı için bu ilişki context sınırını aşmıyor, aynı kalabilir.
- Bu değişiklik repository sorgularını da etkiler (örn. `findByUserAndStock(User, Stock)` → `findByUserIdAndStockId(Long, Long)`), ve entity içindeki `this.getStock().getSymbol()` gibi navigasyonları da (artık mümkün değil, çağıran taraf gereken bilgiyi parametre olarak geçmeli)
- **Verify:** mevcut tüm testler (unit + integration) hâlâ geçiyor, hiçbir davranış değişmedi, sadece ilişkiler ID'ye indirgendi

### Faz M2 — Multi-module Maven iskeleti (fiziksel bölünme)
- Root `pom.xml` → `<packaging>pom</packaging>`, `<modules>` listesi
- 6 child modül, her birinde kendi `pom.xml`, kendi `@SpringBootApplication` sınıfı, kendi `application.properties` (farklı port)
- Mevcut feature paketlerindeki kod ilgili modüle taşınır (`transaction/` paketi → `transaction-service` modülü, vb.) — Faz M1 sayesinde artık hiçbir modülün başka bir modülün class'ına ihtiyacı yok
- Bu aşamada hâlâ **tek** paylaşılan veritabanına bağlanabilirler (DB ayrımı henüz yapılmadı, sırada)
- **Verify:** her modül tek başına `mvn spring-boot:run` ile ayağa kalkıyor

### Faz M3 — Veritabanı ayrımı
- Her serviste kendi Postgres veritabanı (aynı Postgres instance'ında 6 farklı `CREATE DATABASE`, ya da 6 ayrı container — küçük başlarız, tek instance/çoklu DB yeterli)
- Liquibase changelog'ları modüllere bölünür (her modül sadece kendi tablosunu oluşturur)
- **Verify:** her servis kendi DB'sine bağlanıp CRUD yapabiliyor, migration'lar doğru çalışıyor

### Faz M4 — Inter-service REST client'ları
- `transaction-service` içinde `WalletClient`, `PortfolioClient`, `StockClient` (Spring `RestClient` ile)
- Servis adresleri `application.properties`'te (`wallet-service.url=http://localhost:8082`)
- Her servis kendi REST endpoint'lerini (withdraw/deposit, holdings/buy vb.) açar
- **Verify:** `transaction-service`'ten gerçek bir HTTP çağrısıyla `wallet-service`'e ulaşılabiliyor

### Faz M5 — Saga orkestrasyonu
- `TransactionService.buy()`/`sell()` yukarıdaki akışa göre yeniden yazılır, compensating action dahil
- **Verify:** `portfolio-service`'i kasıtlı olarak hata verecek şekilde simüle et (örn. geçici olarak exception fırlat), `wallet-service`'teki bakiyenin gerçekten iade edildiğini doğrula

### Faz M6 — Güvenlik her serviste
- Her serviste `JwtAuthenticationFilter` + `SecurityConfig` (sadece token **doğrulama**; token **issue etme** sadece `auth-service`'te kalır)
- JWT secret tüm servislerde ortak (paylaşılan env variable)
- **Verify:** token'sız istek her serviste 401 dönüyor

### Faz M7 — Docker
- Her modül için `Dockerfile`
- `docker-compose.yml` güncellenir: 6 servis + Postgres + Redis + Kafka
- **Verify:** `docker compose up` ile tüm sistem ayağa kalkıyor, uçtan uca bir `buy` akışı çalışıyor

### Faz M8 — Kubernetes
- Her servis için `Deployment` + `Service` (ClusterIP) manifest'i
- Postgres/Redis/Kafka için `Deployment`/`StatefulSet` + `Service`
- `ConfigMap` (uygulama ayarları), `Secret` (DB şifresi, JWT secret)
- Yerel test için minikube/kind
- **Verify:** `kubectl get pods` hepsi `Running`, port-forward ile uçtan uca akış test edilebiliyor

---

## 6. Sıradaki Adım

Faz M1'den başla: cross-context JPA ilişkilerini düz ID'lere indirgemek. Hazır olduğunda birlikte ilerleriz.
