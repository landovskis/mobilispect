# GitHub Actions CI/CD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two GitHub Actions workflows — `ci.yml` for PR gating and `deploy.yml` for Railway deployment — plus repository branch protection to enforce them.

**Architecture:** Five parallel jobs in `ci.yml` (lint, test, audit, docker-build, mutants) cover all quality gates. `deploy.yml` runs a blocking Railway deploy on every push to `main`. Jobs are added incrementally — one commit per job — so failures are immediately attributable. Branch protection is configured last, after all jobs are green.

**Tech Stack:** GitHub Actions, dtolnay/rust-toolchain, Swatinem/rust-cache, taiki-e/install-action, cargo-nextest, cargo-mutants, rustsec/audit-check, Docker Buildx, Railway CLI

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `.github/workflows/ci.yml` | Create | Five-job CI workflow |
| `.github/workflows/deploy.yml` | Create | Railway deploy workflow |

No existing files are modified. All YAML is greenfield.

---

## Reference: Complete Final Files

### `.github/workflows/ci.yml`

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

env:
  SQLX_OFFLINE: "true"
  CARGO_TERM_COLOR: always

jobs:
  lint:
    name: Lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
        with:
          components: rustfmt, clippy
      - uses: Swatinem/rust-cache@v2
      - name: Install native dependencies
        run: sudo apt-get update && sudo apt-get install -y --no-install-recommends pkg-config libssl-dev protobuf-compiler
      - name: Check formatting
        run: cargo fmt --check
      - name: Clippy
        run: cargo clippy --all-targets --all-features -- -D warnings

  test:
    name: Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
      - uses: Swatinem/rust-cache@v2
      - name: Install native dependencies
        run: sudo apt-get update && sudo apt-get install -y --no-install-recommends pkg-config libssl-dev protobuf-compiler
      - uses: taiki-e/install-action@v2
        with:
          tool: nextest
      - name: Run tests
        run: cargo nextest run --profile ci

  audit:
    name: Audit
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: rustsec/audit-check@v2
        with:
          token: ${{ secrets.GITHUB_TOKEN }}

  docker-build:
    name: Docker Build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: false
          cache-from: type=gha
          cache-to: type=gha,mode=max

  mutants:
    name: Mutation Testing
    runs-on: ubuntu-latest
    if: github.event_name == 'pull_request'
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: dtolnay/rust-toolchain@stable
      - uses: Swatinem/rust-cache@v2
      - name: Install native dependencies
        run: sudo apt-get update && sudo apt-get install -y --no-install-recommends pkg-config libssl-dev protobuf-compiler
      - uses: taiki-e/install-action@v2
        with:
          tool: cargo-mutants
      - name: Compute PR diff
        run: git diff origin/${{ github.base_ref }}...HEAD > /tmp/pr.diff
      - name: Run mutation tests
        run: cargo mutants --in-diff /tmp/pr.diff --timeout 120
      - name: Upload mutants output
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: mutants-out
          path: mutants.out/
          retention-days: 7
```

### `.github/workflows/deploy.yml`

```yaml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    name: Deploy to Railway
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Install Railway CLI
        run: npm install -g @railway/cli
      - name: Deploy server
        run: railway up --service ${{ secrets.RAILWAY_SERVICE_ID_SERVER }}
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}
      - name: Deploy worker
        run: railway up --service ${{ secrets.RAILWAY_SERVICE_ID_WORKER }}
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}
```

---

## Task 1: Create `ci.yml` with the lint job

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create the workflows directory and `ci.yml`**

```bash
mkdir -p .github/workflows
```

Create `.github/workflows/ci.yml` with this content:

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

env:
  SQLX_OFFLINE: "true"
  CARGO_TERM_COLOR: always

jobs:
  lint:
    name: Lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
        with:
          components: rustfmt, clippy
      - uses: Swatinem/rust-cache@v2
      - name: Install native dependencies
        run: sudo apt-get update && sudo apt-get install -y --no-install-recommends pkg-config libssl-dev protobuf-compiler
      - name: Check formatting
        run: cargo fmt --check
      - name: Clippy
        run: cargo clippy --all-targets --all-features -- -D warnings
```

