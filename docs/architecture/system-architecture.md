# System Architecture

[← Architecture index](./README.md) · [Investigation lifecycle →](./investigation-lifecycle.md)

The platform is a **financial crime operations workbench**: simulated transactions are screened, suspicious activity triggers automated multi-agent investigations, supervisors and analysts review outcomes, and operators monitor health through dashboards and notifications.

## High-Level Architecture

```mermaid
flowchart TB
    subgraph Client["Browser"]
        UI["React + Vite Frontend<br/>MUI, React Router"]
    end

    subgraph Backend["Spring Boot Backend :8080"]
        Auth["JWT Authentication<br/>AuthController, JwtAuthenticationFilter"]
        REST["REST Controllers<br/>/api/*"]
        SSE["SSE Hubs<br/>simulation, investigations, notifications"]
        Sim["Transaction Simulator<br/>TransactionSimulationService"]
        Screen["Screening Engine<br/>TransactionScreeningService"]
        InvCreate["Investigation Creation<br/>InvestigationCreationService"]
        Orch["Investigation Engine<br/>InvestigationExecutionService"]
        Sup["AI Orchestrator<br/>SupervisorAgentService"]
        Agents["Specialist Agents<br/>Fraud, KYC, AML, Compliance"]
        Report["Report Generator<br/>InvestigationReportService"]
        Assign["Assignment & Analyst Queue<br/>InvestigationAssignmentService"]
        Notify["Notification Service<br/>NotificationService, NotificationPublisher"]
        Ops["Operations Metrics<br/>OperationsDashboardService"]
        Chat["Chat & RAG<br/>ChatService, KnowledgeSearchService"]
    end

    subgraph External["External"]
        OpenAI["OpenAI API<br/>reports, chat, embeddings"]
    end

    subgraph Data["Data"]
        PG[("PostgreSQL<br/>Flyway migrations")]
    end

    UI -->|"REST (sync)"| REST
    UI -->|"SSE (async push)"| SSE
    REST --> Auth
    REST --> Sim
    REST --> Screen
    REST --> Orch
    REST --> Assign
    REST --> Notify
    REST --> Ops
    REST --> Chat

    Sim --> Screen
    Screen --> InvCreate
    InvCreate --> Orch
    Orch --> Sup
    Sup --> Agents
    Agents --> Report
    Report --> OpenAI
    Chat --> OpenAI

    Sim --> SSE
    Orch --> SSE
    Notify --> SSE

    Backend --> PG
```

**Explanation:** The browser talks to Spring Boot over **synchronous REST** for commands and queries, and over **Server-Sent Events (SSE)** for live transaction feeds, investigation execution progress, and user notifications. Screening and investigation pipelines run inside the backend; OpenAI is used for report narrative, chat, and knowledge embeddings—not for the deterministic specialist agents.

## Communication Patterns

```mermaid
flowchart LR
    subgraph Sync["Synchronous (REST)"]
        S1["Login, CRUD, assign, review decisions"]
        S2["Dashboard KPIs, analyst queue, reports"]
    end

    subgraph Async["Event-driven / async"]
        E1["Simulation scheduler → screening"]
        E2["ScreeningCompletedEvent → auto investigation"]
        E3["InvestigationAutoCreatedEvent → async execution"]
        E4["SSE hubs push live updates to UI"]
        E5["NotificationPublisher → DB + SSE"]
        E6["Waiting-notification scheduler (5 min)"]
    end

    Sync -->|"HTTP request/response"| BackendNode["Spring Boot"]
    Async -->|"Spring events, @Async, @Scheduled"| BackendNode
    BackendNode -->|"SSE stream"| Browser["Browser"]
```

| Pattern | Examples | Latency model |
|---------|----------|---------------|
| **Synchronous REST** | `POST /api/auth/login`, `GET /api/dashboard/operations`, `POST /api/investigations/{id}/assign` | Client waits for response |
| **Async in-process** | Auto-execution after investigation creation (`InvestigationAutoExecutionTrigger`) | Returns immediately; work continues in thread pool |
| **SSE push** | `/api/simulation/live`, `/api/investigations/live`, `/api/notifications/live` | Server pushes events as they occur |
| **Scheduled** | Transaction simulation interval (3s), investigation-waiting notifications (5 min) | Background polling |

## Frontend Architecture

