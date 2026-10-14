# Spd con architecture (initial)

## Principles

1. The Android app is the primary user experience; the backend owns remote operations.
2. Keep protocol parsing/serialization separate from UI and persistence.
3. Do not treat arbitrary imported links as trusted commands.
4. Remote node actions require authentication, authorization, audit events, and explicit node enrollment.
5. Keep upstream projects behind adapters; do not copy source until licenses and compatibility are reviewed.

## Android layers

- `feature/*`: dashboard, config builder/library, scanner, checker, nodes, users, subscriptions, settings.
- `core/model`: protocol-neutral data models and validation results.
- `core/data`: repository interfaces and implementations.
- `core/database`: Room entities and DAOs.
- `core/network`: authenticated API client and DTOs.
- `core/security`: secret redaction, secure token storage integration, input limits.
- `core/designsystem`: Material 3 theme and reusable components.

The first commit keeps a small app module so the foundation is easy to open. Extract feature modules as the codebase grows.

## Backend layers

- API routes and Pydantic schemas
- authentication and role-based authorization
- service layer and repositories
- PostgreSQL persistence and migrations
- Redis-backed background jobs
- adapters for supported Xray operations and compatible upstream panels
- enrolled node agent with least-privilege permissions
- audit log and health/metrics endpoints

## Core data model (planned)

- `accounts`, `roles`, `account_roles`
- `nodes`, `node_credentials`, `node_health_checks`
- `configs`, `config_versions`, `inbounds`
- `users`, `user_node_assignments`, `usage_records`
- `subscriptions`, `subscription_items`
- `scan_jobs`, `scan_results`, `checker_jobs`, `checker_results`
- `audit_logs`, `refresh_tokens`

Use UUID primary keys, UTC timestamps, indexes on ownership/status/time, and foreign keys. Store credentials encrypted or in a dedicated secret manager; never store plaintext node passwords in ordinary tables.

## API outline (planned)

- `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`
- `GET /api/v1/configs`, `POST /api/v1/configs`, `POST /api/v1/configs/validate`, `POST /api/v1/configs/import`, `GET /api/v1/configs/{id}/export`
- `GET /api/v1/nodes`, `POST /api/v1/nodes`, `GET /api/v1/nodes/{id}/health`, `POST /api/v1/nodes/{id}/sync`
- `GET /api/v1/users`, `POST /api/v1/users`, `PATCH /api/v1/users/{id}`
- `POST /api/v1/subscriptions`, `POST /api/v1/subscriptions/{id}/revoke`
- `POST /api/v1/scans`, `GET /api/v1/scans/{id}`; equivalent checker job endpoints
- `GET /health/live`, `GET /health/ready`

## Deployment boundary

Compose is for local development. Production needs TLS/reverse proxy, secrets, migrations, backups, restricted database/cache networking, rate limits, monitoring, and tested restore procedures. A node agent should not run with broader OS privileges than required.
