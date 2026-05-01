# Albudoor HMS

Hospital Management System for Albudoor Hospital, Iraq.

## Stack
- **Backend**: Java 21, Spring Boot 3.3, PostgreSQL 16, Flyway, Spring Security + JWT, Maven multi-module
- **Frontend**: React 18 + TypeScript, Vite, Tailwind CSS, shadcn/ui, react-i18next (en/ar with RTL)
- **Architecture**: Modular monolith with DDD bounded contexts, vertical slice within each module

## Layout
```
backend/        Maven multi-module backend
  platform/     Cross-cutting: BaseEntity, exceptions, audit, outbox
  identity/     Users, roles, JWT auth
  patient-registry/  Patient, MRN, GDF, VIP — REFERENCE IMPLEMENTATION
  app/          Spring Boot composition root
frontend/       React SPA
docker-compose.yml   Local Postgres
```

## Run locally
```bash
# 1. Start Postgres
docker compose up -d db

# 2. Build & run backend (from /backend)
cd backend
./mvnw spring-boot:run -pl app

# 3. Run frontend (from /frontend)
cd frontend
npm install
npm run dev
```

Backend: http://localhost:8080  ·  OpenAPI: http://localhost:8080/swagger-ui.html
Frontend: http://localhost:5173

## Adding a new module (vertical slice pattern)
Each bounded context is a Maven module. Inside, use vertical slices: one folder per use case.

```
patient-registry/src/main/java/.../patientregistry/
  domain/                 # Aggregates, value objects, domain events
  registernewpatient/     # Slice: command + handler + validator + controller + dto + mapper
  searchpatient/          # Slice: query + handler + controller
  togglevip/              # Slice
  infrastructure/         # JPA repositories, MRN generator
```

Boundaries are enforced by ArchUnit tests (`backend/app/src/test/java/.../ArchitectureTest.java`).

## Conventions
- Aggregates extend `AggregateRoot` from `platform`.
- Domain events implement `DomainEvent`; published via Spring `ApplicationEventPublisher`.
- Cross-process events (WhatsApp, printer, etc.) go through the `OutboxEvent` table.
- All migrations under `*/src/main/resources/db/migration/V###__name.sql`. Flyway picks them all up via classpath.
- Controllers return `ApiError` on `DomainException`/`NotFoundException`; handled centrally by `GlobalExceptionHandler`.
