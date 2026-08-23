# Med-AI Assistant — MVP Breakdown Plan

## Overview
Multi-tenant Medical GenAI Web Application divided into **8 MVPs** for incremental delivery.
Each MVP is self-contained, testable, and deployable independently.

---

## MVP 1: Foundation & Core Infrastructure (Week 1-2)
**Goal:** Skeleton project, multi-tenant auth, file upload, database schema, Docker dev environment.

### Backend (Spring Boot 3.x + Java 21)
- [x] Project scaffolding with Spring Boot 3.x, Java 21, Maven
- [x] Multi-tenant database schema (PostgreSQL)
  - `tenants` table (id, name, subdomain, settings, created_at)
  - `users` table (id, tenant_id, email, password_hash, role, name)
  - `patients` table (id, tenant_id, name, dob, gender, medical_record_number)
  - `medical_files` table (id, tenant_id, patient_id, file_type, file_path, upload_status)
  - `audit_logs` table (id, tenant_id, user_id, action, entity_type, entity_id, timestamp)
- [x] Tenant context resolver (JWT claim → ThreadLocal TenantContext)
- [x] Spring Security config with JWT authentication
- [x] Role-based access control (HOSPITAL_ADMIN, DOCTOR, LAB_TECH, PATIENT)
- [x] File upload REST API (images: DICOM/JPEG/PNG, documents: PDF)
- [x] Tenant-scoped file storage (local dev → S3 prod) at `/{tenantId}/patients/{patientId}/...`
- [x] Global exception handler + standard error response
- [x] Health check + info endpoints
- [x] Flyway DB migrations (V1-V4 with Row-Level Security & Refresh Token Rotation)
- [x] OpenAPI/Swagger docs

### Frontend (React 18 + TypeScript + Vite + TailwindCSS + shadcn/ui)
- [x] Project scaffolding
- [x] Auth pages (login, register hospital, forgot password)
- [x] Dashboard shell (sidebar, header, tenant branding)
- [x] File upload component with drag-and-drop
- [x] Patient list page (CRUD)
- [x] Routing with protected routes

### DevOps
- [x] Docker Compose (PostgreSQL, backend, frontend)
- [x] Environment configuration (.env files)
- [x] README with setup instructions

### Deliverable
> A working multi-tenant app where a hospital admin can register, doctors can login, manage patients, and upload medical files. No AI yet.

---

## MVP 2: Medical Image Analysis (Week 3-4)
**Goal:** AI-powered analysis of X-ray, CT, ultrasound images with structured output.

### Backend
- [x] Spring AI integration with OpenAI GPT-4o (vision)
- [x] `AnalysisRequest` / `AnalysisResult` entities
- [x] Image analysis service — sends image + prompt → receives structured JSON
- [x] Structured output schema:
  ```json
  {
    "findings": [{"region": "...", "description": "...", "severity": "...", "confidence": 0.92}],
    "impression": "...",
    "icd10_codes": ["J18.9"],
    "recommendations": ["..."],
    "urgency": "ROUTINE | URGENT | CRITICAL"
  }
  ```
- [x] Analysis status tracking (PENDING → PROCESSING → COMPLETED → FAILED)
- [x] Async processing with Spring Events
- [x] Analysis history per patient (tenant-scoped)
- [x] Retry logic + fallback for API failures
- [x] Cost tracking per analysis (tokens used, model, cost) & per-tenant rate limiting

### Frontend
- [x] Image viewer component (zoom, pan, brightness/contrast, invert, rotate)
- [x] Analysis request form (select image, add clinical notes)
- [x] Analysis results display (structured findings cards)
- [x] Analysis history timeline per patient
- [x] Loading states and error handling

### Deliverable
> Doctor uploads an X-ray → AI returns structured findings with severity, confidence, ICD-10 codes, and recommendations. Results stored and viewable per patient.

---

## MVP 3: Blood Report Analysis & Combined Reasoning (Week 5-6)
**Goal:** OCR + AI extraction of blood reports, combined analysis with imaging.

### Backend
- [x] PDF/image OCR pipeline (Apache Tika / Tesseract / GPT-4o vision)
- [x] Blood report extraction service → structured output:
  ```json
  {
    "test_name": "Complete Blood Count",
    "parameters": [
      {"name": "WBC", "value": 14.5, "unit": "10^3/uL", "reference_range": "4.5-11.0", "flag": "HIGH"}
    ],
    "interpretation": "...",
    "flags": ["INFECTION_LIKELY"]
  }
  ```
- [x] Combined reasoning service — merges image findings + blood report + patient history
- [x] Unified diagnosis recommendation with confidence scoring
- [x] Report template engine (generate printable PDF reports)
- [x] Batch upload support (multiple files per analysis)

### Frontend
- [x] Blood report upload + preview
- [x] Extracted values table with flag indicators (normal/high/low)
- [x] Combined analysis view (image + lab results side by side)
- [x] PDF report download
- [x] Comparison view (current vs. previous results)

