# ADR-0002: Feed Authentication Strategy

## Status
Accepted

## Context
Many GTFS feeds require authentication to access their data. Feed providers use various authentication methods to control access, track usage, and comply with licensing requirements. The Feed Management System must support multiple authentication methods while maintaining security and operational efficiency.

### Common Feed Authentication Methods
1. **API Keys**: Query parameter or header-based
2. **HTTP Basic Authentication**: Username/password
3. **OAuth 2.0**: Client credentials or authorization code flow
4. **Custom Headers**: Provider-specific authentication headers
5. **IP Whitelisting**: Network-based access control
6. **No Authentication**: Public feeds

### Requirements
- Constitutional security requirements for credential management
- Support for multiple authentication methods per feed
- Secure storage and transmission of credentials
- Credential rotation and lifecycle management
- Audit trail for authentication events
- Regional compliance (GDPR, SOC2)

## Decision
We will implement a flexible feed authentication architecture supporting multiple authentication methods with secure credential management:

### Core Components
1. **FeedAuthentication Entity**: Database storage for feed credentials
2. **AuthenticationProvider Interface**: Pluggable authentication strategies
3. **CredentialVault Service**: Encrypted credential storage
4. **Authentication Manager**: Runtime credential resolution
5. **Audit Service**: Authentication event logging

### Supported Authentication Methods

#### 1. API Key Authentication
```kotlin
class ApiKeyAuthenticationProvider : AuthenticationProvider {
    override fun authenticate(request: HttpRequest, credentials: FeedCredentials): HttpRequest
}
```

#### 2. HTTP Basic Authentication
```kotlin
class BasicAuthenticationProvider : AuthenticationProvider {
    override fun authenticate(request: HttpRequest, credentials: FeedCredentials): HttpRequest
}
```

#### 3. OAuth 2.0 Client Credentials
```kotlin
class OAuth2ClientCredentialsProvider : AuthenticationProvider {
    override fun authenticate(request: HttpRequest, credentials: FeedCredentials): HttpRequest
}
```

#### 4. Custom Header Authentication
```kotlin
class CustomHeaderAuthenticationProvider : AuthenticationProvider {
    override fun authenticate(request: HttpRequest, credentials: FeedCredentials): HttpRequest
}
```

### Security Architecture
- **Encryption**: AES-256 encryption for stored credentials
- **Key Management**: AWS KMS or HashiCorp Vault integration
- **Access Control**: Role-based permissions for credential management
- **Audit Trail**: All authentication events logged and monitored
- **Rotation**: Automated credential rotation where supported

### Database Schema
```sql
CREATE TABLE feed_authentication (
    id UUID PRIMARY KEY,
    feed_onestop_id VARCHAR(255) NOT NULL,
    authentication_type VARCHAR(50) NOT NULL,
    encrypted_credentials BYTEA NOT NULL,
    encryption_key_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL
);
```

## Consequences

### Positive
- Support for diverse feed authentication requirements
- Secure credential storage and management
- Pluggable architecture for new authentication methods
- Comprehensive audit trail
- Constitutional security compliance

### Negative
- Increased system complexity
- Additional infrastructure requirements (encryption keys)
- Operational overhead for credential management
- Potential performance impact for authenticated requests

### Security Considerations
- **Credential Exposure**: Risk of credential leakage in logs or errors
- **Man-in-the-Middle**: HTTPS enforcement for all authenticated requests
- **Credential Stuffing**: Rate limiting and monitoring for authentication failures
- **Insider Threats**: Principle of least privilege for credential access

## Implementation Strategy

### Phase 1: Core Infrastructure
- FeedAuthentication entity and repository
- CredentialVault service with encryption
- Basic AuthenticationProvider interface

### Phase 2: Authentication Methods
- API Key authentication (most common)
- HTTP Basic authentication
- Custom header authentication

### Phase 3: Advanced Features
- OAuth 2.0 client credentials
- Credential rotation automation
- Advanced audit and monitoring

### Phase 4: Management Interface
- Frontend credential management UI
- Credential testing and validation
- Bulk credential import/export

## API Design

### Feed Authentication Endpoints
```yaml
POST /api/feed-management/feeds/{feedId}/authentication
PUT /api/feed-management/feeds/{feedId}/authentication
GET /api/feed-management/feeds/{feedId}/authentication
DELETE /api/feed-management/feeds/{feedId}/authentication
POST /api/feed-management/feeds/{feedId}/authentication/test
```

### Permission Requirements
- **FEED_MANAGER**: Full credential management
- **FEED_OPERATOR**: Read access to authentication status
- **FEED_VIEWER**: No access to authentication details

## Alternatives Considered

### Alternative 1: External Secret Management Service
**Rejected**: Added operational complexity and vendor lock-in

### Alternative 2: Feed Provider Proxying
**Rejected**: Legal and compliance issues with credential proxying

### Alternative 3: No Authentication Support
**Rejected**: Excludes many important commercial feeds

## Risks and Mitigations

### Risk: Credential Compromise
**Mitigation**: Regular rotation, monitoring, and encrypted storage

### Risk: Provider API Changes
**Mitigation**: Version-aware authentication and fallback strategies

### Risk: Performance Impact
**Mitigation**: Credential caching and connection pooling

## Related ADRs
- ADR-0001: Transit.land API Integration Strategy
- ADR-0003: Data Encryption Strategy (future)

## References
- [Constitutional Security Requirements](.specify/memory/constitution.md)
- [OWASP API Security Guidelines](https://owasp.org/www-project-api-security/)
- [Spring Security OAuth2 Documentation](https://docs.spring.io/spring-security/site/docs/current/reference/html5/#oauth2)