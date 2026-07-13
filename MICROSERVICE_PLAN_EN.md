# Transition to Microservices — Plan

> **Context:** Phases 1-9 in `PLAN.md` are done (layered architecture → DDD/feature-based modular monolith → Docker Compose + Actuator + Kafka). This document plans splitting that modular monolith into real, independently deployed microservices. The goal is still learning: distributed transactions (Saga), inter-service communication, separate databases, Kubernetes.

---

## 1. Why, and What Changes

In the modular monolith, every bounded context has its own package but still runs in **a single JVM, a single database, a single deployment unit**. Moving to microservices means:
- Each context becomes its own Spring Boot application (its own JAR, its own process, its own port).
- Each service gets **its own** database — no service ever touches another service's tables directly via SQL.
- Inter-service communication is no longer a Java method call — it's **HTTP (RestClient)** and **Kafka events**.
- The biggest problem: flows like `buy()` can no longer live in a single `@Transactional` block → this requires the **Saga pattern** (hand-written orchestration + compensating transactions).
- Ultimately deployed to **Kubernetes**.

## 2. Key Decisions Made

| Topic | Decision |
|---|---|
| Project organization | Single git repo, **multi-module Maven** (parent `pom.xml` + 6 child modules) |
| Synchronous inter-service communication | **REST** (Spring `RestClient`, same as the Finnhub integration) |
| Saga implementation | **Hand-written orchestration** (no framework like Axon/Eventuate — the point is to see the mechanics) |
| Deployment target | **Kubernetes** (locally: minikube/kind) |

## 3. Services and Data Ownership

| Service | Port (suggested) | Owns table(s) | Responsibility |
|---|---|---|---|
| `auth-service` | 8081 | `users`, `refresh_tokens` | register/login/refresh/logout, JWT issuance |
| `wallet-service` | 8082 | `wallets` | balance lookup, withdraw/deposit |
| `stock-service` | 8083 | `stocks` | price lookup, Finnhub integration, price-refresh scheduler |
| `portfolio-service` | 8084 | `holdings` | holding lookup, updating holdings after buy/sell |
| `transaction-service` | 8085 | `transactions` | **buy/sell orchestration hub (Saga)**, transaction history |
| `audit-service` | 8086 | `audit_logs` | writing audit logs (via Kafka event), admin viewing |

**Important rule:** Cross-service `@ManyToOne` relationships (like `Holding.user`) are no longer allowed — no service knows another service's entity, each just holds a plain reference (`Long userId` / `String email`). If user details are ever needed, the owning service is called over REST; in most cases the `email` claim already inside the JWT is enough and no extra call is needed.

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

`sell()` is symmetric (the holding is decremented first; if that fails no compensation is needed since the wallet hasn't been touched yet; if something fails after the wallet has been credited, the wallet credit is reversed).

## 5. Phases

### Phase M1 — Reduce cross-context JPA relationships to plain IDs (inside the monolith, no modules yet)
- **Why this comes first:** Doing this after the physical module split would mean, e.g., the `portfolio-service` module needs a compile-time dependency on `auth-service` just for the `User` class behind `Holding.user` — which breaks microservice independence before it even starts.
- 5 relationships need to change (verified these are the ones present in the codebase):
  - `Holding.user` (`User` → `Long userId`), `Holding.stock` (`Stock` → `Long stockId`)
  - `Transaction.user` (`User` → `Long userId`), `Transaction.stock` (`Stock` → `Long stockId`)
  - `Wallet.user` (`User` → `Long userId`)
- Staying as-is: `RefreshToken.user` — since `RefreshToken` and `User` both stay in `auth-service`, this relationship never crosses a context boundary.
- This also affects repository queries (e.g., `findByUserAndStock(User, Stock)` → `findByUserIdAndStockId(Long, Long)`) and in-entity navigation like `this.getStock().getSymbol()` (no longer possible — the caller must pass whatever it needs as a parameter)
- **Verify:** all existing tests (unit + integration) still pass, no behavior changed, only the relationships were reduced to IDs

### Phase M2 — Multi-module Maven skeleton (the physical split)
- Root `pom.xml` → `<packaging>pom</packaging>`, `<modules>` list
- 6 child modules, each with its own `pom.xml`, its own `@SpringBootApplication` class, its own `application.properties` (different port)
- Code from the current feature packages moves into the matching module (the `transaction/` package → the `transaction-service` module, etc.) — thanks to Phase M1, no module needs another module's classes anymore
- At this stage they can still all point at the **same** shared database (DB separation comes next)
- **Verify:** each module boots on its own with `mvn spring-boot:run`

### Phase M3 — Splitting the database
- Each service gets its own Postgres database (either 6 separate databases on one Postgres instance, or 6 separate containers — start small, one instance/multiple databases is enough)
- Liquibase changelogs are split per module (each module only creates its own table)
- **Verify:** each service connects to its own DB and can do CRUD, migrations run correctly

### Phase M4 — Inter-service REST clients
- Inside `transaction-service`: `WalletClient`, `PortfolioClient`, `StockClient` (using Spring `RestClient`)
- Service addresses live in `application.properties` (`wallet-service.url=http://localhost:8082`)
- Each service exposes its own REST endpoints (withdraw/deposit, holdings/buy, etc.)
- **Verify:** a real HTTP call from `transaction-service` successfully reaches `wallet-service`

### Phase M5 — Saga orchestration
- `TransactionService.buy()`/`sell()` are rewritten per the flow above, including the compensating action
- **Verify:** make `portfolio-service` fail on purpose (e.g., temporarily throw an exception), confirm the balance in `wallet-service` is actually refunded

### Phase M6 — Security in every service
- Every service gets its own `JwtAuthenticationFilter` + `SecurityConfig` (validation only; token **issuance** stays exclusively in `auth-service`)
- The JWT secret is shared across all services (shared env variable)
- **Verify:** an unauthenticated request returns 401 from every service

### Phase M7 — Docker
- A `Dockerfile` per module
- `docker-compose.yml` updated: 6 services + Postgres + Redis + Kafka
- **Verify:** `docker compose up` brings up the whole system, an end-to-end `buy` flow works

### Phase M8 — Kubernetes
- A `Deployment` + `Service` (ClusterIP) manifest per service
- `Deployment`/`StatefulSet` + `Service` for Postgres/Redis/Kafka
- `ConfigMap` (app settings), `Secret` (DB password, JWT secret)
- Local testing via minikube/kind
- **Verify:** `kubectl get pods` shows everything `Running`, an end-to-end flow works via port-forward

---

## 6. Next Step

Start with Phase M1: reduce the cross-context JPA relationships to plain IDs. Once that's ready, we continue together.
