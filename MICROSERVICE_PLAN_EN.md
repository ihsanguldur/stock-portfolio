# Transition to Microservices — Plan

> **Context:** Phases 1-9 in `PLAN.md` are done (layered architecture → DDD/feature-based modular monolith → Docker
> Compose + Actuator + Kafka). This document plans splitting that modular monolith into real, independently deployed
> microservices. The goal is still learning: distributed transactions (Saga), inter-service communication, separate
> databases, Kubernetes.

---

## 1. Why, and What Changes

In the modular monolith, every bounded context has its own package but still runs in **a single JVM, a single database,
a single deployment unit**. Moving to microservices means:

- Each context becomes its own Spring Boot application (its own JAR, its own process, its own port).
- Each service gets **its own** database — no service ever touches another service's tables directly via SQL.
- Inter-service communication is no longer a Java method call — it's **HTTP (RestClient)** and **Kafka events**.
- The biggest problem: flows like `buy()` can no longer live in a single `@Transactional` block → this requires the *
  *Saga pattern** (hand-written orchestration + compensating transactions).
- Ultimately deployed to **Kubernetes**.

## 2. Key Decisions Made

| Topic                                   | Decision                                                                                              |
|-----------------------------------------|-------------------------------------------------------------------------------------------------------|
| Project organization                    | Single git repo, **multi-module Maven** (parent `pom.xml` + 6 child modules)                          |
| Synchronous inter-service communication | **REST** (Spring `RestClient`, same as the Finnhub integration)                                       |
| Saga implementation                     | **Hand-written orchestration** (no framework like Axon/Eventuate — the point is to see the mechanics) |
| Deployment target                       | **Kubernetes** (locally: minikube/kind)                                                               |

## 3. Services and Data Ownership

| Service               | Port (suggested) | Owns table(s)             | Responsibility                                             |
|-----------------------|------------------|---------------------------|------------------------------------------------------------|
| `auth-service`        | 8081             | `users`, `refresh_tokens` | register/login/refresh/logout, JWT issuance                |
| `wallet-service`      | 8082             | `wallets`                 | balance lookup, withdraw/deposit                           |
| `stock-service`       | 8083             | `stocks`                  | price lookup, Finnhub integration, price-refresh scheduler |
| `portfolio-service`   | 8084             | `holdings`                | holding lookup, updating holdings after buy/sell           |
| `transaction-service` | 8085             | `transactions`            | **buy/sell orchestration hub (Saga)**, transaction history |
| `audit-service`       | 8086             | `audit_logs`              | writing audit logs (via Kafka event), admin viewing        |

**Important rule:** Cross-service `@ManyToOne` relationships (like `Holding.user`) are no longer allowed — no service
knows another service's entity, each just holds a plain reference (`Long userId` / `String email`). If user details are
ever needed, the owning service is called over REST; in most cases the `email` claim already inside the JWT is enough
and no extra call is needed.

## 4. The `buy()` Saga Flow (the most critical design piece)

`transaction-service` becomes the orchestrator:

```
1. transaction-service: extract email from JWT, fetch price from stock-service via StockClient
2. transaction-service → wallet-service: POST /wallet/withdraw  { email, amount }
   - Failure (insufficient balance) → returns 400, transaction-service stops here, returns error to caller
3. transaction-service → portfolio-service: POST /portfolio/holdings/buy  { email, symbol, quantity, price }
   - Failure (network error, unexpected condition) → COMPENSATING ACTION:
     transaction-service → wallet-service: POST /wallet/deposit  { email, amount }  (refund)
     then returns error to caller
4. Both succeeded → transaction-service writes its own Transaction record
5. transaction-service → Kafka: "transaction-events" (notification-service keeps listening as before)
```

`sell()` is symmetric (the holding is decremented first; if that fails no compensation is needed since the wallet hasn't
been touched yet; if something fails after the wallet has been credited, the wallet credit is reversed).

## 5. Phases

### Phase M1 — Reduce cross-context JPA relationships to plain IDs (inside the monolith, no modules yet)

- **Why this comes first:** Doing this after the physical module split would mean, e.g., the `portfolio-service` module
  needs a compile-time dependency on `auth-service` just for the `User` class behind `Holding.user` — which breaks
  microservice independence before it even starts.
