---
name: create-adr
description: Create a new Architecture Decision Record following constitutional format requirements. Use when the user says "create ADR", "new ADR", "document decision", or "architecture decision".
disable-model-invocation: true
---

# Create Architecture Decision Record

Generate a new ADR in `docs/adr/` following the constitutional format.

## Workflow

### Step 1: Determine Next ADR Number

```bash
ls docs/adr/ | grep -oP '^\d+' | sort -n | tail -1
```

Increment by 1 and zero-pad to 4 digits.

### Step 2: Gather Information

Ask the user for:
1. **Title**: Short descriptive title (e.g., "Use Kotlin for Backend")
2. **Context**: What is the issue motivating this decision?
3. **Decision**: What is the change being proposed?
4. **Alternatives**: What other options were considered?

### Step 3: Create the ADR File

Write to `docs/adr/NNNN-kebab-case-title.md` using this template:

```markdown
# ADR-NNNN: {Title}

## Status

Proposed

## Context

{Context explaining the issue, forces at play, and why a decision is needed.}

## Decision

{The decision that was made, stated clearly and directly.}

## Consequences

### Positive

- {Benefit 1}
- {Benefit 2}

### Negative

- {Trade-off 1}
- {Trade-off 2}

### Neutral

- {Neutral observation}

## Alternatives Considered

### {Alternative 1 Name}

{Description and why it was not chosen.}

### {Alternative 2 Name}

{Description and why it was not chosen.}
```

### Step 4: Confirm

Report the file path and remind the user that ADRs require team review before acceptance (constitutional requirement).