# nginx Frontend Container Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to
> implement this plan task-by-task.

**Goal:** Add an nginx container to the devcontainer docker-compose that
builds the Angular app and serves it on port 4200, proxying `/api/` and
`/ws` to the Spring Boot backend.

**Architecture:** Multi-stage `Dockerfile.nginx` — Node 24 builder stage
runs `npm run build:prod`, nginx:alpine stage serves the dist output with a
custom `nginx.conf` that handles SPA routing and API/WebSocket proxying.
The `nginx` service is added to `docker-compose.yml` and depends on `app`.

**Tech Stack:** Docker multi-stage build, nginx:alpine, node:24-alpine,
Angular 21 (`npm run build:prod`), docker-compose

---

## Context

- Build context root: repo root (same as all other devcontainer services)
- Angular build output: `frontend/web/dist/web/browser/`
- Package manager: npm (lock file at `frontend/web/package-lock.json`)
- Build command: `npm run build:prod` (= `ng build --configuration production`)
- Backend service name in Docker network: `app` (port 8080)
- nginx proxy rules mirror `frontend/web/proxy.conf.json`
- Host port 4200 is already forwarded in `devcontainer.json`

---

### Task 1: Create nginx.conf

**Files:**

- Create: `.devcontainer/nginx.conf`

#### Step 1: Write the config

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # REST API proxy → Spring Boot backend
    location /api/ {
        proxy_pass         http://app:8080;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }

    # WebSocket proxy → Spring Boot backend
    location /ws {
        proxy_pass         http://app:8080;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade    $http_upgrade;
        proxy_set_header   Connection "Upgrade";
        proxy_set_header   Host       $host;
    }

    # Angular SPA — client-side routing fallback
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

Note on `proxy_pass`: no trailing slash — this preserves the full URI
path (e.g. `/api/feeds` passes as `/api/feeds` to the backend).

#### Step 2: Verify nginx config syntax locally (optional)

```bash
# If nginx is installed on the host:
nginx -t -c $(pwd)/.devcontainer/nginx.conf
# Expected: syntax is ok
```

Skip if nginx not installed; the Docker build will catch syntax errors.

#### Step 3: Commit nginx.conf

```bash
git add .devcontainer/nginx.conf
git commit -m "feat(devcontainer): add nginx config for Angular SPA"
```

---

### Task 2: Create Dockerfile.nginx

**Files:**

- Create: `.devcontainer/Dockerfile.nginx`

#### Step 1: Write the Dockerfile

```dockerfile
# syntax=docker/dockerfile:1.5

# Stage 1: Build Angular
FROM node:24-alpine AS builder
WORKDIR /app
COPY frontend/web/package.json frontend/web/package-lock.json ./
RUN npm ci
COPY frontend/web/ .
RUN npm run build:prod

# Stage 2: Serve with nginx
FROM nginx:alpine
COPY --from=builder /app/dist/web/browser /usr/share/nginx/html
COPY .devcontainer/nginx.conf /etc/nginx/conf.d/default.conf
```

Key points:

- `package*.json` copied before source so npm ci layer is cached
- `npm ci` (not `npm install`) — reproducible from lock file
- Build context is `..` (repo root), so paths are relative to repo root
- `nginx.conf` replaces the default `default.conf` (not `nginx.conf`)

#### Step 2: Build the image to verify it compiles

```bash
docker build -f .devcontainer/Dockerfile.nginx -t mobilispect-nginx-test .
```

Expected: BUILD successfully, no errors. The Angular build may take
2-5 minutes on first run (downloading npm deps).

If the build fails:

- npm ci errors → check `frontend/web/package-lock.json` is committed
- ng build errors → run `npm run build:prod` in `frontend/web/` locally
  to reproduce

#### Step 3: Commit Dockerfile.nginx

```bash
git add .devcontainer/Dockerfile.nginx
git commit -m "feat(devcontainer): add multi-stage Dockerfile for nginx"
```

---

### Task 3: Add nginx service to docker-compose.yml

**Files:**

- Modify: `.devcontainer/docker-compose.yml`

#### Step 1: Add the nginx service

Add the following service block after the `redis` service and before
`airflow`:

```yaml
  nginx:
    build:
      context: ..
      dockerfile: .devcontainer/Dockerfile.nginx
    ports:
      - "4200:80"
    depends_on:
      - app
```

The `depends_on: app` ensures the devcontainer app container is running
before nginx starts. nginx will return 502 for `/api/` requests until
the developer starts Spring Boot inside `app` — this is expected.

#### Step 2: Verify the compose file is valid

```bash
docker compose -f .devcontainer/docker-compose.yml config --quiet
```

Expected: exits 0 with no output (valid YAML and compose schema).

#### Step 3: Build and start the nginx service

```bash
docker compose -f .devcontainer/docker-compose.yml up --build nginx -d
```

Expected: image builds, container starts.

#### Step 4: Verify nginx is serving the Angular app

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:4200/
```

Expected: `200`

```bash
curl -s http://localhost:4200/ | grep -o '<title>.*</title>'
```

Expected: `<title>Mobilispect</title>` (or similar Angular app title)

#### Step 5: Verify SPA routing works (deep link returns index.html)

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:4200/agencies/123
```

Expected: `200` (not 404) — nginx falls back to `index.html`

#### Step 6: Verify static assets are served

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:4200/favicon.ico
```

Expected: `200`

#### Step 7: Commit

```bash
git add .devcontainer/docker-compose.yml
git commit -m "feat(devcontainer): add nginx service to serve Angular frontend"
```

---

## Cleanup

Remove the test image built in Task 2:

```bash
docker rmi mobilispect-nginx-test
```

---

## Restore Stashed Changes

After implementation, restore the stash that was put aside during
the design doc commit:

```bash
git checkout stash@{0} -- \
  .devcontainer/docker-compose.yml \
  airflow/dags/feed_import.py \
  airflow/dags/region_import.py \
  backend/airflow/dags/feed_import.py \
  backend/airflow/dags/region_import.py
git stash drop stash@{0}
```

Note: the stash was created during the design doc commit to work around
a pre-commit stash conflict with pyc files.