- 5 relationships need to change (verified these are the ones present in the codebase):
    - `Holding.user` (`User` → `Long userId`), `Holding.stock` (`Stock` → `Long stockId`)
    - `Transaction.user` (`User` → `Long userId`), `Transaction.stock` (`Stock` → `Long stockId`)
    - `Wallet.user` (`User` → `Long userId`)
- Staying as-is: `RefreshToken.user` — since `RefreshToken` and `User` both stay in `auth-service`, this relationship
  never crosses a context boundary.
- This also affects repository queries (e.g., `findByUserAndStock(User, Stock)` → `findByUserIdAndStockId(Long, Long)`)
  and in-entity navigation like `this.getStock().getSymbol()` (no longer possible — the caller must pass whatever it
  needs as a parameter)
- **Verify:** all existing tests (unit + integration) still pass, no behavior changed, only the relationships were
  reduced to IDs

### Phase M2 — Multi-module Maven skeleton (the physical split)

M1 only covered JPA relationships. Two more cross-context dependencies were discovered before the physical split — these
are resolved first, as part of M2, still inside the monolith:

#### M2.1 — Making the JWT stateless

- **Problem:** `JwtAuthenticationFilter` → `CustomUserDetailsService` → `UserRepository` chain. Since every service
  needs security, this would mean every module has an illegal dependency on `auth-service`'s `UserRepository`.
- **Fix:** add a `userId` claim to the JWT (email and role were already there). `JwtAuthenticationFilter` now builds the
  `Authentication` object directly from JWT claims (email + role + userId), never touching `CustomUserDetailsService`/
  `UserRepository`.
- This is exactly the work planned for Phase M6 — just done now instead of later, so it isn't redone.
- **Verify:** protected endpoints still work after login/register, existing tests pass.

#### M2.2 — Turning AuditAspect into a Kafka event

- **Problem:** `AuditAspect` (running on `@Audited`-annotated `WalletService.deposit()`,
  `TransactionService.buy()/sell()`) synchronously, in-process, reaches into both `auth-service`'s `UserRepository` (to
  resolve userId from email) and `audit-service`'s `AuditLogRepository` (to write the log row).
- **Fix:**
    - `userId` is now read from the JWT (thanks to M2.1), no more `UserRepository` call.
    - `AuditAspect` no longer calls `AuditLogWriter`/`AuditLogRepository` directly; it builds an `AuditEvent` and
      publishes it to a `"audit-events"` Kafka topic.
    - A new `@KafkaListener` consumer inside `audit-service` listens for this event and writes it via
      `AuditLog.create(...)` (the existing `AuditLogWriter` logic moves here).
- Result: `AuditAspect` no longer needs any cross-context repository, so it can safely live in the `common` module.
- **Verify:** `audit_logs` rows are still created after buy/sell/deposit (now asynchronously, via Kafka).

#### M2.3 — The `common` module

- Shared/cross-cutting code (nothing specific to any one bounded context) is extracted into a 7th Maven module (
  `common`), which the other 6 services depend on at compile time:
    - `security/JwtService`, `security/JwtAuthenticationFilter` (stateless, after M2.1)
    - `config/SecurityConfig` (base skeleton — small per-service differences come in M6)
    - `exception/GlobalExceptionHandler`, `exception/InvalidCredentialsException`
    - `helper/RetryHelper`
    - `dto/response/Identifiable`
    - `audit/Audited`, `audit/AuditAspect`, `audit/event/AuditEvent` (after M2.2 — both the services publishing the
      event and the `audit-service` consumer need this event class)
- `CustomUserDetailsService` — deleted entirely while implementing M2.1 (only the old `JwtAuthenticationFilter` used it;
  it turned out to play no role in login/register). Nothing left to move to `auth-service`.

#### M2.4 — Inter-service REST clients + Saga orchestration (still inside the single `app` module)

- **Problem (found after M2.3):** before extracting `common` and physically splitting, checking the services revealed
  they call each other's repositories **directly as Java method calls** — a different kind of cross-context dependency
  than the JPA relationships M1 covered:
    - `TransactionService` → `WalletRepository` (wallet), `HoldingRepository` (portfolio), `StockRepository`/
      `StockService` (stock)
    - `PortfolioService` → `StockRepository`/`StockService` (stock)
    - `AuthService` → `WalletRepository` (wallet — opens the wallet during register)
    - `StockPriceRefreshScheduler` → `HoldingRepository` (portfolio)
