# Deployment Architecture

[← Data model](./data-model.md) · [Architecture index](./README.md)

This document describes how the platform runs locally today and what a production-ready deployment would look like based on **implemented** components. No cloud-specific infrastructure (Kubernetes, managed RDS, load balancers) is defined in the repository.

## Local Development Topology

```mermaid
flowchart TB
    subgraph DevMachine["Developer machine"]
        Browser["Browser<br/>localhost:5173"]
        FE["Vite dev server<br/>npm run dev"]
        BE["Spring Boot<br/>./mvnw spring-boot:run<br/>:8080"]
    end

    subgraph Docker["Docker Compose"]
        PG[("PostgreSQL 16 + pgvector<br/>banking-postgres<br/>host :5433 → container :5432")]
    end

    subgraph External["External network"]
        OAI["OpenAI API<br/>api.openai.com"]
    end

    Browser --> FE
    FE -->|"REST + SSE<br/>/api/*"| BE
    BE -->|"JDBC"| PG
    BE -->|"HTTPS<br/>OPENAI_API_KEY"| OAI
```

**Explanation:** Only PostgreSQL runs in Docker. The frontend and backend are started directly on the host. This matches the current repository—there are no Dockerfiles for the application tiers.

## Production-Ready Topology (logical)

```mermaid
flowchart TB
    subgraph Users["Users"]
        B["Browser clients"]
    end

    subgraph AppTier["Application tier"]
        FEProd["Static frontend<br/>(Vite build → CDN or web server)"]
        BEProd["Spring Boot JAR<br/>(container or VM)"]
    end

    subgraph DataTier["Data tier"]
        PGProd[("PostgreSQL<br/>managed or self-hosted")]
    end

    subgraph External2["External"]
        OAI2["OpenAI API"]
    end

    B --> FEProd
    FEProd --> BEProd
    BEProd --> PGProd
    BEProd --> OAI2
```

For production you would additionally configure: TLS termination, secrets management, CORS origins for your domain, and database backups. These are **recommended practices**, not implemented in-repo.

## Docker Compose

File: [`docker-compose.yml`](../../docker-compose.yml)

```mermaid
flowchart LR
    DC["docker compose up -d"]
    PG2["postgres service<br/>pgvector/pgvector:pg16"]
    Vol["banking_postgres_data volume"]
    HC["healthcheck: pg_isready"]

    DC --> PG2
    PG2 --> Vol
    PG2 --> HC
```

| Setting | Value |
|---------|-------|
| Container name | `banking-postgres` |
| Host port | `5433` |
| Database | `banking_platform` |
| User / password | `banking_user` / `banking_password` |
| Health check | `pg_isready -U banking_user -d banking_platform` every 10s |

## Port and URL Map

| Component | Address | Notes |
|-----------|---------|-------|
| Frontend (dev) | `http://localhost:5173` | Vite dev server |
| Backend API | `http://localhost:8080/api` | Spring Boot |
| Actuator health | `http://localhost:8080/actuator/health` | Public |
| Actuator info | `http://localhost:8080/actuator/info` | Public |
| Prometheus | `http://localhost:8080/actuator/prometheus` | ADMIN/SUPERVISOR only |
| PostgreSQL | `localhost:5433` | Docker-mapped |

## Startup Sequence

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant DC as Docker Compose
    participant PG as PostgreSQL
    participant BE as Spring Boot
    participant FW as Flyway
    participant FE as Vite

    Dev->>DC: docker compose up -d
    DC->>PG: start postgres, wait for healthcheck
    Dev->>BE: ./mvnw spring-boot:run
    BE->>PG: connect JDBC
    BE->>FW: migrate schema (V1–V19)
    FW-->>BE: schema ready
    BE->>BE: Tomcat :8080, register controllers
    Dev->>FE: npm run dev
    FE->>BE: API calls to localhost:8080/api
