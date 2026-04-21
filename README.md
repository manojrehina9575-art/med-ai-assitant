# Med AI Assistant

Multi-tenant Medical GenAI Web Application — AI-powered analysis of medical images and blood reports for hospitals.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA |
| **Frontend** | React 18, TypeScript, Vite, TailwindCSS, shadcn/ui, Zustand |
| **Database** | PostgreSQL 16, Flyway migrations |
| **Auth** | JWT (jjwt), BCrypt, RBAC |
| **API Docs** | SpringDoc OpenAPI (Swagger) |
| **Containerization** | Docker, Docker Compose |

## Project Structure

```
med-ai-assistant/
├── backend/          # Spring Boot API
├── frontend/         # React SPA
├── docker/           # Docker Compose + Dockerfiles
├── MVP-PLAN.md       # Full MVP breakdown (8 phases)
└── README.md
```

## Quick Start (Local Development)

### Prerequisites
- Java 21+
- Node.js 20+
- PostgreSQL 16+ (or use Docker)
- Maven 3.9+

### 1. Start PostgreSQL via Docker

```bash
cd docker
docker-compose up -d postgres
```

### 2. Start the Backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The API will be available at `http://localhost:8080`.  
Swagger UI: `http://localhost:8080/swagger-ui.html`

### 3. Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

The UI will be available at `http://localhost:5173`.

### Full Docker Compose (all services)

```bash
cd docker
docker-compose up --build
```

## API Endpoints (MVP 1)

### Auth
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register-tenant` | Register a new hospital + admin |
| POST | `/api/auth/login` | Login (returns JWT) |
| POST | `/api/auth/register-user` | Register user (admin only) |
| POST | `/api/auth/refresh` | Refresh access token |
| GET | `/api/auth/tenants` | List hospitals |

### Patients
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/patients` | Create patient |
| GET | `/api/patients` | List patients (paginated, searchable) |
| GET | `/api/patients/{id}` | Get patient by ID |
| PUT | `/api/patients/{id}` | Update patient |
| DELETE | `/api/patients/{id}` | Soft-delete patient |

### File Upload
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/patients/{id}/files` | Upload medical file |
| GET | `/api/patients/{id}/files` | List patient files |
| GET | `/api/patients/{id}/files/{fileId}/download` | Download file |
| DELETE | `/api/patients/{id}/files/{fileId}` | Delete file |

## MVP Roadmap

| MVP | Focus | Status |
|-----|-------|--------|
| **1** | Foundation (auth, patients, upload) | **Current** |
| **2** | Medical image analysis (GPT-4o vision) | Planned |
| **3** | Blood report analysis + combined reasoning | Planned |
| **4** | RAG & knowledge base | Planned |
| **5** | AI chat with memory | Planned |
| **6** | Function calling & agentic workflows | Planned |
| **7** | Advanced frontend, search, DICOM viewer | Planned |
| **8** | Fine-tuning, compliance, production | Planned |

See [MVP-PLAN.md](./MVP-PLAN.md) for the full detailed breakdown.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL port |
| `DB_NAME` | medai | Database name |
| `DB_USERNAME` | medai | Database user |
| `DB_PASSWORD` | medai_secret | Database password |
| `JWT_SECRET` | (base64) | JWT signing key |
| `STORAGE_TYPE` | local | `local` or `s3` |
| `STORAGE_LOCAL_PATH` | ./uploads | Local file storage path |
| `CORS_ORIGINS` | http://localhost:5173 | Allowed CORS origins |

## Multi-Tenancy

Every table has a `tenant_id` column. The JWT carries the tenant claim, and a request filter sets `TenantContext` (ThreadLocal) so all queries are automatically scoped to the correct tenant.

## Roles

| Role | Capabilities |
|------|-------------|
| `HOSPITAL_ADMIN` | Full access, user management |
| `DOCTOR` | Patient CRUD, file upload, AI analysis |
| `LAB_TECH` | Patient view, file upload |
| `PATIENT` | View own records (future) |
