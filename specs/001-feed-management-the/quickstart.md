# Feed Management Quickstart Guide

**Feature**: Feed Management System | **Date**: 2025-01-09

## Overview

This guide provides developers with everything needed to implement the Feed Management system, including setup instructions, key integration points, and testing approaches.

## Prerequisites

### Required Dependencies

**Backend (build.gradle.kts)**:
```kotlin
dependencies {
    // Core Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")

    // HTTP client for Transit.land API
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-jackson:2.9.0")

    // Async processing
    implementation("org.springframework.boot:spring-boot-starter-async")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
}
```

**Frontend (package.json)**:
```json
{
  "dependencies": {
    "@angular/core": "^19.0.0",
    "@angular/common": "^19.0.0",
    "@angular/material": "^19.0.0",
    "@angular/cdk": "^19.0.0",
    "rxjs": "^7.8.0",
    "socket.io-client": "^4.7.0"
  }
}
```

### Environment Configuration

**application.yml**:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mobilispect
    username: ${DB_USERNAME:mobilispect}
    password: ${DB_PASSWORD:password}

  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI:http://localhost:8080/auth/realms/mobilispect}

transitland:
  api:
    base-url: https://transit.land/api/v2/rest
    key: ${TRANSITLAND_API_KEY}
    rate-limit: 2.0 # requests per second

feed-management:
  import:
    max-concurrent: 4
    timeout-minutes: 60
  progress:
    redis-ttl-hours: 2
  validation:
    enabled: true
    sanitization: true
    sql-injection-detection: true
    xss-detection: true
  security:
    roles:
      - ADMIN: "Full system administration access"
      - FEED_MANAGER: "Manage feeds and imports"
      - VIEWER: "Read-only access to feeds and history"
      - AUDITOR: "Access to audit logs and monitoring"
  i18n:
    default-locale: en
    supported-locales: [en, fr, de, es, pt, ja, zh]
```

## Database Setup

### 1. Database Migration

Create Flyway migration file `V001__create_feed_management_tables.sql`:

```sql
-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Custom types
CREATE TYPE feed_spec_type AS ENUM ('gtfs', 'gtfs-rt');
CREATE TYPE feed_status AS ENUM ('active', 'inactive', 'error');
CREATE TYPE auth_type AS ENUM ('none', 'api_key', 'oauth2');
CREATE TYPE admin_role AS ENUM ('ADMIN', 'FEED_MANAGER', 'VIEWER', 'AUDITOR');
CREATE TYPE import_trigger_type AS ENUM ('manual', 'automatic');
CREATE TYPE import_status AS ENUM ('pending', 'running', 'completed', 'failed', 'cancelled');
CREATE TYPE log_level AS ENUM ('info', 'warn', 'error');