### Deliverable
> Upload blood report PDF → AI extracts values, flags abnormalities, interprets. Combined with image analysis for unified diagnosis.

---

## MVP 4: RAG & Knowledge Base (Week 7-8)
**Goal:** Hospital-specific knowledge base with document ingestion and AI Q&A.

### Backend
- [x] pgvector extension setup for PostgreSQL
- [x] Document ingestion pipeline:
  - Upload PDF/DOCX → extract text → chunk → embed → store in vector DB
  - Metadata: tenant_id, document_type (PROTOCOL, GUIDELINE, JOURNAL), title, source
- [x] Embedding service (OpenAI text-embedding-3-small or local)
- [x] RAG query pipeline:
  - User query → embed → similarity search (filtered by tenant_id) → top-k chunks → LLM generates answer with citations
- [x] Knowledge base CRUD API (upload, list, delete, re-index documents)
- [x] Chunking strategies (recursive text splitting, 512 tokens, 50 overlap)
- [x] Citation tracking (which chunks were used in the answer)
- [x] Scheduled re-indexing for updated documents

### Frontend
- [x] Knowledge base management page (upload, list, delete documents)
- [x] Q&A interface (ask a question → get answer with source citations)
- [x] Document viewer (view uploaded guidelines)
- [x] Search within knowledge base

### Deliverable
> Hospital admin uploads protocol PDFs. Doctor asks "What is our antibiotic protocol for CAP?" → AI answers grounded in hospital's own documents, with citations.

---

## MVP 5: AI Chat with Memory (Week 9)
**Goal:** Multi-turn conversational AI with patient context and session memory.

### Backend
- [x] Chat session management (create, list, archive sessions)
- [x] Chat message storage (session_id, role, content, timestamp, tenant_id)
- [x] Clinical Guardrails Engine (prompt injection defense, acute red-flag emergency detection, allergy sentinels)
- [x] Memory strategies:
  - **Short-term:** Last N messages in context window
  - **Patient context:** Auto-inject patient info + recent analyses into system prompt
- [x] Streaming response support (SSE)
- [x] Chat can reference:
  - Patient's analysis history (image + blood)
  - Knowledge base (RAG)
  - General medical knowledge (LLM)
- [x] Token budget & cost management (per-tenant rate limiting and spend tracking)

### Frontend
- [x] Chat interface (message bubbles, streaming text, markdown rendering)
- [x] Session list sidebar (per patient or general)
- [x] Patient context indicator (which patient is being discussed)
- [x] Copy/export chat transcript
- [x] Suggested clinical follow-up questions

### Deliverable
> Doctor opens chat about Patient X → AI knows patient's recent X-ray showed pneumonia, WBC was elevated → Doctor asks follow-ups → AI remembers conversation context across turns.

---

## MVP 6: Function Calling, Tools & Agentic Workflows (Week 10)
**Goal:** AI can execute actions (schedule appointments, write prescriptions) and run multi-step workflows.

### Backend
- [ ] Tool registry (define available tools with JSON schema)
- [ ] Built-in tools:
  - `scheduleAppointment(patientId, date, type, notes)`
  - `writePrescription(patientId, medications[])`
  - `orderLabTest(patientId, tests[])`
  - `generateDischargeSummary(patientId)`
  - `sendNotification(userId, message, channel)`
  - `searchPatientHistory(patientId, query)`
- [ ] Function calling integration (LLM decides which tool to call)
- [ ] Agentic workflow engine:
  - Agent receives high-level goal → plans steps → executes tools in sequence → reports results
  - Example: "Discharge patient X" → generate summary + schedule follow-up + write prescription + notify patient
- [ ] Tool execution logging (for audit trail)
- [ ] Human-in-the-loop confirmation for critical actions (prescriptions, appointments)
- [ ] MCP (Model Context Protocol) server:
  - Expose tools as MCP resources
  - Allow external systems to discover and invoke AI capabilities

### Frontend
- [ ] Tool execution confirmation dialogs
- [ ] Agent workflow progress tracker (step-by-step visualization)
- [ ] Action cards (appointment scheduled, prescription written)
- [ ] Agent chat mode toggle (enable/disable tool use)

### Deliverable
> Doctor says "Discharge patient with amoxicillin and follow-up in 7 days" → Agent plans and executes: generates discharge summary, writes prescription, schedules appointment, and confirms each step.

---

## MVP 7: Advanced Frontend, Search & DICOM (Week 11)
**Goal:** Polish UI, full-text search, DICOM viewer, batch processing, mobile PWA.

### Backend
- [ ] Elasticsearch integration (tenant-scoped full-text search)
- [ ] Index: patients, diagnoses, reports, chat transcripts
- [ ] Batch processing API (upload N studies → process async → webhook/notification on completion)
- [ ] Notification service (in-app, email, push for critical findings)
- [ ] Export API (PDF reports, CSV data export)
- [ ] Dashboard analytics API (analyses per day, top diagnoses, model usage)