- [ ] **Step 2: Commit and push**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add lint job"
git push origin HEAD
```

- [ ] **Step 3: Verify in GitHub Actions**

Open `https://github.com/landovskis/mobilispect/actions`. Find the run triggered by the push. Confirm the `lint` job completes green — both "Check formatting" and "Clippy" steps pass.

If "Check formatting" fails: run `cargo fmt` locally, commit the result, and push again.

---

## Task 2: Add the test job

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Append the `test` job to `ci.yml`**

Add the following under `jobs:`, after the `lint` job:

```yaml
  test:
    name: Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
      - uses: Swatinem/rust-cache@v2
      - name: Install native dependencies
        run: sudo apt-get update && sudo apt-get install -y --no-install-recommends pkg-config libssl-dev protobuf-compiler
      - uses: taiki-e/install-action@v2
        with:
          tool: nextest
      - name: Run tests
        run: cargo nextest run --profile ci
```

- [ ] **Step 2: Commit and push**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add test job"
git push origin HEAD
```

- [ ] **Step 3: Verify in GitHub Actions**

In the Actions tab, find the new run. Confirm both `lint` and `test` are green. The `test` job will spin up Docker containers via testcontainers — expect it to take ~3–5 minutes. If a test fails, the output shows which test and why.

Note: `ubuntu-latest` runners have a live Docker daemon; testcontainers works without any extra configuration.

---

## Task 3: Add the audit job

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Append the `audit` job to `ci.yml`**

Add under `jobs:`:

```yaml
  audit:
    name: Audit
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: rustsec/audit-check@v2
        with:
          token: ${{ secrets.GITHUB_TOKEN }}
```

`GITHUB_TOKEN` is provided automatically by GitHub Actions — no manual secret configuration needed.

- [ ] **Step 2: Commit and push**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add audit job"
git push origin HEAD
```

- [ ] **Step 3: Verify in GitHub Actions**

Confirm the `audit` job goes green. If it reports a CVE, the output will name the affected crate and advisory ID — address it before continuing.

---

## Task 4: Add the docker-build job

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Append the `docker-build` job to `ci.yml`**

Add under `jobs:`:

```yaml
  docker-build:
    name: Docker Build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: false
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

`push: false` means the image is built but never published. `type=gha` uses GitHub's built-in Actions cache for Docker layers.

- [ ] **Step 2: Commit and push**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add docker-build job"
git push origin HEAD
```

- [ ] **Step 3: Verify in GitHub Actions**

Confirm `docker-build` goes green. The first run will be slow (~5–8 min, cold Docker layer cache). Subsequent runs will be ~2–3 min. If it fails, the error will be in the Docker build output — usually a missing dependency or file.

---

## Task 5: Add the mutants job

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Append the `mutants` job to `ci.yml`**

Add under `jobs:`:

```yaml
  mutants:
    name: Mutation Testing
    runs-on: ubuntu-latest
    if: github.event_name == 'pull_request'
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: dtolnay/rust-toolchain@stable
      - uses: Swatinem/rust-cache@v2
      - name: Install native dependencies
        run: sudo apt-get update && sudo apt-get install -y --no-install-recommends pkg-config libssl-dev protobuf-compiler
      - uses: taiki-e/install-action@v2
        with:
          tool: cargo-mutants
        # If taiki-e/install-action fails to find a cargo-mutants binary,
        # replace the two lines above with:
        #   run: cargo install cargo-mutants --locked
      - name: Compute PR diff
        run: git diff origin/${{ github.base_ref }}...HEAD > /tmp/pr.diff
      - name: Run mutation tests
        run: cargo mutants --in-diff /tmp/pr.diff --timeout 120
      - name: Upload mutants output
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: mutants-out
          path: mutants.out/
          retention-days: 7
```

`if: github.event_name == 'pull_request'` means this job only runs on PRs, not on direct pushes to any branch. `if: always()` on the artifact upload ensures surviving mutants are uploaded even when the job fails — so you can inspect the `mutants.out/` report.

