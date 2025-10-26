# ADR-0001: Transit.land API v2 Integration Strategy

## Status
Accepted

## Context
The Feed Management System requires integration with Transit.land's public GTFS feed directory to discover, validate, and download transit feeds. Transit.land provides a comprehensive API v2 that offers:

- Feed metadata and discovery
- GTFS download URLs
- Feed validation status
- Historical version tracking
- Geographic and operator-based search

### Requirements
- Constitutional requirement for 200ms API response times
- Rate limiting compliance (1000 requests/hour for free tier)
- Retry and error handling for service reliability
- Feed metadata validation before import
- Support for protected feeds with authentication

## Decision
We will integrate with Transit.land API v2 using a dedicated API client with the following architecture:

### Implementation Components
1. **TransitLandApiClient**: Reactive Spring WebClient-based service
2. **WebClient Configuration**: Optimized connection pooling and timeouts
3. **Data Models**: Type-safe Kotlin serialization classes
4. **Error Handling**: Circuit breaker and retry patterns
5. **Caching Strategy**: Redis caching for frequently accessed feed metadata

### Technical Architecture
- **Protocol**: HTTPS REST API
- **Authentication**: API key (when available) + User-Agent identification
- **Rate Limiting**: Client-side throttling with exponential backoff
- **Timeout Configuration**: 30s connection, 45s read timeout
- **Connection Pooling**: Max 50 connections, 45s idle timeout
- **Retry Policy**: 3 attempts with exponential backoff

### API Endpoints Used
- `GET /feeds/{onestop_id}` - Feed metadata
- `GET /feeds/{onestop_id}/feed_versions` - Version history
- `GET /feeds` - Search and discovery
- `GET /feeds/{onestop_id}/download_url` - GTFS download

## Consequences

### Positive
- Standardized feed discovery and validation
- Real-time feed metadata and status
- Automatic GTFS URL resolution
- Community-maintained feed directory
- No hosting costs for feed metadata

### Negative
- External service dependency
- Rate limiting constraints for high-volume usage
- Potential service availability issues
- Limited control over feed metadata accuracy

### Risks and Mitigations
- **Service Downtime**: Implement circuit breaker pattern and fallback to cached metadata
- **Rate Limiting**: Client-side throttling and request queuing
- **Data Quality**: Additional validation of feed metadata and URLs
- **Authentication**: Secure API key management and rotation

## Alternatives Considered

### Alternative 1: Direct GTFS Directory Maintenance
**Rejected**: High maintenance overhead and incomplete coverage

### Alternative 2: OpenMobilityData Integration
**Rejected**: Less comprehensive API and documentation

### Alternative 3: Multiple Provider Strategy
**Deferred**: Added complexity not justified for MVP

## Implementation Notes
- Client configured in `TransitLandWebClientConfig`
- Service implemented in `TransitLandApiClient`
- Error handling with custom `TransitLandApiException`
- Metrics and observability through Micrometer
- Constitutional compliance for performance and reliability

## Related ADRs
- ADR-0002: Feed Authentication Strategy

## References
- [Transit.land API v2 Documentation](https://www.transit.land/documentation/rest-api)
- [Constitutional Performance Requirements](.specify/memory/constitution.md)
- [Spring WebClient Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)