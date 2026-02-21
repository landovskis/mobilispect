---
name: pre-merge
description: Run constitutional pre-merge gates and generate a PR-ready summary. Use when user says "pre-merge", "merge check", "ready for PR", or "run gates".
disable-model-invocation: true
---

# Pre-Merge Gates

Run the constitutional pre-merge gate suite and produce a structured summary for the PR description.

## Workflow

### Step 1: Identify Changed Platforms

```bash
git diff --name-only main...HEAD
```

Determine which platforms are affected: backend, frontend/web, frontend/mobile, airflow.

### Step 2: Run Gates Per Platform

#### Backend (if changed)

Run each gate sequentially, capturing pass/fail:

1. **Format check**: `cd backend && ./gradlew ktfmtCheck`
2. **Unit tests**: `cd backend && ./gradlew test -x integrationTest`
3. **Integration tests**: `cd backend && ./gradlew integrationTest`
4. **OWASP security scan**: `./scripts/security-scan.sh`
5. **Coverage validation**: `./scripts/validate-coverage.sh backend`
6. **Modulith verification**: `cd backend && ./gradlew verifyModulith`

#### Frontend Web (if changed)

1. **Lint**: `cd frontend/web && npm run lint`
2. **Unit tests**: `cd frontend/web && npm test -- --watchAll=false`
3. **E2E tests**: `cd frontend/web && npm run e2e`

#### Airflow (if changed)

1. **Python lint**: Check for syntax errors in `airflow/dags/` and `airflow/pipeline/`

### Step 3: Generate Summary

Produce a markdown summary in this format:

```markdown
## Pre-Merge Gate Results

| Gate | Status |
|------|--------|
| Backend Format (ktfmt) | PASS/FAIL |
| Backend Unit Tests | PASS/FAIL |
| Backend Integration Tests | PASS/FAIL |
| Security Scan (OWASP) | PASS/FAIL |
| Coverage (>=80%) | PASS/FAIL (XX%) |
| Modulith Boundaries | PASS/FAIL |
| Web Lint | PASS/FAIL |
| Web Unit Tests | PASS/FAIL |
| Web E2E Tests | PASS/FAIL |

**Overall**: PASS/FAIL
```

Only include rows for platforms that were changed.

### Step 4: Report

- If all gates pass: display the summary and suggest it be included in the PR description.
- If any gate fails: display the summary, highlight failures, and suggest remediation steps.