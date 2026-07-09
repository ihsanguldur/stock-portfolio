# Stock Portfolio Application — Learning-Focused Project Plan

> **Goal:** This project looks like a "portfolio management app," but the real goal is to **learn Java and Spring Boot through problems you'd actually encounter at a banking/fintech company.** The domain (buying/selling stocks) is just a vehicle — the real payoff is hands-on practice with concurrency, caching, scheduling, auditing, and security.

---

## 1. Assumptions

The following weren't fully specified, so I'm flagging them as assumptions — correct me if any are wrong:

| Topic | Assumption |
|---|---|
| Stock price API | **Finnhub** (free tier, near-real-time data, simple REST API). Switching to Alpha Vantage or Twelve Data later only requires changing the `StockPriceClient` implementation. |
| Currency | Single currency (USD or TRY), no multi-currency support (out of scope). |
| Virtual balance | Every user gets a fixed starting balance on registration (e.g., 100,000 units of virtual money). |
| Deployment | Local development only (Postgres + Redis via Docker Compose); production deployment is out of scope for now. |

---

## 2. Tech Stack (already set up in pom.xml)

- **Java 21**, **Spring Boot 4.1.0**, Maven
- `spring-boot-starter-webmvc` — REST API
- `spring-boot-starter-data-jpa` + `postgresql` — persistent storage
- `spring-boot-starter-security` + `jjwt-*` (0.12.6) — JWT auth (access + refresh tokens)
- `spring-boot-starter-data-redis` + `spring-boot-starter-cache` — caching
- `spring-boot-starter-aspectj` — AOP (for auditing/logging; the renamed "aop starter")
- `spring-boot-starter-validation` — request validation
- `lombok`, `spring-boot-devtools`

**Note:** No extra dependency is needed for `@Scheduled`/`@Async` — `spring-context` is already included.

---

## 3. Learning Phases (Roadmap)

Each phase builds on the previous one. Following the order is recommended — each phase is an independently verifiable milestone.

### Phase 1 — Basic CRUD and layered architecture
- Build the Entity → Repository → Service → Controller → DTO flow
- `Stock` and `User` entities (without auth for now)
- **Verify:** `GET /api/stocks` works and can be tested via Postman/curl

### Phase 2 — Security & JWT Auth
- Register/login flow, access + refresh token
- Role-based authorization (`USER`, `ADMIN`)
- **Verify:** A protected endpoint returns 401 without a token, and 200 with a valid token after login

### Phase 3 — External API Integration
- Fetch real prices from Finnhub (using `RestClient`, no extra dependency needed)
- Error handling: API timeout, rate limiting, invalid symbol
- **Verify:** A valid symbol returns a current price; an invalid symbol returns a meaningful error

### Phase 4 — Redis Caching
- Cache price data (`@Cacheable`, with TTL)
- Cache eviction strategy
- **Verify:** A second request for the same symbol doesn't hit the external API (confirm via logs)

### Phase 5 — Concurrency & Transaction Management (the heart of banking systems)
- Race condition scenario: two simultaneous "buy" requests can leave the balance/holding inconsistent
- Optimistic locking with `@Version` (on the `Wallet` and `Holding` entities)
- Pessimistic locking alternative (`@Lock(PESSIMISTIC_WRITE)`) for critical balance updates
- Discussion of `@Transactional` isolation levels
- **Verify:** Write a multi-threaded test for simultaneous purchases and confirm the balance never goes negative / stays consistent