- [ ] **Step 2: Commit and push**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add mutation testing job"
git push origin HEAD
```

- [ ] **Step 3: Open a PR to trigger the mutants job**

The `mutants` job only runs on `pull_request` events. Open a draft PR from this branch against `main` on GitHub. The `mutants` job will appear alongside the other four jobs.

Because `--in-diff` scopes mutations to lines touched in this PR (which includes the new workflow YAML — not Rust code), the job should pass quickly with no surviving mutants. If surviving mutants are reported, download the `mutants-out` artifact to inspect which lines lack coverage, then add tests before continuing.

- [ ] **Step 4: Verify all five jobs are green on the PR**

In the PR checks panel, confirm: `Lint`, `Test`, `Audit`, `Docker Build`, `Mutation Testing` are all green.

---

## Task 6: Create `deploy.yml`

**Files:**
- Create: `.github/workflows/deploy.yml`

**Prerequisite:** Tasks 7 (secrets) must be completed before this job can succeed. Create the file now; it will stay red until secrets are configured.

- [ ] **Step 1: Create `.github/workflows/deploy.yml`**

```yaml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    name: Deploy to Railway
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Install Railway CLI
        run: npm install -g @railway/cli
      - name: Deploy server
        run: railway up --service ${{ secrets.RAILWAY_SERVICE_ID_SERVER }}
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}
      - name: Deploy worker
        run: railway up --service ${{ secrets.RAILWAY_SERVICE_ID_WORKER }}
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}
```

`railway up` without `--detach` blocks until Railway confirms the deployment succeeded or failed. Server deploys first; a failed server deploy prevents the worker from deploying.

- [ ] **Step 2: Commit and push**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: add Railway deploy workflow"
git push origin HEAD
```

This push will trigger `deploy.yml` on the branch — it will fail immediately with "missing secret" errors until Task 7 is complete. That is expected.

---

## Task 7: Configure repository secrets

This task is performed in the GitHub repository settings UI. No code changes.

- [ ] **Step 1: Get your Railway token**

In the Railway dashboard → Account Settings → Tokens → create a new token named `github-actions`. Copy the value.

- [ ] **Step 2: Get the Railway service IDs**

In the Railway dashboard, open each service (server and worker). The service ID is in the URL: `https://railway.app/project/<project-id>/service/<service-id>`. Copy both IDs.

- [ ] **Step 3: Add secrets to GitHub**

Go to `https://github.com/landovskis/mobilispect/settings/secrets/actions` → "New repository secret". Add all three:

| Secret name | Value |
|-------------|-------|
| `RAILWAY_TOKEN` | The token from Step 1 |
| `RAILWAY_SERVICE_ID_SERVER` | The server service ID from Step 2 |
| `RAILWAY_SERVICE_ID_WORKER` | The worker service ID from Step 2 |

- [ ] **Step 4: Re-run the failed deploy job**

In the Actions tab, find the failed `deploy` run from Task 6. Click "Re-run failed jobs". Confirm it goes green — both server and worker deploy successfully. Railway build logs stream directly in the job output.

---

## Task 8: Configure branch protection

This task is performed in the GitHub repository settings UI. No code changes.

- [ ] **Step 1: Open branch protection settings**

Go to `https://github.com/landovskis/mobilispect/settings/branches`. Click "Add branch protection rule". Set the branch name pattern to `main`.

- [ ] **Step 2: Enable required status checks**

Enable "Require status checks to pass before merging". In the search box, add each of the following (they appear after the first successful CI run):

- `Lint`
- `Test`
- `Audit`
- `Docker Build`
- `Mutation Testing`

- [ ] **Step 3: Enable additional protections**

Enable:
- "Require branches to be up to date before merging" — prevents stale-branch merges
- "Do not allow bypassing the above settings" — enforces rules for admins too

- [ ] **Step 4: Save and verify**

Click "Create". Open a new test PR. Confirm the merge button shows "All checks must pass" and lists the five required checks.
