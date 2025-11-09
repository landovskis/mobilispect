# Implementation Plan: Feed Management System

**Branch**: `001-feed-management-the` | **Date**: 2025-01-09 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-feed-management-the/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Implement a comprehensive feed management system enabling administrators to import and monitor transit feed data from metropolitan regions. The system will automatically discover and sync regions from the transit.land API into the database, creating feed records with metadata, URLs, and authentication requirements. Administrators can then initiate imports for these discovered feeds, monitor progress in real-time, and view import history. The system supports role-based access control and automated daily updates using content hash comparison for change detection.

## Technical Context

**Language/Version**: Kotlin 2.0+ (Spring Boot backend), TypeScript (Angular frontend)
**Primary Dependencies**: Spring Boot, Spring Security, PostgreSQL 17, Redis 8.2, Retrofit/OkHttp (transit.land integration), Jackson (JSON processing), Angular 19 LTS, RxJS, Grafana Cloud (observability)
**Storage**: PostgreSQL 17 for feed metadata, import history, content hashes; Redis 8.2 for transient progress; File system/S3 for GTFS archives
**Testing**: JUnit 5, Testcontainers, Mockito (backend); Jest, Cypress (frontend)
**Target Platform**: Linux server deployment, web-based admin interface
**Project Type**: Web application (Spring Boot backend + Angular frontend)
**Performance Goals**: 200ms API response time, support 1000+ concurrent feed checks, handle feeds up to 1GB
**Constraints**: <200ms p95 response time, 95% import success rate, 24/7 availability for automatic updates
**Scale/Scope**: 100+ metropolitan regions, 10+ concurrent administrators, daily automated operations

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Phase 1 Design Review ✅

**Post-Design Constitutional Compliance Verified**:

### Code Quality First ✅

- **DRY/YAGNI/SOLID**: Clean separation with dedicated services (`TransitLandApiClient`, `FeedDiscoveryService`, `ImportProgressService`)
- **Linting/Formatting**: ktlint for Kotlin backend, ESLint/Prettier for Angular frontend
- **Code Reviews**: All changes require review, especially integration with external APIs
- **Architecture**: Clear domain boundaries with feeds, imports, and authentication modules

### Test-Driven Development ✅

- **Unit Tests**: 80%+ coverage planned for feed processing, validation, and API integration logic
- **Integration Tests**: End-to-end tests for transit.land API integration and GTFS processing with Testcontainers
- **Contract Tests**: OpenAPI specification provides contract validation between frontend and backend
- **Test Strategy**: Comprehensive testing approach documented in quickstart guide

### Cross-Platform UX Consistency ✅

- **Angular Frontend**: Consistent admin interface with Material Design and WebSocket real-time updates
- **Responsive Design**: Mobile-friendly admin interface for monitoring imports
- **Light/Dark Mode**: Required across admin interface per constitutional requirements
- **API Design**: RESTful API with proper HTTP status codes and error handling

### Performance Standards ✅

- **API Response**: 200ms p95 requirement maintained with async processing and Redis caching
- **Database Optimization**: Proper indexing strategy for PostgreSQL 17 with performance-focused queries
- **Background Processing**: Spring async processing with controlled thread pools for imports
- **Efficient Progress Tracking**: Redis 8.2-based transient data storage to reduce database load

### Observability & Monitoring ✅

- **Grafana Cloud Integration**: Centralized observability platform for monitoring, alerting, and visualization
- **Structured Logging**: Import progress, errors, and API interactions with structured arguments sent to Grafana Cloud
- **Metrics**: Feed import success rates, processing times, Transit.land API usage exported to Grafana Cloud
- **Health Checks**: Database, Redis 8.2, Transit.land API connectivity monitoring with Grafana Cloud dashboards
- **WebSocket Monitoring**: Real-time connection tracking and performance metrics visualized in Grafana Cloud
- **Business Dashboards**: Critical business metrics and system health indicators displayed in Grafana Cloud

### Architecture Decision Records ✅

- **External Integration**: Transit.land API v2 with rate limiting and SHA1-based change detection
- **Feed Discovery**: Automated sync from transit.land API to populate database with region feeds
- **Data Storage**: PostgreSQL 17 for persistent data, Redis 8.2 for transient progress, file system/S3 for GTFS archives
- **Authentication Strategy**: Role-based access control with three distinct permission levels
- **Progress Architecture**: Clean separation of persistent state vs. transient progress data