```mermaid
flowchart TB
    App["App.tsx<br/>BrowserRouter"]
    Shell["AppShell.tsx<br/>nav + NotificationBell"]
    AuthCtx["AuthContext"]
    NotifCtx["NotificationContext"]

    Pages["Pages<br/>Dashboard, Analyst Queue, Investigations,<br/>Live Transactions, Notifications, Operations"]
    Services["Services<br/>investigationService, notificationService,<br/>simulationService, dashboardService"]
    Hooks["SSE Hooks<br/>useSimulationLiveStream<br/>useInvestigationLiveStream<br/>useNotificationStream"]
    API["api.ts<br/>Axios → VITE_API_URL or localhost:8080/api"]

    App --> Shell
    App --> AuthCtx
    App --> NotifCtx
    Shell --> Pages
    Pages --> Services
    Pages --> Hooks
    Services --> API
    Hooks --> API
    NotifCtx --> Hooks
```

Default API base: `http://localhost:8080/api` (override with `VITE_API_URL`). Default project: `8c0c0dee-dd8e-4419-bef3-a2e93c10a726` (`VITE_PROJECT_ID`).

## Backend Module Map

| Package | Responsibility |
|---------|----------------|
| `auth` | JWT login, `CurrentUserService`, demo user seeding |
| `simulation` | Mock transaction generation, demo scenarios, simulation SSE |
| `screening` | Rule-based transaction screening, status resolution |
| `investigation` | Case lifecycle, findings, execution, review, assignment |
| `investigation.supervisor` | Execution plan (`SupervisorAgentService`) |
| `investigation.fraud/kyc/aml/compliance` | Deterministic specialist analysis |
| `investigation.report` | LLM + deterministic report generation |
| `investigation.assignment` | Analyst queue, assign / claim / unassign |
| `notification` | Persisted notifications, SSE, domain hooks |
| `dashboard` / `operations` | Operations KPIs and platform health |
| `chat` / `knowledge` | Conversational AI and RAG over uploaded documents |
| `mockdata` | Demo customers and transactions |
| `observability` | Metrics, correlation IDs, custom health indicators |

## Live SSE Channels

| Endpoint | Hub | Event purpose |
|----------|-----|---------------|
| `GET /api/simulation/live` | `TransactionSimulationEventHub` | Live transaction + screening updates |
| `GET /api/investigations/live` | `InvestigationNotificationHub` | Investigation created, execution progress |
| `GET /api/notifications/live` | `NotificationEventHub` | User-scoped notification push |
| `POST /api/chat/stream` | `StreamingChatService` | Token streaming for project chat |

All SSE endpoints require JWT authentication (Bearer token via fetch stream, not native `EventSource`).

## OpenAI vs Deterministic Logic

```mermaid
flowchart LR
    subgraph Deterministic["Deterministic (no LLM)"]
        D1["Transaction screening rules"]
        D2["Supervisor execution plan"]
        D3["Fraud / KYC / AML / Compliance agents"]
        D4["DeterministicInvestigationReportGenerator"]
        D5["ExplainabilityService"]
    end

    subgraph LLM["OpenAI (optional)"]
        L1["Investigation report narrative"]
        L2["Project chat agents"]
        L3["Knowledge embeddings & search"]
    end

    Orch2["InvestigationExecutionService"] --> Deterministic
    Report2["InvestigationReportService"] --> Deterministic
    Report2 -->|"if OPENAI_API_KEY set"| LLM
    Report2 -->|"on failure or missing key"| D4
```

Specialist agents produce structured **findings** stored in PostgreSQL. Report generation merges LLM narrative with deterministic facts; failures fall back to pure deterministic output and emit an `OPENAI_FALLBACK_MODE` notification.

## Operations Surfaces

| Surface | API | Purpose |
|---------|-----|---------|
| Operations Dashboard | `GET /api/dashboard/operations` | Fraud ops KPIs, active cases, live alerts |
| Operations Center | `GET /api/operations/center` | Platform health (DB, SSE, OpenAI), error counters |
| Actuator | `/actuator/health`, `/actuator/prometheus` | Infrastructure health and Prometheus metrics |

## See Also

- [Investigation Lifecycle](./investigation-lifecycle.md) — status transitions from screening to closure
- [AI Agent Orchestration](./ai-agent-orchestration.md) — supervisor plan and agent responsibilities
- [Deployment Architecture](./deployment-architecture.md) — ports, Docker Compose, environment variables
