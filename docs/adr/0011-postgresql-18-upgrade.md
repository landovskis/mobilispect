# ADR 0011: PostgreSQL 18 Upgrade

**Date**: 2026-01-02
**Status**: Accepted
**Jira**: MSPEC-16

## Context

Mobilispect currently uses PostgreSQL 17 for its primary data storage across development, testing, and production environments. PostgreSQL 18 was officially released on September 25, 2025, bringing significant performance improvements, developer-friendly features, and operational enhancements.

The team needs to evaluate whether upgrading to PostgreSQL 18 provides meaningful benefits that justify the upgrade effort, considering:

1. **Performance**: Query execution improvements for GTFS data processing and route frequency analysis
2. **Developer Experience**: New features that simplify database schema design and maintenance
3. **Operational Excellence**: Improved upgrade paths, monitoring, and reliability features
4. **Constitutional Alignment**: Maintaining alignment with the latest stable database technology (Constitution Principle I)

## Decision

**Upgrade from PostgreSQL 17 to PostgreSQL 18 across all environments (development, testing, CI/CD, and production).**

### Rationale

PostgreSQL 18 provides several compelling improvements aligned with Mobilispect's architecture and performance requirements:

#### 1. Performance Enhancements (Constitution Principle IV)

**Asynchronous I/O (AIO) Subsystem**
- Demonstrated up to 3× performance improvements for storage reads
- Particularly beneficial for sequential scans, bitmap heap scans, and vacuum operations
- Directly supports Mobilispect's GTFS import workflows which perform large sequential scans during feed processing

**Skip Scan for B-tree Indexes**
- Improves query execution when prefix columns in multi-column indexes are omitted
- Enables better index utilization for route frequency queries that don't always filter on all indexed columns
- Reduces need for redundant index definitions

**Hash Join and GROUP BY Optimizations**
- Reduced memory usage and improved performance for aggregation queries
- Critical for route frequency calculations and analytics queries that perform extensive grouping

**Parallel GIN Index Builds**
- Faster index creation during schema migrations
- Reduces downtime during maintenance windows

#### 2. Developer-Friendly Features (Constitution Principle VII)

**UUIDv7 Support**
- Timestamp-ordered UUIDs improve index locality and caching
- Potential future migration path for distributed ID generation (currently using standard primary keys)
- Better query performance when filtering by UUID ranges

**Virtual Generated Columns (Default)**
- Compute values at read time instead of storing them
- Reduces storage overhead and write amplification
- Useful for computed fields like formatted route names or frequency classifications

**Temporal Constraints (WITHOUT OVERLAPS)**
- Native support for time-range constraints on PRIMARY KEY and UNIQUE constraints
- Simplifies modeling of schedule validity periods and route variant effective dates
- Eliminates need for custom trigger-based constraint validation

**Enhanced RETURNING Clauses**
- `OLD` and `NEW` support in RETURNING for INSERT, UPDATE, DELETE, MERGE
- Simplifies audit logging and optimistic locking implementations

#### 3. Operational Excellence (Constitution Principles III & V)

**Improved Major Version Upgrades**
- Planner statistics preserved across upgrades
- Faster post-upgrade performance recovery (no lengthy ANALYZE runs required)
- Reduced upgrade-related downtime and performance degradation

**Data Checksums by Default**
- Automatic detection of storage corruption
- Improves data integrity guarantees (Constitutional Security Requirement)
- No additional configuration needed for new environments

**NOT NULL Constraints as NOT VALID**
- Add NOT NULL constraints without full table scans
- Later validation without ACCESS EXCLUSIVE locks
- Enables zero-downtime schema migrations for large tables

#### 4. Security Enhancements (Constitution Principle V)

**OAuth 2.0 Support**
- Native integration with single-sign-on (SSO) systems
- Future-proofs authentication strategy for multi-tenant deployments
- Aligns with enterprise security best practices

#### 5. Hardware Acceleration

**ARM NEON and SVE Support**
- Optimized popcount operations for ARM processors
- Future-compatibility with ARM-based cloud instances
- Performance improvements for geospatial operations using PostGIS

**x86 AVX-512 for CRC32C**
- Faster checksum calculations on Intel/AMD processors
- Improves data integrity validation performance

### Implementation Plan

The upgrade requires updates across multiple layers:

#### 1. Development Environment
- **Docker Compose**: Update `postgres:17-alpine` → `postgres:18-alpine`
- **Local Testing**: Verify schema migrations and data compatibility

#### 2. Test Environment
- **Testcontainers**: Update test configuration from `PostgreSQLContainer("postgres:17-alpine")` → `PostgreSQLContainer("postgres:18-alpine")`
- **Integration Tests**: Run full test suite to verify compatibility
- **E2E Tests**: Verify end-to-end workflows with PostgreSQL 18

#### 3. Documentation
- **README.md**: Update PostgreSQL version references
- **CLAUDE.md**: Update constitutional technology stack specification
- **Constitution.md**: Update technology stack version (if referenced)
- **Spec Documents**: Update PostgreSQL version in feature specifications

