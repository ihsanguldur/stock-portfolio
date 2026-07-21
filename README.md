# Portfolio — Stock Trading Simulator

A stock/portfolio trading simulator written in Java/Spring Boot, started as a modular monolith and migrated into real microservices. The transition followed the phases (M1-M6) in `MICROSERVICE_PLAN_EN.md`; all phases are complete.

## Services

| Service | Port | Owns table(s) | Responsibility |
|---|---|---|---|
| `auth-service` | 8081 | `users`, `refresh_tokens` | register/login/refresh/logout, JWT issuance |
| `wallet-service` | 8082 | `wallets` | balance lookup, withdraw/deposit |
| `stock-service` | 8083 | `stocks` | price lookup, Finnhub integration, price-refresh scheduler |
| `portfolio-service` | 8084 | `holdings` | holding lookup, updating holdings after buy/sell |
| `transaction-service` | 8085 | `transactions` | **buy/sell orchestration (Saga)**, transaction history |
| `audit-service` | 8086 | `audit_logs` | writing audit logs (via Kafka event), admin viewing |

Plus `common` — a 7th Maven module that the other 6 services depend on at compile time: shared JWT security (`JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`), global exception handling, retry helper, audit event/aspect.

Services talk to each other via REST clients (`RestClient`) and Kafka events (audit, notifications, price refresh). `transaction-service` orchestrates the buy/sell flow as a Saga: debit the wallet → add to the portfolio; if the second step fails, a compensating refund is issued back to the wallet.

## Architecture

- Each service owns its own Postgres database (`auth_service_db`, `wallet_service_db`, `stock_service_db`, `portfolio_service_db`, `transaction_service_db`, `audit_service_db`), schemas managed via Liquibase changelogs.
- `stock-service` uses Redis to cache prices.
- Inter-service async communication goes through Kafka (audit events, notifications, price-refresh requests).
- JWT-based stateless authentication, shared across all services via the `common` module.

## Running it

Three environments are supported:

### 1. IntelliJ / native (development)

Each service runs independently via `mvn spring-boot:run` or IntelliJ run configurations, each with its own `application.properties`. Native Postgres (`5432`) and Redis (`6379`) must be running on the host; the 6 databases must already exist (see the Liquibase changelogs).

### 2. Docker Compose

```bash
docker compose up -d --build
```

Brings up Postgres, Redis, Kafka, and all 6 services in containers. `docker/postgres-init/init-multiple-dbs.sh` creates the 6 databases automatically on first boot.

### 3. Kubernetes (minikube)

```bash
./k8s/deploy.sh
```

Builds the images in minikube's own Docker daemon and applies every manifest (namespace → secrets/configmap → postgres/redis/kafka → the 6 services) into the `portfolio` namespace.

App services are exposed via NodePort (JWT auth already protects them, so direct access was left open):

| Service | Port | NodePort |
|---|---|---|
| auth-service | 8081 | 30081 |
| wallet-service | 8082 | 30082 |
| stock-service | 8083 | 30083 |
| portfolio-service | 8084 | 30084 |
| transaction-service | 8085 | 30085 |
| audit-service | 8086 | 30086 |

```bash
minikube ip   # e.g. 192.168.49.2
curl http://$(minikube ip):30081/api/...
```

Postgres/Redis/Kafka stay `ClusterIP` (internal-only, no auth layer of their own). To inspect Postgres with DataGrip or similar:

```bash
kubectl port-forward -n portfolio svc/postgres 5433:5432
```

**`k8s/secrets.yaml` is gitignored** (it holds a real Finnhub API key) and must be created manually before `deploy.sh` can run. It needs:

| Key | Used by | Purpose |
|---|---|---|
| `POSTGRES_PASSWORD` | postgres pod | Postgres superuser password |
| `JWT_SECRET` | all 6 app services | HMAC signing key for JWTs (generate with `openssl rand -base64 32`) |
| `FINNHUB_API_KEY` | stock-service | Finnhub API key for live stock prices |

## Tech stack

Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA, Spring Data Redis, Spring Kafka, Liquibase, PostgreSQL 16, Redis 7, Apache Kafka (KRaft), Maven (multi-module reactor), Docker / Docker Compose, Kubernetes (minikube).

## Planning documents

- `MICROSERVICE_PLAN_EN.md` / `MICROSERVICE_PLAN.md` — monolith-to-microservices transition plan (phases M1-M6)
- `PLAN_EN.md` / `PLAN.md` — overall feature plan for the project