---
name: release-notes
description: Generate release notes from conventional commits with constitutional quality results. Use when user says "release notes", "changelog", "prepare release", or "what changed".
disable-model-invocation: true
---

# Generate Release Notes

Produce release notes from conventional commits since the last tag, including constitutional quality gate results.

## Workflow

### Step 1: Find Last Release

```bash
git describe --tags --abbrev=0 2>/dev/null || echo "no-tag"
```

If no tag exists, use the initial commit as the baseline.

### Step 2: Collect Commits

```bash
git log {last_tag}..HEAD --pretty=format:"%h %s" --no-merges
```

### Step 3: Categorize by Convention

Parse conventional commit prefixes and group:

- **New Features** (`feat:`): New functionality added
- **Bug Fixes** (`fix:`): Defect corrections
- **Performance** (`perf:`): Performance improvements
- **Documentation** (`docs:`): Documentation changes
- **Refactoring** (`refactor:`): Code restructuring
- **Tests** (`test:`): Test additions or fixes
- **Chores** (`chore:`): Build, CI, dependency updates

### Step 4: Identify Breaking Changes

Look for commits containing `BREAKING CHANGE:` in the body or `!` after the type (e.g., `feat!:`).

```bash
git log {last_tag}..HEAD --grep="BREAKING CHANGE" --pretty=format:"%h %s"
```

### Step 5: Gather Quality Metrics (Constitutional Requirement)

Release notes must include coverage, security, performance, accessibility, and observability results.

Run and capture output from:
1. **Coverage**: `./scripts/validate-coverage.sh` (or report last known %)
2. **Security**: `./scripts/security-scan.sh` status
3. **Performance**: `./scripts/performance-check.sh` status

If scripts cannot be run, note "Pending CI verification" for each.

### Step 6: Generate Release Notes

Format as:

```markdown
# v{version} Release Notes

## Highlights

{1-2 sentence summary of the most significant changes}

## New Features

- {commit hash} {description}

## Bug Fixes

- {commit hash} {description}

## Breaking Changes

- {commit hash} {description}

## Other Changes

- {commit hash} {description}

## Quality Gate Results

| Metric | Status |
|--------|--------|
| Test Coverage | {X}% (>=80% required) |
| Security Scan (OWASP) | PASS/FAIL |
| Performance (p95 <=200ms) | PASS/FAIL |
| Accessibility (WCAG 2.1 AA) | PASS/FAIL |

## Contributors

{list of commit authors}
```

### Step 7: Suggest Version Bump

Based on the changes:
- **Major**: If breaking changes exist
- **Minor**: If new features exist (no breaking changes)
- **Patch**: If only fixes, refactors, or chores

Report the suggested next version and the generated release notes.