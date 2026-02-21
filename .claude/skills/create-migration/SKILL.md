---
name: create-migration
description: Create a new Flyway versioned migration with correct numbering. Use when user says "create migration", "new migration", "add migration", "schema change", or "alter table".
disable-model-invocation: true
---

# Create Flyway Migration

Generate a correctly numbered Flyway migration SQL file in the backend.

## Workflow

### Step 1: Determine Next Version Number

List existing migrations and find the highest version:

```bash
ls backend/src/main/resources/db/migration/V*.sql | grep -oP 'V\K\d+' | sort -n | tail -1
```

Increment by 1 and zero-pad to 3 digits (e.g., V061 -> V062).

### Step 2: Gather Information

Ask the user for:
1. **Purpose**: What schema change is needed? (create table, alter table, add index, etc.)
2. **Description**: Short kebab-case description for the filename

### Step 3: Create the Migration File

Write to `backend/src/main/resources/db/migration/V{NNN}__{description}.sql`

Note: Flyway requires **double underscore** between version and description.

Follow these conventions from existing migrations:
- Use lowercase SQL keywords for consistency with the codebase
- Include comments explaining the purpose
- For `CREATE TABLE`: always include `id` as primary key, use appropriate constraints
- For destructive changes: include a comment noting what data may be lost
- Always make migrations idempotent where possible (`IF NOT EXISTS`, `IF EXISTS`)

Template:

```sql
-- V{NNN}: {Purpose description}
-- Constitutional requirement: All schema changes via versioned migrations

{SQL statements}
```

### Step 4: Validate

Remind the user to:
1. Run the migration locally: start the devcontainer or run `./backend/gradlew flywayMigrate`
2. Verify the migration is reversible if possible
3. Never modify this file after it has been applied (constitutional requirement)

### Important Rules

- **Never modify existing migrations** - always create a new one
- **Version gaps are OK** - the project already has gaps (V020 to V025, V032 to V035)
- **Double underscore** separator is required by Flyway
- **One concern per migration** - don't mix unrelated schema changes