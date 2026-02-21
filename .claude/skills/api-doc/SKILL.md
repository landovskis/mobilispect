---
name: api-doc
description: Generate or update an OpenAPI 3.1 spec from Spring REST controllers. Use when endpoints are added, modified, or when user says "api doc", "openapi", "swagger", or "document endpoints".
---

# Generate OpenAPI Specification

Produce an OpenAPI 3.1 YAML spec from the backend's `@RestController` classes.

## Workflow

### Step 1: Discover Controllers

Find all REST controllers:

```bash
grep -rl '@RestController' backend/src/main/kotlin/com/mobilispect/backend/
```

Known controllers and their base paths:
- `AgencyController` - `/agencies`, `/regions/{regionId}/agencies`
- `FeedImportController` - `/imports`
- `RegionController` - `/regions`
- `RouteController` - `/agencies/{agencyId}/routes`
- `FrequencyController` - route frequencies and variants
- `CommonSectionController` - `/common-sections`
- `StopController` - `/stops`

### Step 2: Extract Endpoint Metadata

For each controller, read the file and extract:
- Base `@RequestMapping` path
- Each `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`
- Method parameters: `@PathVariable`, `@RequestParam`, `@RequestBody`
- Return types (map to JSON schema)
- Any `@Valid` or validation annotations

### Step 3: Extract DTOs

Find data classes used in request/response bodies:
- Look for return types of controller methods
- Look for `@RequestBody` parameter types
- Map Kotlin data class fields to JSON Schema properties

### Step 4: Generate OpenAPI YAML

Write to `docs/api/openapi.yaml` using this structure:

```yaml
openapi: 3.1.0
info:
  title: Mobilispect API
  description: Transit analysis and feed management API
  version: 0.0.13-SNAPSHOT
servers:
  - url: http://localhost:8080
    description: Local development
paths:
  /agencies:
    get:
      summary: ...
      operationId: ...
      parameters: ...
      responses:
        '200':
          description: ...
          content:
            application/json:
              schema:
                ...
components:
  schemas:
    AgencyDto:
      type: object
      properties:
        ...
```

### Step 5: Validate

- Ensure all referenced schemas exist in `components/schemas`
- Ensure all path parameters have corresponding `parameters` entries
- Check for consistent naming (camelCase for properties, PascalCase for schemas)

### Step 6: Report

Display a summary: number of paths, operations, and schemas documented. Note any endpoints that couldn't be fully documented (e.g., missing return type info) and flag them for manual review.