### Frontend
- [ ] Global search bar (search patients, diagnoses, reports across all data)
- [ ] DICOM viewer integration (Cornerstone.js)
  - Zoom, pan, window/level, measurement tools
  - AI overlay (annotated regions from analysis)
- [ ] Dashboard analytics (charts: analyses/day, top conditions, cost tracking)
- [ ] Batch upload interface with progress tracking
- [ ] Notification center
- [ ] Mobile responsive / PWA support
- [ ] Multi-language UI (i18n framework)
- [ ] Dark mode

### Deliverable
> Full-featured clinical dashboard with DICOM viewing, global search, analytics, batch processing, and mobile support.

---

## MVP 8: Fine-tuning, Compliance & Production Readiness (Week 12)
**Goal:** LoRA fine-tuning, compliance hardening, observability, deployment.

### Backend
- [x] Fine-tuning pipeline:
  - Data preparation (anonymized medical text → training format)
  - LoRA adapter training (Llama 3 via Ollama/vLLM)
  - Model registry (store adapters per tenant)
  - A/B testing between base and fine-tuned models
- [x] Compliance hardening:
  - PHI redaction before external API calls (regex + NER)
  - Consent management API
  - Data retention policies (auto-purge after configured period)
  - Encryption at rest (AES-256) for all medical data
- [x] Observability:
  - OpenTelemetry tracing (request → LLM call → DB → response)
  - Prometheus metrics (latency, token usage, cost, error rates per tenant)
  - Grafana dashboards
  - Loki for log aggregation
- [x] Performance:
  - Redis caching for frequent LLM responses
  - Connection pooling optimization
  - Load testing (k6 / Gatling)
- [x] Deployment:
  - Kubernetes manifests (Deployment, Service, Ingress, HPA)
  - Helm chart
  - CI/CD pipeline (GitHub Actions → build → test → deploy)
  - Staging + Production environments

### Deliverable
> Production-ready platform with fine-tuned models, full compliance, monitoring, and automated deployment.

---

## Tech Stack Summary

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 21, Spring Boot 3.x, Spring AI, Spring Security |
| **Frontend** | React 18, TypeScript, Vite, TailwindCSS, shadcn/ui, Zustand |
| **Database** | PostgreSQL 16 + pgvector, Flyway migrations |
| **Cache** | Redis 7 |
| **Search** | Elasticsearch 8 |
| **Message Queue** | RabbitMQ |
| **AI Models** | OpenAI GPT-4o (vision+text), text-embedding-3-small, Ollama (local Llama 3) |
| **File Storage** | Local (dev) → AWS S3 (prod) |
| **Auth** | JWT (jjwt), BCrypt |
| **API Docs** | SpringDoc OpenAPI |
| **Containerization** | Docker, Docker Compose, Kubernetes |
| **CI/CD** | GitHub Actions |
| **Monitoring** | Prometheus, Grafana, Loki, OpenTelemetry |

---

## Directory Structure

```
med-ai-assistant/
├── backend/                          # Spring Boot application
│   ├── src/main/java/com/medai/
│   │   ├── MedAiApplication.java
│   │   ├── config/                   # Security, AI, DB, multitenancy configs
│   │   ├── tenant/                   # Tenant resolution, context, filter
│   │   ├── auth/                     # JWT, login, registration
│   │   ├── user/                     # User entity, repository, service
│   │   ├── patient/                  # Patient CRUD
│   │   ├── upload/                   # File upload service
│   │   ├── analysis/                 # Image + blood report analysis
│   │   ├── rag/                      # RAG pipeline, vector store, ingestion
│   │   ├── chat/                     # Chat sessions, memory
│   │   ├── agent/                    # Tools, function calling, workflows
│   │   ├── mcp/                      # MCP server
│   │   ├── search/                   # Elasticsearch integration
│   │   ├── notification/             # Alerts, push notifications
│   │   ├── audit/                    # Audit logging
│   │   └── common/                   # Shared DTOs, exceptions, utils
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   ├── application-prod.yml
│   │   └── db/migration/            # Flyway SQL scripts
│   └── pom.xml
├── frontend/                         # React application
│   ├── src/
│   │   ├── components/              # Reusable UI components
│   │   ├── pages/                   # Route pages
│   │   ├── hooks/                   # Custom React hooks
│   │   ├── stores/                  # Zustand state stores
│   │   ├── services/                # API client services
│   │   ├── types/                   # TypeScript types
│   │   └── utils/                   # Helpers
│   ├── package.json
│   └── vite.config.ts
├── docker/                           # Docker configs
│   ├── docker-compose.yml
│   ├── docker-compose.dev.yml
│   └── Dockerfile.*
├── k8s/                             # Kubernetes manifests
├── docs/                            # Documentation
├── MVP-PLAN.md                      # This file
└── README.md
```

---

## Running Order for Development
1. **Start with MVP 1** — get the skeleton running end-to-end
2. Each subsequent MVP builds on the previous
3. Every MVP should have its own integration tests
4. Deploy to staging after each MVP for stakeholder review