### Phase 6 — Scheduling & Async
- `@Scheduled` job that periodically refreshes prices for actively held stocks (e.g., every minute)
- `@Async` for sending a post-transaction "notification" (simulated, doesn't need to be a real email)
- **Verify:** Confirm the job runs periodically in the logs, and that the async method doesn't block the main request thread

### Phase 7 — Auditing & Logging (AOP)
- Custom `@Audited` annotation + `@Around` aspect
- Every money-moving action (`buy`/`sell`/`deposit`) is recorded in an `AuditLog` table with who/when/what changed
- **Verify:** Confirm a record appears in the `audit_log` table after a buy transaction

### Phase 8 — Error Handling & Testing
- Global `@RestControllerAdvice` + `ProblemDetail` (RFC 7807)
- Unit tests (Mockito), integration tests (Testcontainers with real Postgres/Redis)
- **Verify:** Test suite passes; error scenarios return meaningful HTTP status/body

### Phase 9 — (Optional, advanced)
- Bring up the full stack with Docker Compose
- `spring-boot-starter-actuator` for health checks/metrics
- Move to an event-driven architecture with Kafka/RabbitMQ (only if you actually want to)

---

## 4. Domain Model (Entities)

| Entity | Fields (summary) | Relationship |
|---|---|---|
| `User` | id, email, passwordHash, role, walletBalance, createdAt | 1—1 Wallet, 1—N Transaction |
| `RefreshToken` | id, token, userId, expiryDate, revoked | N—1 User |
| `Wallet` | id, userId, balance, `@Version` | 1—1 User |
| `Stock` | id, symbol, name, lastPrice, lastUpdated | 1—N Holding |
| `Holding` | id, userId, stockId, quantity, avgCost, `@Version` | N—1 User, N—1 Stock |
| `Transaction` | id, userId, stockId, type (BUY/SELL), quantity, price, timestamp | N—1 User, N—1 Stock |
| `AuditLog` | id, actorUserId, action, entityType, entityId, beforeState, afterState, timestamp | — |

---

## 5. API Endpoints

### Auth (`/api/auth`) — public
| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Register a new user (starting balance assigned automatically) |
| POST | `/api/auth/login` | Returns access + refresh tokens |
| POST | `/api/auth/refresh` | New access token via refresh token |
| POST | `/api/auth/logout` | Invalidates the refresh token (authenticated) |

### User (`/api/users`) — authenticated
| Method | Path | Description |
|---|---|---|
| GET | `/api/users/me` | Profile info |
| PUT | `/api/users/me` | Update profile |

### Wallet (`/api/wallet`) — authenticated
| Method | Path | Description |
|---|---|---|
| GET | `/api/wallet` | Current balance |
| POST | `/api/wallet/deposit` | Deposit virtual money (for testing; wouldn't exist in prod) |

### Stocks (`/api/stocks`) — authenticated
| Method | Path | Description |
|---|---|---|
| GET | `/api/stocks` | Searchable/listed stocks |
| GET | `/api/stocks/{symbol}` | Current price (from Redis cache, falls back to external API) |

### Portfolio (`/api/portfolio`) — authenticated
| Method | Path | Description |
|---|---|---|
| GET | `/api/portfolio` | All holdings + total value + profit/loss |
| GET | `/api/portfolio/holdings/{symbol}` | Detail for a single holding |

### Transactions (`/api/transactions`) — authenticated
| Method | Path | Description |
|---|---|---|
| POST | `/api/transactions/buy` | `{ symbol, quantity }` — simulated buy |
| POST | `/api/transactions/sell` | `{ symbol, quantity }` — simulated sell |
| GET | `/api/transactions` | Transaction history (paginated) |
| GET | `/api/transactions/{id}` | Detail for a single transaction |

### Admin (`/api/admin`) — ADMIN role
| Method | Path | Description |
|---|---|---|
| GET | `/api/admin/audit-logs` | All audit records (paginated/filterable) |

---

## 6. Security Details

- **Access token:** short-lived (e.g., 15 min), sent as `Authorization: Bearer <token>` on every request
- **Refresh token:** long-lived (e.g., 7 days), stored in DB (`RefreshToken` table), with rotation (the old one is invalidated on every refresh)
- **Password:** `BCryptPasswordEncoder`
- **Filter chain:** `JwtAuthenticationFilter` validates the token on every request and sets the authentication into the `SecurityContext`
- Public endpoints: `/api/auth/**`; everything else requires authentication

---

## 7. External API Integration (assuming Finnhub)

- `StockPriceClient` interface + `FinnhubStockPriceClient` implementation
- Uses Spring 6.1+ `RestClient` (synchronous, no extra dependency required)
- The API key is read from an environment variable (`${FINNHUB_API_KEY}`), never hardcoded in `application.yml`
- Error scenarios: timeout, 429 (rate limit), 404 (invalid symbol) — all mapped to custom exceptions

---

## 8. Caching Strategy

- `@Cacheable("stockPrices")` on `StockPriceService.getPrice(symbol)`
- TTL: 60 seconds in Redis (to avoid hitting rate limits) — configured via `RedisCacheConfiguration`
- The scheduled job from Phase 6 proactively refreshes the cache for actively held stocks (`@CacheEvict` + re-fetch)

---

## 9. Concurrency & Transaction Management

**Scenario:** If a user sends two simultaneous "buy" requests (double-click, retry, etc.), a race condition between the balance check and the balance deduction can leave the balance negative or the holding inconsistent.

**Layers of defense:**
1. `@Version` (optimistic locking) on the `Wallet` and `Holding` entities — a conflicting update throws `OptimisticLockException`, which the service layer either retries or surfaces to the user as an error
2. Pessimistic locking alternative (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) discussed for critical paths like balance deduction
3. The entire buy/sell flow runs inside a single `@Transactional` method, with an appropriate isolation level

---

## 10. Scheduling & Async

- `@Scheduled(fixedRate = 60000)` — periodically refreshes prices for stocks with active holdings
- `@Async` — sends a post-transaction "notification" (log/console simulation) without blocking the main request thread
- `@EnableScheduling` and `@EnableAsync` added to the main application class

---

## 11. Auditing & Logging (AOP)

- A custom `@Audited` annotation is applied to money-moving service methods (`buy`, `sell`, `deposit`)
- An `@Aspect` class wraps these methods with `@Around` advice, writing the before/after state to the `AuditLog` table
- In banking, the audit trail answering "who changed what, and when" is critical — so logging is centralized and consistent via AOP instead of scattering manual log statements across every method

---

## 12. Error Handling

- `@RestControllerAdvice` + `ProblemDetail` (RFC 7807) for a standard error format
- Custom exceptions: `InsufficientBalanceException`, `StockNotFoundException`, `InvalidTokenException`, `OptimisticLockConflictException`

---

## 13. Local Environment Setup

`docker-compose.yml` (to be added in Phase 9, though it can be set up earlier too):
- `postgres:16` — portfolio DB
- `redis:7` — cache

Datasource, Redis, and JWT secret config in `application.yml` are read from environment variables (secrets are never committed to the repo).

---

## 14. Testing Strategy

- **Unit tests:** Service layer, with the repository/external client mocked via Mockito
- **Integration tests:** `@SpringBootTest` + Testcontainers (real Postgres/Redis, not mocks)
- **Concurrency test:** A dedicated test that fires parallel requests at the same endpoint from multiple threads to verify consistency (the verification step for Phase 5)

---

## 15. Next Step

Start with Phase 1: create the `Stock` and `User` entities and the basic repository/service/controller layers. Let's continue together once you're ready.