```

Always ensure **only one backend instance** listens on port 8080. A stale JVM running an older build is a common cause of missing API routes (e.g., notifications returning 401).

## Environment Variables

| Variable | Required | Purpose |
|----------|----------|---------|
| `OPENAI_API_KEY` | Optional | Report LLM, chat, embeddings. Without it, reports use deterministic fallback |
| `VITE_API_URL` | Optional (frontend) | Override API base URL (default `http://localhost:8080/api`) |
| `VITE_PROJECT_ID` | Optional (frontend) | Override default project UUID |

### Backend configuration (`application.properties`)

| Property | Default | Notes |
|----------|---------|-------|
| `server.port` | `8080` | HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5433/banking_platform` | JDBC URL |
| `spring.datasource.username` | `banking_user` | DB user |
| `spring.datasource.password` | `banking_password` | DB password |
| `auth.jwt.secret` | Dev secret in properties | **Change for production** |
| `auth.jwt.expiration-ms` | `86400000` (24h) | Token lifetime |
| `auth.demo-users.enabled` | `true` | Seed demo accounts |
| `spring.ai.openai.chat.options.model` | `gpt-4.1-mini` | Default chat/report model |
| `simulation.transactions.interval-ms` | `3000` | Simulation tick interval |
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | Actuator exposure |

Database credentials can be overridden via standard Spring `spring.datasource.*` environment variables or an external config file.

## Health Checks

```mermaid
flowchart TB
    Act["/actuator/health"]
    DB["db<br/>(Spring Boot JDBC)"]
    Exec["investigationExecution<br/>InvestigationExecutionHealthIndicator"]
    OAI3["openAiConfiguration<br/>OpenAiConfigurationHealthIndicator"]
    RAG["ragKnowledge<br/>RagKnowledgeHealthIndicator"]

    Act --> DB
    Act --> Exec
    Act --> OAI3
    Act --> RAG
```

| Indicator | What it checks |
|-----------|----------------|
| `db` | PostgreSQL connectivity |
| `investigationExecution` | Investigation pipeline readiness |
| `openAiConfiguration` | Whether `OPENAI_API_KEY` is configured |
| `ragKnowledge` | Knowledge base / embedding readiness |

`management.endpoint.health.show-details=always` exposes component status in the health response.

## Frontend Build (non-dev)

```bash
cd frontend
npm run build    # output → frontend/dist/
```

Serve `frontend/dist/` via any static file host. Set `VITE_API_URL` at **build time** to point to your backend API.

## Backend Build (non-dev)

```bash
cd backend
./mvnw package -DskipTests
java -jar target/banking-engineering-backend-*.jar
```

Requires PostgreSQL reachable at the configured JDBC URL before startup (Flyway runs on boot).

## Observability

| Mechanism | Location |
|-----------|----------|
| Prometheus metrics | `/actuator/prometheus` |
| Custom counters | `BankingMetrics` — execution failures, report fallbacks |
| Correlation IDs | `CorrelationIdFilter` |
| Operations Center API | `GET /api/operations/center` — aggregates health for UI |

## Network and CORS

Backend CORS allows `http://localhost:5173` for `/api/**` and `/actuator/**`. Production deployments must update `SecurityConfig` / `CorsConfig` with the actual frontend origin.

SSE streams (`/api/simulation/live`, `/api/investigations/live`, `/api/notifications/live`) use long-lived HTTP connections through the same backend port.

## What Is Not in the Repository

The following are **not implemented** and should not be assumed:

- Application Dockerfiles or Kubernetes manifests
- CI/CD pipeline configuration
- Cloud-managed services (AWS, GCP, Azure)
- Redis, message queues, or separate worker processes
- Multi-region or horizontal backend scaling configuration

## See Also

- [System Architecture](./system-architecture.md) — component overview
- [Security and RBAC](./security-and-rbac.md) — JWT and CORS
- [Data Model](./data-model.md) — Flyway migrations and PostgreSQL schema