- **Why this order:** the original phase order (physical split first, then REST clients, then Saga) was actually
  impossible — without the REST clients, modules like `transaction-service` would never compile (illegal cross-module
  dependency). This is the same class of sequencing mistake we caught for M1's JPA relationships, just for service-level
  calls this time. So the old "Phase M4" (REST clients) and "Phase M5" (Saga) were pulled forward to here, before the
  physical split.
- **Work (still one `app` module, one process — the RestClients will call `localhost:8080` on themselves, since
  everything is still in one process):**
    - Real HTTP endpoints are added where missing: `POST /wallet/withdraw`, `POST /portfolio/holdings/buy`,
      `POST /portfolio/holdings/sell` (currently only exist as internal methods, not exposed)
    - `WalletClient`, `PortfolioClient`, `StockClient` are written (using Spring `RestClient`)
    - `TransactionService.buy()`/`sell()` are rewritten to use these clients, including the compensating action (Saga
      flow below)
    - `AuthService.register()` switches to `WalletClient` for opening the wallet
    - `StockPriceRefreshScheduler` stops querying `HoldingRepository` directly — either it asks `PortfolioClient` which
      stocks have holdings, or (simpler) it just refreshes all stocks periodically, removing the cross-context query
      entirely
- **Verify:** `TransactionServiceTest`/`PortfolioServiceTest`/`AuthServiceTest` updated with the new client mocks; make
  `portfolio-service` fail on purpose and confirm the balance in `wallet-service` is actually refunded

#### M2.5 — The physical split

- With no more cross-context Java dependencies, this step really is just moving files around
- Root `pom.xml` → `<packaging>pom</packaging>`, `<modules>` list (`common` + 6 services)
- 6 child modules, each with its own `pom.xml`, its own `@SpringBootApplication` class, its own
  `application.properties` (different port)
- Code from the current feature packages moves into the matching module (the `transaction/` package → the
  `transaction-service` module, etc.)
- RestClient base URLs switch from `localhost:8080` to the real per-service ports (e.g.
  `wallet-service.url=http://localhost:8082`)
- At this stage they can still all point at the **same** shared database (DB separation comes next)
- **Verify:** each module boots on its own with `mvn spring-boot:run`, the end-to-end buy/sell flow works over real HTTP
  calls

### Phase M3 — Splitting the database

- Each service gets its own Postgres database (either 6 separate databases on one Postgres instance, or 6 separate
  containers — start small, one instance/multiple databases is enough)
- Liquibase changelogs are split per module (each module only creates its own table)
- **Important:** M1 removed the `@ManyToOne`/`@OneToOne` relationships on the Java side, but never touched the DB-level
  FK constraints — they're still there: `fk_holdings_user`/`fk_holdings_stock` (holdings→users/stocks),
  `fk_transactions_user`/`fk_transactions_stock` (transactions→users/stocks), `fk_wallets_user` (wallets→users).
  Harmless while everything shares one DB (extra integrity guarantee for free). But once physically split into separate
  databases, Postgres doesn't support cross-database FKs, so a **Liquibase changeset dropping these 5 constraints** is
  needed. `fk_refresh_tokens_user` stays (refresh_tokens and users both remain in auth-service).
- **Verify:** each service connects to its own DB and can do CRUD, migrations run correctly, cross-context FKs are gone

### Phase M4 — Security in every service

- Every service gets its own `JwtAuthenticationFilter` + `SecurityConfig` (validation only; token **issuance** stays
  exclusively in `auth-service`)
- The JWT secret is shared across all services (shared env variable)
- **Verify:** an unauthenticated request returns 401 from every service

### Phase M5 — Docker

- A `Dockerfile` per module
- `docker-compose.yml` updated: 6 services + Postgres + Redis + Kafka
- **Verify:** `docker compose up` brings up the whole system, an end-to-end `buy` flow works

### Phase M6 — Kubernetes

- A `Deployment` + `Service` (ClusterIP) manifest per service
- `Deployment`/`StatefulSet` + `Service` for Postgres/Redis/Kafka
- `ConfigMap` (app settings), `Secret` (DB password, JWT secret)
- Local testing via minikube/kind
- **Verify:** `kubectl get pods` shows everything `Running`, an end-to-end flow works via port-forward

---

## 6. Next Step

Phase M2.4 — Inter-service REST clients + Saga orchestration (still inside the single `app` module). Once that's ready,
we continue together.
