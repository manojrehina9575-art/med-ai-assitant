# Med-AI Clinical

> **Enterprise clinical documentation infrastructure — AI drafts radiology and pathology reports, licensed clinicians review and cryptographically sign every output.**

[![Live Site](https://img.shields.io/badge/site-medaiclinical.com-0b6953?style=flat-square)](https://medaiclinical.com)
[![App](https://img.shields.io/badge/app-app.medaiclinical.com-0b6953?style=flat-square)](https://app.medaiclinical.com)

Med-AI Clinical is a multi-tenant, FHIR R4-native platform for hospitals and diagnostic chains. It synthesises radiology studies (DICOM, X-Ray, CT, MRI) and laboratory biomarkers into structured clinical drafts using an agentic AI workflow. **No report reaches a patient record without authenticated physician sign-off.**

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Monorepo Structure](#monorepo-structure)
- [Backend Modules](#backend-modules)
- [Frontend Pages](#frontend-pages)
- [Agentic AI Workflow](#agentic-ai-workflow)
- [FHIR R4 & Compliance](#fhir-r4--compliance)
- [Multi-Tenancy & Security](#multi-tenancy--security)
- [Quick Start](#quick-start)
- [Environment Variables](#environment-variables)
- [Deployment](#deployment)
- [API Reference](#api-reference)
- [Roles & RBAC](#roles--rbac)
- [Roadmap](#roadmap)

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│  Cloudflare Pages          medaiclinical.com (marketing, static HTML) │
│  Cloudflare Workers        wrangler.jsonc — no build step             │
└──────────────────────────────────────────────────────────────────────┘
                                    │
┌──────────────────────────────────────────────────────────────────────┐
│  React 18 SPA              app.medaiclinical.com                      │
│  Vite · TypeScript · TailwindCSS · shadcn/ui · Zustand · Zod         │
└──────────────────────────────────────────────────────────────────────┘
                                    │  REST / JWT
┌──────────────────────────────────────────────────────────────────────┐
│  Spring Boot 3.3 API       :8080                                      │
│  Java 21 Virtual Threads · Spring AI · LangGraph4J                   │
│  SpringDoc OpenAPI (Swagger) · Actuator + Prometheus                  │
└──────────────────────────────────────────────────────────────────────┘
          │                        │                        │
  PostgreSQL 16 + pgvector   Redis (rate-limit)      S3 / local storage
  pgvector extension         distributed RPM cap      DICOM, labs, PDFs
  Row-Level Security         per-tenant AI budget     SSE-S3/KMS at rest
  Flyway migrations          (optional, replicas)
  6-yr partition audit log
```

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA, Spring AI |
| **AI / Agentic** | LangGraph4J workflow graph, Groq (Qwen 3.6-27B / Llama 3.3-70B) or OpenAI GPT-4o, ONNX all-MiniLM-L6-v2 local embeddings |
| **Frontend** | React 18, TypeScript, Vite, TailwindCSS, shadcn/ui (Radix), Zustand, Zod, Axios |
| **DICOM** | dicom-parser (client-side) |
| **Database** | PostgreSQL 16 + pgvector, Flyway migrations, RLS tenant isolation |
| **Cache** | Redis (distributed AI rate-limit window for replicas) |
| **Storage** | Local disk or S3-compatible (AWS S3, Cloudflare R2, MinIO) — SSE-S3/KMS encrypted |
| **Standards** | FHIR R4 (DiagnosticReport, Observation, ImagingStudy), ICD-10, SNOMED CT, LOINC, RxNorm |
| **Auth** | JWT access (15 min) + httpOnly refresh cookie (7 days), BCrypt, RBAC |
| **Observability** | Spring Actuator, Prometheus (`/actuator/prometheus`), structured audit log |
| **Containerisation** | Docker, Docker Compose, Kubernetes (Helm charts in `helm/`) |
| **Marketing** | Static HTML, Cloudflare Workers Static Assets, `wrangler.jsonc` |
| **API Docs** | SpringDoc OpenAPI — `http://localhost:8080/swagger-ui.html` |

---

## Monorepo Structure

```
med-ai-assistant/
├── backend/                  # Spring Boot API (Java 21)
│   └── src/main/java/com/medai/
│       ├── agent/            # LangGraph4J clinical workflow graph + MCP tools
│       ├── analysis/         # Async AI analysis engine (image + blood report)
│       ├── analytics/        # Usage + cost analytics
│       ├── audit/            # Append-only audit ledger (6-yr retention, partitioned)
│       ├── auth/             # JWT auth, tenant registration, RBAC
│       ├── billing/          # Per-tenant usage invoicing (daily cron)
│       ├── chat/             # AI chat with memory
│       ├── clinical/         # Clinical entities, safety checks
│       ├── common/           # Shared DTOs, exceptions, pagination
│       ├── compliance/       # Consent, PHI redaction, crypto, retention
│       ├── export/           # Report PDF/FHIR export
│       ├── fhir/             # FHIR R4 facade (DiagnosticReport, Observation)
│       ├── finetuning/       # Dataset curation + fine-tuning pipeline
│       ├── knowledge/        # RAG knowledge base (pgvector)
│       ├── notification/     # Critical-result escalation + notifications
│       ├── patient/          # Patient CRUD
│       ├── report/           # Clinical report lifecycle (draft → signed)
│       ├── tenant/           # Tenant management
│       ├── terminology/      # ICD-10, SNOMED, LOINC, RxNorm validation
│       ├── upload/           # File upload (DICOM, labs, PDF) + storage abstraction
│       └── user/             # User management
├── frontend/                 # React 18 SPA
│   └── src/
│       ├── pages/            # 15 application pages (see below)
│       ├── components/       # Shared UI components
│       ├── stores/           # Zustand state stores
│       ├── services/         # Axios API clients
│       ├── types/            # TypeScript type definitions
│       └── utils/            # Helpers and utilities
├── marketing/                # Static marketing site (medaiclinical.com)
│   ├── index.html            # Single-page HTML — no build step
│   ├── og-image.jpg          # Social preview (1200×630)
│   ├── favicon.svg           # Brand SVG favicon
│   ├── sitemap.xml           # XML sitemap
│   ├── robots.txt
│   ├── site.webmanifest
│   └── _headers              # Cloudflare Pages security + cache headers
├── docker/                   # Docker Compose + Dockerfiles
│   ├── docker-compose.yml    # postgres + backend + frontend
│   ├── Dockerfile.backend
│   └── Dockerfile.frontend
├── k8s/                      # Kubernetes manifests
│   ├── backend-deployment.yaml
│   ├── frontend-deployment.yaml
│   ├── postgres-statefulset.yaml
│   ├── redis-deployment.yaml
│   ├── ingress.yaml          # TLS ingress
│   ├── hpa.yaml              # Horizontal Pod Autoscaler
│   ├── network-policy.yaml
│   └── configmap.yaml
├── helm/                     # Helm charts
├── load-tests/               # Load testing scripts
├── wrangler.jsonc            # Cloudflare Workers config (marketing site)
├── .env.example              # All environment variables documented
└── MVP-PLAN.md               # Detailed 8-phase build plan
```

---

## Backend Modules

### `agent` — Agentic Clinical Workflow
LangGraph4J-powered state graph that orchestrates multi-step clinical reasoning.

**Built-in tools:**
| Tool | Description |
|------|-------------|
| `SearchPatientHistoryTool` | Retrieves prior studies and clinical context |
| `WritePrescriptionTool` | Generates prescriptions with safety checks |
| `OrderLabTestTool` | Issues lab orders with ontology validation |
| `ScheduleAppointmentTool` | Books appointments |
| `SendNotificationTool` | Dispatches critical-result alerts |
| `GenerateDischargeSummaryTool` | Produces discharge summaries |

### `analysis` — AI Analysis Engine
Asynchronous AI analysis pipeline for medical images (DICOM) and blood reports. Includes:
- Configurable thread pool (core 4, max 8, queue 50)
- **Reaper job** — recovers `PENDING`/`PROCESSING` analyses after crashes (runs every 60 s)
- Per-tenant AI rate limiting (RPM + daily USD cap)
- Model pricing table (Qwen 3.6, Llama 3.3, GPT-4o, GPT-4o-mini)

### `audit` — Immutable Audit Ledger
- AOP-based `@Audited` aspect
- Buffered batch writes (queue 10 000, flush every 500 ms, batch size 500)
- Monthly PostgreSQL partitions created 3 months ahead
- 6-year retention policy (nightly retention cron)

### `compliance` — DPDP / HIPAA
| Sub-module | Function |
|------------|---------|
| `consent` | Granular per-patient consent tracking |
| `crypto` | AES-256 field-level encryption |
| `phi` | Client-side PHI de-identification / redaction |
| `retention` | Data retention enforcement (nightly cron) |

### `fhir` — FHIR R4 Facade
Native FHIR R4 resources: `DiagnosticReport`, `Observation`, `ImagingStudy`. ABDM-compatible output. Mapper + controller + service layers.

### `knowledge` — RAG Knowledge Base
Local ONNX embeddings (`all-MiniLM-L6-v2`) stored in pgvector. No API call for embeddings — no PHI leaves the server.

### `terminology` — Medical Ontologies
ICD-10, SNOMED CT, LOINC validation. RxNorm integration via NLM RxNav API (no PHI sent — drug name only, configurable timeout 1 500 ms).

### `billing` — Usage Invoicing
Per-tenant billing based on AI token consumption. Daily cron (03:00) invoices tenants whose cycle closes that day (period anchored per tenant, max 28 days).

### `notification` — Critical Result Escalation
- 15-minute acknowledgement window (configurable)
- Sweep every 60 s — widens notification audience on non-acknowledgement
- Full audit trail of every notification sent

---

## Frontend Pages

| Page | Route | Description |
|------|-------|-------------|
| `LoginPage` | `/login` | JWT authentication |
| `RegisterPage` | `/register` | Hospital/tenant onboarding |
| `DashboardPage` | `/` | Overview, metrics, activity feed |
| `WorklistPage` | `/worklist` | Radiology reading worklist (awaiting sign-off) |
| `AnalysisPage` | `/analysis` | Medical image AI analysis results |
| `BloodReportPage` | `/blood-report` | Lab / blood report synthesis |
| `PatientsPage` | `/patients` | Patient list, search, management |
| `UploadPage` | `/upload` | Drag-and-drop DICOM/lab/PDF upload |
| `ChatPage` | `/chat` | AI clinical chat with session memory |
| `KnowledgeBasePage` | `/knowledge` | RAG knowledge base management |
| `WorkflowsPage` | `/workflows` | Agentic workflow monitoring |
| `CompliancePage` | `/compliance` | DPDP/HIPAA consent, audit, PHI redaction |
| `FineTuningPage` | `/fine-tuning` | Dataset curation + model fine-tuning |
| `ObservabilityPage` | `/observability` | Prometheus metrics, analysis queue |
| `SettingsPage` | `/settings` | Tenant/user configuration |

---

## Agentic AI Workflow

```
Study/Lab arrives (PACS / LIS)
        │
        ▼
  AnalysisService (async pool)
        │
        ▼
  ClinicalWorkflowGraph  ◄── LangGraph4J state machine
        │
        ├── Node: patient_history_lookup
        ├── Node: image_synthesis / lab_synthesis
        ├── Node: terminology_validation  (ICD-10, SNOMED, LOINC)
        ├── Node: safety_check            (drug interactions, contraindications)
        ├── Node: draft_generation
        └── Node: critical_result_check   ──► NotificationService (STAT alert)
                 │
                 ▼
        ReportService  ──► DiagnosticReport draft (status: DRAFT)
                 │
                 ▼
        Clinician review on WorklistPage
                 │
                 ▼
        Cryptographic sign-off  ──► Report sealed (SHA-256 timestamp + MCI reg)
                 │
                 ▼
        FHIR R4 export  ──► EHR / ABDM gateway
```

AI providers are **swappable** — Groq (Qwen/Llama), OpenAI, Azure OpenAI, or any OpenAI-compatible endpoint — configured via `AI_BASE_URL`.

---

## FHIR R4 & Compliance

- **FHIR R4** — `DiagnosticReport`, `Observation`, `ImagingStudy` resources, ABDM-compatible
- **ICD-10 / SNOMED CT / LOINC** — all conclusions and lab parameters validated against verified ontologies; unmatched terms remain plain text (no hallucinated codes)
- **RxNorm** — drug name lookup via NLM RxNav (no PHI in request)
- **DPDP Act 2023** — consent tracking, data residency, retention enforcement
- **HIPAA Security Rule** — encryption at rest (SSE-S3/KMS), httpOnly refresh cookies, audit trail
- **Prescription safety** — cross-reactivity, duplicate therapy, contraindication screening, daily dose caps

---

## Multi-Tenancy & Security

### Dual-Role Database Architecture

```
DB_USERNAME (owner)   — used ONLY by Flyway for schema migrations
DB_APP_USERNAME       — non-superuser role used by the running application

A PostgreSQL superuser bypasses Row-Level Security unconditionally.
The app never connects as the owner so RLS enforcement is absolute —
a missed application-layer filter cannot leak cross-tenant data.
```

Migration `V11` creates the `medai_app` role. Every table has a `tenant_id` column. A request filter extracts the tenant claim from the JWT and sets `TenantContext` (ThreadLocal), scoping all queries automatically.

### JWT Security
| Token | Lifetime | Transport |
|-------|----------|-----------|
| Access | 15 minutes | `Authorization: Bearer` header |
| Refresh | 7 days | `httpOnly` cookie (`Secure` in production) |

15-minute access tokens minimise the window where a deactivated account or demoted role still works. Silent refresh is invisible to users.

---

## Quick Start

### Prerequisites
- Java 21+
- Node.js 20+
- Maven 3.9+
- Docker & Docker Compose

### 1. Configure environment

```bash
cp .env.example .env
# Edit .env — at minimum set JWT_SECRET and GROQ_API_KEY
# Generate a JWT secret: openssl rand -base64 64
```

### 2. Start all services with Docker Compose

```bash
cd docker
docker-compose up --build
```

| Service | URL |
|---------|-----|
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Frontend | http://localhost:5173 |

### 3. Local development (without Docker)

**Start PostgreSQL only:**
```bash
cd docker && docker-compose up -d postgres
```

**Start backend:**
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Start frontend:**
```bash
cd frontend
npm install
npm run dev
```

---

## Environment Variables

### Database
| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `medai` | Database name |
| `DB_USERNAME` | `medai` | **Owner role** — Flyway migrations only |
| `DB_PASSWORD` | `medai_secret` | Owner role password |
| `DB_APP_USERNAME` | `medai_app` | **App runtime role** — subject to RLS |
| `DB_APP_PASSWORD` | `medai_app_secret` | App role password |

### Auth
| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | *(required)* | Base64 JWT signing key (`openssl rand -base64 64`) |
| `JWT_EXPIRATION_MS` | `900000` | Access token lifetime (15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` | Refresh token lifetime (7 days) |
| `JWT_REFRESH_COOKIE_SECURE` | `true` | Set `false` for plain-HTTP local dev only |

### AI Provider
| Variable | Default | Description |
|----------|---------|-------------|
| `AI_BASE_URL` | `https://api.groq.com/openai` | OpenAI-compatible API base URL |
| `AI_MODEL` | `qwen/qwen3.6-27b` | Vision-capable model ID |
| `GROQ_API_KEY` | *(required)* | Groq API key (or set `OPENAI_API_KEY` for OpenAI) |
| `AI_DATA_AGREEMENT_IN_PLACE` | `false` | Set `true` only after BAA/DPA is signed |
| `AI_RATE_LIMIT_RPM` | `10` | Max AI requests per minute per tenant |
| `AI_RATE_LIMIT_DAILY_USD` | `50.0` | Max daily AI spend per tenant (USD) |

> ⚠️ **Clinical data warning:** The default Groq endpoint has no HIPAA BAA. Use synthetic or de-identified data. For real patient data, use Azure OpenAI, AWS Bedrock, or a self-hosted model and set `AI_DATA_AGREEMENT_IN_PLACE=true`.

### Storage
| Variable | Default | Description |
|----------|---------|-------------|
| `STORAGE_TYPE` | `local` | `local` (single-instance) or `s3` |
| `STORAGE_LOCAL_PATH` | `./uploads` | Local file path (`local` mode only) |
| `STORAGE_S3_BUCKET` | | S3 bucket name |
| `STORAGE_S3_REGION` | `us-east-1` | AWS region |
| `STORAGE_S3_ENDPOINT` | | Custom endpoint (R2: `https://<account>.r2.cloudflarestorage.com`) |
| `STORAGE_S3_ACCESS_KEY` | | Leave blank to use IAM role / IRSA |
| `STORAGE_S3_SECRET_KEY` | | Leave blank to use IAM role / IRSA |

### Rate Limiting (Redis — replicas only)
| Variable | Default | Description |
|----------|---------|-------------|
| `APP_RATE_LIMIT_REDIS_HOST` | *(blank)* | Leave blank for single-instance |
| `APP_RATE_LIMIT_REDIS_PORT` | `6379` | Redis port |

### CORS
| Variable | Default | Description |
|----------|---------|-------------|
| `CORS_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Comma-separated allowed origins |

---

## Deployment

### Docker Compose (development/staging)
```bash
cd docker && docker-compose up --build
```

### Kubernetes (production)
```bash
# Apply all manifests
kubectl apply -f k8s/

# Or use Helm
helm upgrade --install medai ./helm -f helm/values.yaml
```

K8s manifests include:
- Backend `Deployment` with `HorizontalPodAutoscaler`
- `StatefulSet` for PostgreSQL
- Redis `Deployment`
- TLS `Ingress`
- `NetworkPolicy` — restricts inter-pod traffic

### Marketing Site (Cloudflare)
```bash
# Deploy medaiclinical.com (pure static, no build step)
npx wrangler deploy
```

The `wrangler.jsonc` serves only the `marketing/` directory. The React app is **not** published via Wrangler — it runs separately on `app.medaiclinical.com`.

---

## API Reference

Full interactive docs: **`http://localhost:8080/swagger-ui.html`**

### Auth
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/auth/register-tenant` | Register hospital + admin account |
| `POST` | `/api/auth/login` | Authenticate, returns access token + refresh cookie |
| `POST` | `/api/auth/refresh` | Rotate refresh token silently |
| `POST` | `/api/auth/register-user` | Add user to tenant (admin only) |
| `GET` | `/api/auth/tenants` | List registered hospitals |

### Patients
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/patients` | Create patient |
| `GET` | `/api/patients` | Paginated, searchable list (tenant-scoped) |
| `GET` | `/api/patients/{id}` | Patient detail |
| `PUT` | `/api/patients/{id}` | Update patient |
| `DELETE` | `/api/patients/{id}` | Soft-delete |

### Files & Upload
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/patients/{id}/files` | Upload DICOM / lab PDF / blood report (max 100 MB) |
| `GET` | `/api/patients/{id}/files` | List patient files |
| `GET` | `/api/patients/{id}/files/{fileId}/download` | Download file |
| `DELETE` | `/api/patients/{id}/files/{fileId}` | Delete file |

### Analysis
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/analysis` | Trigger AI analysis on uploaded file |
| `GET` | `/api/analysis/{id}` | Poll analysis status / result |

### Reports
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/reports` | Report worklist (tenant-scoped, filterable) |
| `GET` | `/api/reports/{id}` | Report detail with audit trail |
| `PUT` | `/api/reports/{id}/sign` | Cryptographic physician sign-off |
| `PUT` | `/api/reports/{id}/amend` | Amend signed report (creates new version) |

### FHIR
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/fhir/DiagnosticReport/{id}` | FHIR R4 DiagnosticReport resource |
| `GET` | `/api/fhir/Observation/{id}` | FHIR R4 Observation resource |

---

## Roles & RBAC

| Role | Capabilities |
|------|-------------|
| `HOSPITAL_ADMIN` | Full tenant access, user management, billing, compliance settings |
| `DOCTOR` | Patient CRUD, file upload, trigger AI analysis, review and sign reports, AI chat |
| `LAB_TECH` | View patients, upload lab files, view analysis results |
| `PATIENT` | View own records *(future)* |

---

## Roadmap

| Phase | Focus | Status |
|-------|-------|--------|
| **1** | Foundation — auth, multi-tenancy, patients, file upload | ✅ Done |
| **2** | Medical image AI analysis (DICOM, vision model) | ✅ Done |
| **3** | Blood report / lab analysis + combined reasoning | ✅ Done |
| **4** | RAG knowledge base (pgvector, ONNX embeddings) | ✅ Done |
| **5** | AI chat with session memory | ✅ Done |
| **6** | Agentic workflows (LangGraph4J, function-calling tools) | ✅ Done |
| **7** | FHIR R4, terminology validation, compliance module | ✅ Done |
| **8** | Fine-tuning pipeline, billing, Kubernetes, production hardening | 🔄 In Progress |

See [MVP-PLAN.md](./MVP-PLAN.md) for the full detailed breakdown.

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feat/my-feature`)
3. Commit your changes (`git commit -m 'feat: add my feature'`)
4. Push and open a Pull Request

---

## License

Proprietary. All rights reserved — Med-AI Clinical © 2026.

