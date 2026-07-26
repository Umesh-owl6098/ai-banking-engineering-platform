# AI Financial Crime Operations Platform — Architecture

Technical architecture documentation for the **AI Financial Crime Operations Platform** (`banking-engineering-backend` + React frontend). These pages describe the implemented system as it exists in the repository today.

## Audience

Technical managers, staff engineers, and new contributors who need a fast, accurate picture of runtime flow, AI orchestration, security, data, and deployment.

## Documentation Index

| Document | Summary |
|----------|---------|
| [System Architecture](./system-architecture.md) | End-to-end components, REST vs SSE, major subsystems |
| [Investigation Lifecycle](./investigation-lifecycle.md) | Transaction screening through case closure |
| [AI Agent Orchestration](./ai-agent-orchestration.md) | Supervisor plan, specialist agents, reports, fallback |
| [Security and RBAC](./security-and-rbac.md) | JWT auth, roles, authorization, ownership rules |
| [Data Model](./data-model.md) | Core entities, relationships, optimistic locking |
| [Deployment Architecture](./deployment-architecture.md) | Local topology, Docker Compose, env vars, health checks |

## Quick Reference

| Layer | Technology | Default URL |
|-------|------------|-------------|
| Frontend | React 19, Vite 8, MUI 9 | `http://localhost:5173` |
| Backend | Spring Boot 4, Java 21 | `http://localhost:8080/api` |
| Database | PostgreSQL 16 + pgvector (Docker) | `localhost:5433` |
| AI provider | OpenAI (`OPENAI_API_KEY`) | External API |

## Related Material

- Product spec (implementation reference): [`.kiro/specs/multi-agent-fraud-investigation.md`](../../.kiro/specs/multi-agent-fraud-investigation.md)
- Database migrations: [`backend/src/main/resources/db/migration/`](../../backend/src/main/resources/db/migration/)
