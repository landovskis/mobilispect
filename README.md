# Mobilispect

![Android CI](https://github.com/alandovskis/mobilispect/actions/workflows/android-ci.yml/badge.svg)
![Backend CI](https://github.com/alandovskis/mobilispect/actions/workflows/backend-ci.yml/badge.svg)

## Directory Structure

### Frontend

The frontend is powered by Kotlin Multi Platform. Available only as an
Android app, but an iOS app is planned.

```text
android: Android App + UI

common
├── build: Outputs
├── schemas: Room database schemas
├── src
│   ├── androidAndroidTest: Android instrumented tests
│   ├── androidMain: Android-specific code
│   ├── androidTest: Android local tests
│   ├── commonMain: Shared logic
│   ├── commonTest: Shared tests
│   ├── iosMain: iOS-specific code
│   └── iosTest: iOS local tests

ios: iOS App + UI
```

### Backend

The backend is powered by Spring Boot and follows a modular monolith
architecture: domain modules own their data and interfaces, and service
extraction requires an ADR and migration plan. Integration tests use
Testcontainers (PostgreSQL 18, Redis 8.2) so Docker must be available in
both local and CI environments.

## Development Containers

A ready-to-use Visual Studio Code devcontainer is available under
`.devcontainer/`.

1. Install the VS Code **Dev Containers** extension
   (or use `devcontainer` CLI).
2. Open this repository in VS Code and run **“Reopen in Container”**.
3. The container provisions:
   - JDK 25 with Gradle wrapper support
   - Node.js 20 with Angular CLI and pnpm via Corepack
   - Local PostgreSQL 18 and Redis 8.2 services (via docker-compose)

4. Default ports exposed: `8080` (backend), `4200` (Angular),
   `5432` (PostgreSQL), `6379` (Redis).

First launch automatically installs web dependencies (`npm install` in
`frontend/web`). Re-run manually if package manifests change.
