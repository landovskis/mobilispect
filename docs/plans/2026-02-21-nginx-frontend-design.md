# nginx Frontend Container Design

**Date:** 2026-02-21
**Status:** Approved

## Context

The devcontainer environment has a Spring Boot backend (`app`, port 8080),
PostgreSQL, Redis, and Airflow — but no service to serve the Angular
frontend. The Angular dev server (`ng serve`) requires running it manually
inside the `app` container. This design adds an nginx container to the
devcontainer docker-compose that builds and serves the Angular app
automatically.

## Decision

Use a multi-stage Docker build (`Dockerfile.nginx`) that:

1. Builds the Angular app with `ng build --configuration production`
   inside a `node:24-alpine` stage
2. Copies the output into `nginx:alpine` alongside a custom nginx config

This avoids a separate build service or named volumes and keeps the nginx image self-contained.

## Files

| File | Action |
|------|--------|
| `.devcontainer/Dockerfile.nginx` | New — multi-stage Angular build + nginx |
| `.devcontainer/nginx.conf` | New — SPA routing + API/WS proxy |
| `.devcontainer/docker-compose.yml` | Modified — add `nginx` service |

## nginx Routing

| Path | Destination |
|------|------------|
| `/api/*` | `http://app:8080` (Spring Boot REST) |
| `/ws` | `http://app:8080` (WebSocket, HTTP/1.1 upgrade) |
| `/*` | Angular SPA with `try_files` fallback to `index.html` |

This mirrors the existing `proxy.conf.json` used by `ng serve`.

## Port Mapping

nginx listens on port 80 inside the container, mapped to host port 4200
(already forwarded in `devcontainer.json`).

## Trade-offs

- The Angular build runs at image build time, not at container start.
  Code changes require rebuilding the image (`docker compose build nginx`).
- The backend proxy silently fails (502) until the developer starts
  Spring Boot inside the `app` container — expected devcontainer behaviour.
- For hot-reload development, `ng serve` remains available inside the
  `app` container on port 4200.
