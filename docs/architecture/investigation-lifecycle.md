# Investigation Lifecycle

[← System architecture](./system-architecture.md) · [Architecture index](./README.md) · [AI agent orchestration →](./ai-agent-orchestration.md)

This document describes how a transaction moves from simulation through screening, automated investigation, human review, assignment, and closure.

## End-to-End Flow

```mermaid
flowchart TB
    TX["Transaction received<br/>(simulator or mock API)"]
    PROC["PROCESSING<br/>transaction_screening_results"]
    CLEARED["CLEARED"]
    SUSP["SUSPICIOUS"]
    CRIT["CRITICAL"]
    FAIL["SCREENING_FAILED"]

    NEW["NEW<br/>investigation_cases"]
    RUN["RUNNING"]
    EXEC_FAIL["EXECUTION_FAILED"]
    REPORT["REPORT_GENERATED"]
    AWAIT["AWAITING_REVIEW"]
    ASSIGNED["ASSIGNED"]
    REVIEW["IN_REVIEW"]
    APPROVED["APPROVED"]
    REJECTED["REJECTED"]
    ESC["ESCALATED"]
    CLOSED["CLOSED"]

    TX --> PROC
    PROC --> CLEARED
    PROC --> SUSP
    PROC --> CRIT
    PROC --> FAIL

    SUSP -->|"auto-create"| NEW
    CRIT -->|"auto-create"| NEW

    NEW --> RUN
    RUN --> REPORT
    RUN --> EXEC_FAIL
    EXEC_FAIL -->|"retry"| RUN
    REPORT --> AWAIT

    AWAIT --> ASSIGNED
    AWAIT --> APPROVED
    AWAIT --> REJECTED
    AWAIT --> ESC
    ASSIGNED --> REVIEW
    REVIEW --> APPROVED
    REVIEW --> REJECTED
    REVIEW --> ESC
    APPROVED --> CLOSED
    REJECTED --> CLOSED
    ESC -->|"re-investigate"| RUN
```

**Explanation:** Only **SUSPICIOUS** and **CRITICAL** screening outcomes trigger automatic investigation creation (`InvestigationCreationService`). The automated pipeline moves cases through agent execution and report generation before landing in **AWAITING_REVIEW** for human action.

## Transaction Screening Stage

```mermaid
stateDiagram-v2
    [*] --> PROCESSING: beginProcessing()
    PROCESSING --> CLEARED: no rules triggered
    PROCESSING --> SUSPICIOUS: rules triggered, score less than 80
    PROCESSING --> CRITICAL: score 80 or higher
    PROCESSING --> SCREENING_FAILED: exception
    CLEARED --> [*]
    SUSPICIOUS --> [*]
    CRITICAL --> [*]
    SCREENING_FAILED --> [*]
```

| Status | Meaning | Next step |
|--------|---------|-----------|
| `PROCESSING` | Screening row created | Rules evaluated in `TransactionScreeningService.screen()` |
| `CLEARED` | No rules fired | No investigation |
| `SUSPICIOUS` | Rules fired, score below 80 | Auto investigation (`priority=HIGH`) |
| `CRITICAL` | Score ≥ 80 | Auto investigation (`priority=CRITICAL`) |
| `SCREENING_FAILED` | Screening error | Logged; no auto investigation |

**Rules evaluated:** `FLAGGED_STATUS`, `HIGH_RISK_SCORE`, `LARGE_TRANSFER`, `STRUCTURING`, `RAPID_MOVEMENT`, `HIGH_RISK_COUNTRY`, `PEP_ACTIVITY`, `NEW_ACCOUNT_ACTIVITY`.

## Automated Investigation Pipeline

```mermaid
flowchart TB
    A["Screening completes<br/>SUSPICIOUS or CRITICAL"]
    B["InvestigationCreationService<br/>status = NEW"]
    C["InvestigationAutoExecutionService<br/>async trigger"]
    D["NEW to RUNNING"]
    E["InvestigationExecutionService<br/>run agent plan"]
    F["SSE agent progress events"]
    G["InvestigationReportService<br/>generate report"]
    H["REPORT_GENERATED"]
    I["AWAITING_REVIEW"]
    J["SSE ready-for-review event"]

    A --> B --> C --> D --> E
    E --> F
    E --> G --> H --> I --> J
```

Manual re-execution: `POST /api/investigations/{id}/execute` (ADMIN/SUPERVISOR) from `NEW` or `EXECUTION_FAILED`.

## Investigation Case Statuses