## Project Structure

### Documentation (this feature)

```
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```
backend/
├── src/main/kotlin/com/mobilispect/backend/
│   ├── feed/                    # Feed management domain
│   │   ├── controller/          # REST endpoints for feed operations
│   │   ├── service/             # Business logic for imports, monitoring, and discovery
│   │   │   ├── FeedDiscoveryService.kt      # Sync feeds from transit.land API
│   │   │   ├── FeedImportService.kt         # Handle feed imports
│   │   │   └── ImportProgressService.kt     # Track import progress
│   │   ├── repository/          # Data access for feeds and history
│   │   ├── model/               # Feed entities and DTOs
│   │   │   ├── FeedEntity.kt                # Database entity for feeds
│   │   │   └── FeedAuthentication.kt        # Authentication config per feed
│   │   └── integration/         # Transit.land API client
│   │       └── TransitLandApiClient.kt      # Retrofit client for transit.land
│   ├── security/                # Role-based access control
│   └── config/                  # Configuration for external APIs
└── src/test/kotlin/
    ├── unit/                    # Unit tests for services and utilities
    ├── integration/             # Integration tests for API clients
    └── contract/                # Contract tests for REST APIs

frontend/
├── src/app/
│   ├── feed-management/         # Feed management feature module
│   │   ├── components/          # UI components for region selection, progress
│   │   ├── services/            # Angular services for API integration
│   │   ├── models/              # TypeScript interfaces for feed data
│   │   └── pages/               # Main admin interface pages
│   ├── shared/                  # Shared components and utilities
│   └── core/                    # Authentication and routing
└── src/test/
    ├── unit/                    # Jest unit tests
    └── e2e/                     # Cypress end-to-end tests
```

**Structure Decision**: Web application using existing backend/frontend structure. Feed management will be implemented as a dedicated domain module in the backend with corresponding Angular feature module in frontend, following the established project conventions.

## Feed Discovery Architecture

### Overview

The system automatically discovers and synchronizes transit feed data from the transit.land API into the local database. This provides a persistent, queryable source of available feeds that administrators can import.

### Discovery Workflow

1. **Initial Sync**: System performs initial discovery on startup or via admin trigger
2. **API Query**: `FeedDiscoveryService` queries transit.land API for all available feeds
3. **Data Mapping**: API responses mapped to `FeedEntity` objects with region metadata
4. **Database Upsert**: Feeds inserted or updated in PostgreSQL (idempotent operation)
5. **Authentication Config**: Feed authentication requirements stored in `FeedAuthentication` entity
6. **Frontend Display**: Region feeds page queries database records to display available feeds

### Transit.land API Integration

**Endpoints Used**:

- `/api/v2/rest/feeds` - List all available feeds with metadata
- `/api/v2/rest/operators` - Get operator information for regions

**Data Extracted**:

- Feed ID (onestop_id)
- Feed URLs (GTFS static, GTFS-RT)
- Region/operator information
- Authentication requirements
- Last updated timestamps

### Database Schema

**FeedEntity**:

- `id: UUID` - Primary key
- `onestopId: String` - transit.land unique identifier
- `regionName: String` - Metropolitan region name
- `staticFeedUrl: String?` - GTFS static feed URL
- `realtimeFeedUrl: String?` - GTFS-RT feed URL
- `operatorName: String?` - Transit operator name
- `lastDiscovered: Instant` - Last sync timestamp
- `authenticationId: UUID?` - FK to FeedAuthentication

**FeedAuthentication**:

- `id: UUID` - Primary key
- `feedId: UUID` - FK to FeedEntity
- `authType: AuthType` - Enum: NONE, API_KEY, OAUTH2
- `encryptedCredentials: String?` - Encrypted auth credentials
- `headerName: String?` - HTTP header for API key (e.g., X-API-Key)

### Discovery Scheduling

- **Initial Sync**: On application startup
- **Periodic Sync**: Daily scheduled task to refresh feed list
- **Manual Trigger**: Admin interface button to force immediate sync
- **Error Handling**: Retry logic with exponential backoff for transit.land API failures

### Implementation Notes

- Discovery is separate from import - it only populates available feeds
- Idempotent operations allow safe re-sync without data duplication
- Frontend always reads from database, not directly from transit.land API
- Authentication configuration stored securely with encryption at rest

## Complexity Tracking

*Fill ONLY if Constitution Check has violations that must be justified*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