#### 4. CI/CD Pipelines
- **Testcontainers-based CI**: No changes required (Testcontainers dynamically pulls images)
- **Verification**: Ensure CI pipelines pull `postgres:18-alpine` during test execution

#### 5. Production Migration (Future)
- **Backup Strategy**: Full database backup before upgrade
- **Upgrade Process**: Use `pg_upgrade` with preserved planner statistics
- **Validation**: Run schema migrations in read-only mode, verify data integrity
- **Rollback Plan**: Maintain PostgreSQL 17 backup for rollback capability
- **Performance Monitoring**: Monitor query performance post-upgrade (expected improvement)

### Migration Compatibility

PostgreSQL 18 maintains backward compatibility with PostgreSQL 17:
- **Schema Compatibility**: All DDL (Data Definition Language) remains compatible
- **Query Compatibility**: No breaking changes to SQL syntax or semantics
- **Extension Compatibility**: PostGIS and other extensions support PostgreSQL 18
- **Driver Compatibility**: PostgreSQL JDBC driver (currently 42.7.7) supports PostgreSQL 18

### Risk Assessment

**Low Risk**: PostgreSQL maintains strong backward compatibility across major versions
- **Schema**: No breaking changes to Flyway migrations
- **Queries**: No changes required to existing SQL or JPA queries
- **Extensions**: PostGIS and other extensions have PostgreSQL 18 support
- **Testing**: Testcontainers-based integration tests provide comprehensive validation

## Consequences

### Positive

1. **Performance**: 3× faster I/O for GTFS import operations and route frequency analysis
2. **Index Efficiency**: Skip scan enables better index utilization, reducing query planning overhead
3. **Operational Excellence**: Faster future upgrades with preserved planner statistics
4. **Data Integrity**: Checksums enabled by default improve corruption detection
5. **Developer Productivity**: Virtual generated columns and temporal constraints simplify schema design
6. **Future-Proofing**: OAuth 2.0 support prepares for enterprise SSO integration
7. **Hardware Optimization**: Better performance on ARM and x86 architectures

### Negative

1. **Upgrade Effort**: Requires coordinated update across development, testing, and production environments
   - **Mitigation**: Testcontainers-based testing validates compatibility before production deployment
2. **Potential Regression**: New optimizer features could theoretically degrade specific query plans
   - **Mitigation**: Comprehensive integration test suite validates performance before production
3. **Documentation Lag**: Team must update internal documentation and runbooks
   - **Mitigation**: Update documentation as part of this ADR implementation

### Neutral

1. **Breaking Changes**: None expected; PostgreSQL 18 maintains backward compatibility
2. **Extension Support**: All currently used extensions (PostGIS, Flyway) support PostgreSQL 18

## Alternatives Considered

### 1. Stay on PostgreSQL 17 (Rejected)

**Rationale**: Foregoes significant performance and operational improvements
- Misses 3× I/O performance gains critical for GTFS import workflows
- Loses improved upgrade experience for future major version migrations
- Delays adoption of developer-friendly features (temporal constraints, virtual columns)
- Does not align with Constitutional principle of using stable, latest technology

### 2. Wait for PostgreSQL 19 (Rejected)

**Rationale**: Unnecessary delay without clear benefit
- PostgreSQL 18 is stable (released September 2025)
- PostgreSQL 19 release timeline is 12+ months away (September 2026)
- Delaying upgrade postpones performance benefits for 12+ months
- No breaking changes announced in PostgreSQL 19 that would affect migration strategy

### 3. Gradual Rollout (PostgreSQL 17 in Production, 18 in Development) (Rejected)

**Rationale**: Creates environment inconsistency
- Violates development-production parity principle
- Risk of version-specific behavior differences causing production issues
- Increases testing burden (must validate both versions)
- Testcontainers makes version parity straightforward to maintain

## Related Decisions

- **ADR 0009**: Spring Modulith module boundaries (database schema ownership)
- **ADR 0003**: Spring Batch feed discovery (benefits from improved I/O performance)
- **ADR 0004**: GTFS library selection (benefits from faster sequential scans)

## Open Questions

None. PostgreSQL 18 is stable, backward-compatible, and ready for production use.

## Implementation Checklist

- [ ] Update `.devcontainer/docker-compose.yml` to `postgres:18-alpine`
- [ ] Update Testcontainer configurations to `postgres:18-alpine`
- [ ] Update `CLAUDE.md` PostgreSQL version reference
- [ ] Update `README.md` PostgreSQL version reference
- [ ] Update `.specify/memory/constitution.md` technology stack (if applicable)
- [ ] Update feature specification documents referencing PostgreSQL version
- [ ] Run full integration test suite to verify compatibility
- [ ] Update ADR index/catalog with this decision
- [ ] Plan production migration strategy (separate deployment plan)

## References

- [PostgreSQL 18 Release Notes](https://www.postgresql.org/docs/current/release-18.html)
- [PostgreSQL 18 Announcement](https://www.postgresql.org/about/news/postgresql-18-released-3142/)
- [What's New in PostgreSQL 18 - Better Stack Community](https://betterstack.com/community/guides/databases/postgresql-18-new-features/)
- [PostgreSQL 18 New Features - Neon](https://neon.com/postgresql/postgresql-18-new-features)