All statuses are stored as strings on `investigation_cases.status` and validated by `InvestigationCaseService`.

### Automated pipeline statuses

| Status | Description |
|--------|-------------|
| `NEW` | Case created; awaiting auto-execution |
| `RUNNING` | Agent execution in progress |
| `REPORT_GENERATED` | Report persisted; transitioning to review |
| `EXECUTION_FAILED` | Pipeline failed; retryable |
| `AWAITING_REVIEW` | Report ready; awaiting supervisor/analyst action |

### Human workflow statuses

| Status | Description |
|--------|-------------|
| `ASSIGNED` | Supervisor assigned case to an analyst |
| `IN_REVIEW` | Assigned analyst actively reviewing |
| `APPROVED` / `REJECTED` | Human decision recorded |
| `ESCALATED` | Escalated for further investigation |
| `CLOSED` | Terminal state after approve/reject |

### Legacy manual statuses

The schema also supports **`OPEN`** and **`INVESTIGATING`** for manually created cases outside the auto pipeline. These coexist with the automated `NEW` → `RUNNING` flow.

## Allowed Transitions

Key transitions enforced by `InvestigationCaseService.ALLOWED_STATUS_TRANSITIONS`:

```mermaid
stateDiagram-v2
    direction LR
    NEW --> RUNNING
    RUNNING --> REPORT_GENERATED
    RUNNING --> EXECUTION_FAILED
    EXECUTION_FAILED --> RUNNING
    REPORT_GENERATED --> AWAITING_REVIEW
    AWAITING_REVIEW --> ASSIGNED
    AWAITING_REVIEW --> APPROVED
    AWAITING_REVIEW --> REJECTED
    AWAITING_REVIEW --> ESCALATED
    ASSIGNED --> IN_REVIEW
    IN_REVIEW --> APPROVED
    IN_REVIEW --> REJECTED
    IN_REVIEW --> ESCALATED
    APPROVED --> CLOSED
    REJECTED --> CLOSED
    ESCALATED --> RUNNING
```

The automated pipeline covers `NEW` through `AWAITING_REVIEW`. Human review covers assignment, analyst review, decisions, and closure. `ESCALATED` can return to `RUNNING` for re-investigation.

## Failure and Retry

```mermaid
flowchart LR
    RUN2["RUNNING"]
    EF["EXECUTION_FAILED"]
    Retry["POST /investigations/{id}/execute"]
    Fields["execution_failure_stage<br/>execution_failure_message<br/>execution_failure_at"]

    RUN2 -->|"exception in pipeline"| EF
    EF --> Fields
    EF --> Retry
    Retry --> RUN2
```

On failure:
- Case status → `EXECUTION_FAILED`
- Failure metadata persisted on `investigation_cases`
- SSE event `EXECUTION_FAILED` published
- Notification `AI_EXECUTION_FAILURE` sent to supervisors
- Metrics incremented (`BankingMetrics`)

## Assignment and Analyst Review

After **AWAITING_REVIEW**, cases enter the analyst workflow:

```mermaid
flowchart LR
    AWAIT2["AWAITING_REVIEW"]
    Claim["Analyst claim<br/>POST .../claim"]
    Assign["Supervisor assign<br/>POST .../assign"]
    ASSIGNED2["ASSIGNED"]
    START["Start review<br/>HumanReviewService"]
    INREV["IN_REVIEW"]
    DECIDE["Approve / Reject / Escalate"]

    AWAIT2 --> Claim
    AWAIT2 --> Assign
    Claim --> ASSIGNED2
    Assign --> ASSIGNED2
    ASSIGNED2 --> START
    START --> INREV
    INREV --> DECIDE
```

**Analyst queue** (`GET /api/analyst-queue`): partitions cases into `myQueue`, `unassigned`, `inReview`, `escalated`, and `allAssigned` (supervisors only).

## Audit Trail

Lifecycle events are recorded in **`investigation_case_events`** via `InvestigationAuditService` (not a separate `audit_events` table). Event types include `CASE_CREATED`, `AGENT_FINDING_PRODUCED`, `HUMAN_DECISION`, `INVESTIGATION_ASSIGNED`, `ANALYST_REVIEW_STARTED`, and others.

## See Also

- [AI Agent Orchestration](./ai-agent-orchestration.md) — what happens during `RUNNING`
- [Security and RBAC](./security-and-rbac.md) — who can assign, claim, and decide
- [Data Model](./data-model.md) — `investigation_cases`, `agent_findings`, `investigation_reports`