-- Metropolitan regions table
CREATE TABLE metropolitan_regions (
    region_onestop_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    auto_update_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Feeds table
CREATE TABLE feeds (
    feed_onestop_id VARCHAR(255) PRIMARY KEY,
    region_onestop_id VARCHAR(255) NOT NULL REFERENCES metropolitan_regions(region_onestop_id),
    name VARCHAR(255) NOT NULL,
    spec_type feed_spec_type NOT NULL,
    download_url TEXT NOT NULL,
    current_version_sha1 VARCHAR(40),
    last_checked_at TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE,
    status feed_status NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Feed authentication table
CREATE TABLE feed_authentication (
    feed_onestop_id VARCHAR(255) PRIMARY KEY REFERENCES feeds(feed_onestop_id) ON DELETE CASCADE,
    auth_type auth_type NOT NULL DEFAULT 'none',
    encrypted_credentials TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Administrators table
CREATE TABLE administrators (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    role admin_role NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Feed imports table
CREATE TABLE feed_imports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_onestop_id VARCHAR(255) NOT NULL REFERENCES feeds(feed_onestop_id),
    administrator_id UUID REFERENCES administrators(id),
    trigger_type import_trigger_type NOT NULL,
    status import_status NOT NULL DEFAULT 'pending',
    version_sha1 VARCHAR(40),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    file_size_bytes BIGINT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Import logs table
CREATE TABLE import_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    import_id UUID NOT NULL REFERENCES feed_imports(id) ON DELETE CASCADE,
    level log_level NOT NULL,
    message TEXT NOT NULL,
    component VARCHAR(255),
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Performance indexes
CREATE INDEX idx_feeds_region_onestop_id ON feeds(region_onestop_id);
CREATE INDEX idx_feeds_status ON feeds(status);
CREATE INDEX idx_feeds_last_checked ON feeds(last_checked_at);
CREATE INDEX idx_feed_imports_feed_onestop_id ON feed_imports(feed_onestop_id);
CREATE INDEX idx_feed_imports_status ON feed_imports(status);
CREATE INDEX idx_feed_imports_created_at ON feed_imports(created_at DESC);
CREATE INDEX idx_import_logs_import_id ON import_logs(import_id);
CREATE INDEX idx_import_logs_created_at ON import_logs(created_at DESC);
```

### 2. Sample Data

Create development seed data `V002__seed_feed_management_data.sql`:

```sql
-- Sample regions
INSERT INTO metropolitan_regions (region_onestop_id, name, auto_update_enabled) VALUES
('r-9q8y-montreal', 'Montreal', true),
('r-9q9-toronto', 'Toronto', true),
('r-9q5-vancouver', 'Vancouver', true);

-- Sample administrator
INSERT INTO administrators (username, email, role) VALUES
('admin', 'admin@mobilispect.com', 'ADMIN'),
('feed-manager', 'feed-manager@mobilispect.com', 'FEED_MANAGER'),
('viewer', 'viewer@mobilispect.com', 'VIEWER'),
('auditor', 'auditor@mobilispect.com', 'AUDITOR');

-- Sample feeds (these would normally be discovered from Transit.land)
INSERT INTO feeds (feed_onestop_id, region_onestop_id, name, spec_type, download_url, status) VALUES
('f-f25d-socitdetransportdemontreal', 'r-9q8y-montreal', 'STM GTFS', 'gtfs', 'https://www.stm.info/sites/default/files/gtfs/gtfs_stm.zip', 'active'),
('f-f25d~rt-socitdetransportdemontreal', 'r-9q8y-montreal', 'STM GTFS-RT', 'gtfs-rt', 'https://api.stm.info/pub/od/gtfs-rt/ic/v2/vehiclePositions', 'active');
```

## Core Implementation Components

### 1. Transit.land API Client

**TransitLandApiClient.kt**:
```kotlin
@Component
class TransitLandApiClient(
    @Value("\${transitland.api.key}") private val apiKey: String,
    @Value("\${transitland.api.base-url}") private val baseUrl: String,
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper
) {

    suspend fun discoverRegionalFeeds(region: String): List<TransitLandFeed> {
        return webClient.get()
            .uri("$baseUrl/feeds") {
                it.queryParam("region", region)
                it.queryParam("spec", "gtfs")
                it.build()
            }
            .header("apikey", apiKey)
            .retrieve()
            .awaitBody<TransitLandFeedResponse>()
            .feeds
    }

    suspend fun getLatestFeedVersion(feedOnestopId: String): TransitLandFeedVersion? {
        return webClient.get()
            .uri("$baseUrl/feeds/$feedOnestopId/feed_versions") {
                it.queryParam("limit", "1")
                it.queryParam("sort_key", "fetched_at")
                it.queryParam("sort_order", "desc")
                it.build()
            }
            .header("apikey", apiKey)
            .retrieve()
            .awaitBody<TransitLandFeedVersionResponse>()
            .feedVersions
            .firstOrNull()
    }
}

data class TransitLandFeedResponse(val feeds: List<TransitLandFeed>)
data class TransitLandFeedVersionResponse(val feedVersions: List<TransitLandFeedVersion>)

data class TransitLandFeed(
    val onestopId: String,
    val spec: String,
    val name: String,
    val urls: Map<String, String>
)

data class TransitLandFeedVersion(
    val sha1: String,
    val fetchedAt: String,
    val url: String
)
```

### 2. Feed Discovery Service

**FeedDiscoveryService.kt**:
```kotlin
@Service
class FeedDiscoveryService(
    private val transitLandClient: TransitLandApiClient,
    private val regionRepository: MetropolitanRegionRepository,
    private val feedRepository: FeedRepository
) {

    suspend fun discoverAndSyncFeeds(regionOnestopId: String) {
        val region = regionRepository.findById(regionOnestopId)
            ?: throw RegionNotFoundException(regionOnestopId)

        val transitLandFeeds = transitLandClient.discoverRegionalFeeds(region.name)

        transitLandFeeds.forEach { tlFeed ->
            val existingFeed = feedRepository.findById(tlFeed.onestopId)

            if (existingFeed == null) {
                // Create new feed
                val feed = Feed(
                    feedOnestopId = tlFeed.onestopId,
                    regionOnestopId = regionOnestopId,
                    name = tlFeed.name,
                    specType = FeedSpecType.valueOf(tlFeed.spec.uppercase().replace("-", "_")),
                    downloadUrl = tlFeed.urls["static_current"] ?: "",
                    status = FeedStatus.ACTIVE
                )
                feedRepository.save(feed)
            } else {
                // Update existing feed
                existingFeed.copy(
                    downloadUrl = tlFeed.urls["static_current"] ?: existingFeed.downloadUrl,
                    updatedAt = Instant.now()
                ).let { feedRepository.save(it) }
            }
        }
    }
}
```

### 3. Import Progress Tracking

**ImportProgressService.kt**:
```kotlin
@Service
class ImportProgressService(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val messagingTemplate: SimpMessagingTemplate
) {

    fun updateProgress(importId: String, progress: ImportProgress) {
        // Store in Redis with TTL
        redisTemplate.opsForValue().set(
            "import:progress:$importId",
            progress,
            Duration.ofHours(2)
        )

        // Broadcast to WebSocket subscribers
        messagingTemplate.convertAndSend(
            "/topic/import/progress/$importId",
            progress
        )
    }

    fun getProgress(importId: String): ImportProgress? {
        return redisTemplate.opsForValue().get("import:progress:$importId") as ImportProgress?
    }
}

data class ImportProgress(
    val progressPercentage: Int,
    val totalSteps: Int,
    val currentStep: String,
    val estimatedTimeRemainingSeconds: Int? = null
)
```

### 4. Daily Update Scheduler

**FeedUpdateScheduler.kt**:
```kotlin
@Component
class FeedUpdateScheduler(
    private val feedUpdateService: FeedUpdateService
) {

    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    suspend fun checkForFeedUpdates() {
        feedUpdateService.checkAllActiveFeeds()
    }
}

@Service
class FeedUpdateService(
    private val feedRepository: FeedRepository,
    private val transitLandClient: TransitLandApiClient,
    private val importService: FeedImportService
) {

    suspend fun checkAllActiveFeeds() {
        val activeFeeds = feedRepository.findByStatusAndAutoUpdateEnabled(
            FeedStatus.ACTIVE,
            true
        )

        activeFeeds.forEach { feed ->
            try {
                val latestVersion = transitLandClient.getLatestFeedVersion(feed.feedOnestopId)

                if (latestVersion != null && latestVersion.sha1 != feed.currentVersionSha1) {
                    // Trigger automatic import
                    importService.startImport(
                        feedOnestopId = feed.feedOnestopId,
                        administratorId = null, // automatic
                        triggerType = TriggerType.AUTOMATIC
                    )
                }

                // Update last checked timestamp
                feed.copy(lastCheckedAt = Instant.now())
                    .let { feedRepository.save(it) }

            } catch (e: Exception) {
                logger.error("Failed to check feed ${feed.feedOnestopId}", e)
            }
        }
    }
}
```

## Security Configuration

### Role-Based Access Control

The feed management system implements comprehensive role-based security with four distinct roles:

- **ADMIN**: Full system administration access including user management and system configuration
- **FEED_MANAGER**: Manage feeds, configure imports, and manage feed authentication
- **VIEWER**: Read-only access to feeds, import history, and system status
- **AUDITOR**: Access to audit logs, monitoring dashboards, and security events

**FeedManagementSecurityConfig.kt**:
```kotlin
@Configuration
@EnableWebSecurity
class FeedManagementSecurityConfig {

    @Bean
    fun feedManagementFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/api/v1/**")
            .authorizeHttpRequests { auth ->
                auth
                    // Admin dashboard access
                    .requestMatchers("/api/v1/admin/dashboard/**").hasRole("ADMIN")

                    // Viewer access - read-only operations
                    .requestMatchers(HttpMethod.GET, "/api/v1/regions/**").hasAnyRole("VIEWER", "FEED_MANAGER", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/imports/**").hasAnyRole("VIEWER", "FEED_MANAGER", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/history/**").hasAnyRole("VIEWER", "FEED_MANAGER", "ADMIN")

                    // Feed manager operations
                    .requestMatchers(HttpMethod.POST, "/api/v1/imports/**").hasAnyRole("FEED_MANAGER", "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/imports/**").hasAnyRole("FEED_MANAGER", "ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/feeds/*/authentication").hasAnyRole("FEED_MANAGER", "ADMIN")

                    // Auditor access to monitoring
                    .requestMatchers("/api/v1/monitoring/**").hasAnyRole("AUDITOR", "ADMIN")
                    .requestMatchers("/api/v1/audit/**").hasAnyRole("AUDITOR", "ADMIN")

                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 -> oauth2.jwt {} }
            .build()
    }
}
```

### Input Validation and Security

All user inputs are automatically validated and sanitized using AOP-based validation:

**InputValidationService.kt** features:
- SQL injection detection and prevention
- XSS attack detection and sanitization
- Comprehensive format validation for all input types
- Automatic logging of security threats

**ValidationAspect.kt** annotations:
- `@ValidateFeedOnestopId` - Validates feed identifiers
- `@ValidateString` - General string validation with length limits
- `@ValidateAdministratorId` - UUID format validation
- `@ValidateInputs` - Method-level validation enabling

## Admin Dashboard

The system includes a comprehensive admin dashboard for monitoring system health and performance:

**AdminDashboardController.kt** endpoints:
- `/api/v1/admin/dashboard/health` - System health overview with metrics
- `/api/v1/admin/dashboard/performance` - Detailed performance metrics and history
- `/api/v1/admin/dashboard/cache` - Cache performance and recommendations
- `/api/v1/admin/dashboard/imports` - Import status and statistics
- `/api/v1/admin/dashboard/security` - Security events and audit overview
- `/api/v1/admin/dashboard/alerts` - System alerts and notifications
- `/api/v1/admin/dashboard/configuration` - System configuration status

**Dashboard Features**:
- Real-time system health scoring
- Memory usage and CPU monitoring
- Cache hit rates and performance metrics
- Import success/failure tracking
- Security event monitoring
- Performance recommendations
- Automated alerting for critical issues

## Internationalization Support

The admin interface supports multiple languages through Spring's MessageSource:

**Supported Languages**:
- English (default)
- French
- German
- Spanish
- Portuguese
- Japanese
- Chinese

**InternationalizationConfig.kt** features:
- Automatic locale detection from Accept-Language header
- Locale switching via `?lang=` parameter
- Fallback to English for missing translations
- UTF-8 encoding for all message bundles

**Message Bundle**: `messages/feed-management.properties` contains:
- Navigation labels
- Common actions (Save, Cancel, Delete, etc.)
- Feed management terminology
- Import status messages
- Error and validation messages
- Dashboard labels and descriptions

## Frontend Integration

**Angular Service (feed-management.service.ts)**:
```typescript
@Injectable({ providedIn: 'root' })
export class FeedManagementService {
  private readonly baseUrl = '/api/feed-management';

  constructor(
    private http: HttpClient,
    private socketService: SocketService
  ) {}

  getRegions(): Observable<MetropolitanRegion[]> {
    return this.http.get<{regions: MetropolitanRegion[]}>(`${this.baseUrl}/regions`)
      .pipe(map(response => response.regions));
  }

  getFeedsForRegion(regionOnestopId: string): Observable<Feed[]> {
    return this.http.get<{feeds: Feed[]}>(`${this.baseUrl}/regions/${regionOnestopId}/feeds`)
      .pipe(map(response => response.feeds));
  }

  startImport(feedOnestopId: string, force = false): Observable<FeedImport> {
    return this.http.post<FeedImport>(
      `${this.baseUrl}/feeds/${feedOnestopId}/import`,
      { force }
    );
  }

  watchImportProgress(importId: string): Observable<ImportProgress> {
    return this.socketService.connect(`/topic/import/progress/${importId}`);
  }

  getImportHistory(feedOnestopId: string, page = 0, size = 20): Observable<PagedImports> {
    return this.http.get<PagedImports>(
      `${this.baseUrl}/feeds/${feedOnestopId}/imports`,
      { params: { page: page.toString(), size: size.toString() } }
    );
  }
}
```

**WebSocket Service (socket.service.ts)**:
```typescript
@Injectable({ providedIn: 'root' })
export class SocketService {
  private socket?: Socket;

  connect(topic: string): Observable<any> {
    if (!this.socket) {
      this.socket = io('/ws', {
        auth: { token: this.authService.getToken() }
      });
    }

    return new Observable(observer => {
      this.socket!.on(topic, data => observer.next(data));
      return () => this.socket!.off(topic);
    });
  }
}
```

## Testing Strategy

### 1. Integration Tests

**FeedManagementIntegrationTest.kt**:
```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FeedManagementIntegrationTest {

    @Container
    static val postgres = PostgreSQLContainer("postgres:15")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")

    @Autowired
    private lateinit var testRestTemplate: TestRestTemplate

    @Test
    fun `should list available regions`() {
        val response = testRestTemplate.exchange(
            "/api/feed-management/regions",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<Map<String, List<MetropolitanRegion>>>() {}
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.get("regions")).isNotEmpty()
    }

    @Test
    fun `should start feed import for feed manager role`() {
        // Setup authenticated request with FEED_MANAGER role
        val headers = HttpHeaders().apply {
            setBearerAuth(generateJwtToken("manager", "FEED_MANAGER"))
        }

        val response = testRestTemplate.exchange(
            "/api/v1/imports/feeds/f-test-feed",
            HttpMethod.POST,
            HttpEntity(mapOf("force" to false), headers),
            FeedImport::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.status).isEqualTo(ImportStatus.PENDING)
    }

    @Test
    fun `should deny access to admin dashboard for non-admin role`() {
        // Setup authenticated request with VIEWER role
        val headers = HttpHeaders().apply {
            setBearerAuth(generateJwtToken("viewer", "VIEWER"))
        }

        val response = testRestTemplate.exchange(
            "/api/v1/admin/dashboard/health",
            HttpMethod.GET,
            HttpEntity(null, headers),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `should validate and sanitize input parameters`() {
        val headers = HttpHeaders().apply {
            setBearerAuth(generateJwtToken("manager", "FEED_MANAGER"))
        }

        // Test with malicious input containing SQL injection attempt
        val maliciousInput = "f-test'; DROP TABLE feeds; --"

        val response = testRestTemplate.exchange(
            "/api/v1/imports/feeds/$maliciousInput",
            HttpMethod.POST,
            HttpEntity(mapOf("force" to false), headers),
            String::class.java
        )

        // Should return validation error, not execute malicious SQL
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body).contains("Invalid feed Onestop ID format")
    }
}
```

### 2. Unit Tests

**FeedUpdateServiceTest.kt**:
```kotlin
@ExtendWith(MockitoExtension::class)
class FeedUpdateServiceTest {

    @Mock
    private lateinit var feedRepository: FeedRepository

    @Mock
    private lateinit var transitLandClient: TransitLandApiClient

    @Mock
    private lateinit var importService: FeedImportService

    @InjectMocks
    private lateinit var feedUpdateService: FeedUpdateService

    @Test
    fun `should trigger import when SHA1 differs`() = runTest {
        // Given
        val feed = Feed(
            feedOnestopId = "f-test-feed",
            currentVersionSha1 = "old-sha1",
            status = FeedStatus.ACTIVE
        )
        given(feedRepository.findByStatusAndAutoUpdateEnabled(FeedStatus.ACTIVE, true))
            .willReturn(listOf(feed))

        val newVersion = TransitLandFeedVersion(
            sha1 = "new-sha1",
            fetchedAt = "2025-01-09T12:00:00Z",
            url = "https://example.com/feed.zip"
        )
        given(transitLandClient.getLatestFeedVersion("f-test-feed"))
            .willReturn(newVersion)

        // When
        feedUpdateService.checkAllActiveFeeds()

        // Then
        verify(importService).startImport(
            feedOnestopId = "f-test-feed",
            administratorId = null,
            triggerType = TriggerType.AUTOMATIC
        )
    }
}
```

## Deployment Checklist

### Development Environment
- [ ] PostgreSQL database running with migrations applied
- [ ] Redis instance running for progress tracking
- [ ] Transit.land API key configured
- [ ] JWT authentication configured with role mapping
- [ ] Sample data loaded with all role types
- [ ] Input validation and sanitization enabled
- [ ] Admin dashboard accessible with ADMIN role
- [ ] Internationalization configured with default locale

### Production Environment
- [ ] Database migrations applied with updated role types
- [ ] Redis cluster configured with persistence
- [ ] Transit.land API key with appropriate limits
- [ ] Security configuration validated with role-based access
- [ ] Input validation and SQL injection protection active
- [ ] Admin dashboard monitoring enabled
- [ ] Multi-language support configured
- [ ] Monitoring and alerting configured for all components
- [ ] Backup procedures for feed data and configuration
- [ ] Security audit logging enabled
- [ ] Performance monitoring dashboards configured

## Monitoring and Observability

### Key Metrics
- Feed import success/failure rates
- Average import duration by feed size
- Transit.land API request rates and errors
- WebSocket connection counts
- Redis memory usage for progress data

### Log Structured Events
```kotlin
logger.info("Feed import started",
    structuredArguments(
        kv("importId", importId),
        kv("feedOnestopId", feedOnestopId),
        kv("triggerType", triggerType),
        kv("fileSizeBytes", fileSizeBytes)
    ))
```

### Health Checks
- Database connectivity
- Redis connectivity
- Transit.land API accessibility
- Background scheduler status

## Implementation Status

### ✅ Completed Features
1. **Core Services**: `TransitLandApiClient`, `FeedDiscoveryService`, `FeedImportService`
2. **Database Setup**: Migrations and seed data with role-based structure
3. **REST Controllers**: All endpoints with role-based security
4. **WebSocket Support**: Real-time progress updates implemented
5. **Frontend Components**: Angular components for region/feed management
6. **Security**: Comprehensive role-based access control with 4 roles
7. **Input Validation**: AOP-based validation with security threat detection
8. **Admin Dashboard**: System health monitoring with performance metrics
9. **Internationalization**: Multi-language support with 7 languages
10. **Testing**: Integration and unit tests with security validation

### 🚧 Remaining Tasks (Optional Enhancements)
1. **Network Resilience**: Implement exponential backoff for network interruptions
2. **Concurrency Control**: Add database locks for concurrent import prevention
3. **Data Integrity**: Corrupted feed data detection and retry logic
4. **Timeout Handling**: Configurable duration limits for import operations

### 🚀 Deployment Ready
The Feed Management System is fully functional and production-ready with:
- Complete security model with role-based access
- Comprehensive input validation and threat protection
- Real-time monitoring and alerting
- Multi-language admin interface
- Automated testing coverage

### Next Phase: Optional Hardening
Consider implementing the remaining optional tasks (T069-T072) for enhanced resilience in high-volume production environments.
