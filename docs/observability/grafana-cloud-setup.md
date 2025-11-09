# Grafana Cloud Setup for Feed Management Monitoring

## Overview

This document describes how to set up Grafana Cloud dashboards for real-time monitoring of the Feed Management System's import operations, progress tracking, and system health.

## Prerequisites

1. **Grafana Cloud Account**: Set up at [grafana.com](https://grafana.com/)
2. **Prometheus Metrics**: Application must expose metrics at `/actuator/prometheus`
3. **Application Configuration**: Spring Boot actuator and micrometer configured

## Dashboard Installation

### 1. Import Dashboards

#### Main Feed Monitoring Dashboard
1. Navigate to your Grafana Cloud instance
2. Go to **Dashboards** → **Import**
3. Upload the dashboard JSON file: `docs/observability/grafana-feed-monitoring-dashboard.json`
4. Configure data source as your Prometheus instance

#### Automatic Updates Monitoring Dashboard
1. Navigate to your Grafana Cloud instance
2. Go to **Dashboards** → **Import**
3. Upload the dashboard JSON file: `docs/observability/grafana-automatic-updates-dashboard.json`
4. Configure data source as your Prometheus instance
5. This dashboard specifically monitors:
   - Automatic import success rates
   - Scheduler health and status
   - Feed version check performance
   - Transit.land API connectivity
   - Recent automatic import failures

#### Import History Analytics Dashboard
1. Navigate to your Grafana Cloud instance
2. Go to **Dashboards** → **Import**
3. Upload the dashboard JSON file: `docs/observability/grafana-import-history-analytics-dashboard.json`
4. Configure data source as your Prometheus instance
5. This dashboard provides comprehensive historical analysis:
   - Daily import trends over time
   - Regional import statistics and performance
   - Import status and trigger type distributions
   - Hourly import patterns for capacity planning
   - Import duration percentiles and performance metrics
   - Failure pattern analysis and error breakdowns
   - Feed-specific performance metrics
   - Automation vs manual import trends
   - Key performance indicators (KPIs) and success rates

### 2. Required Metrics

The dashboard expects the following Prometheus metrics to be available:

#### Import Metrics
```prometheus
# Total imports counter
feed_import_total{status="success|failure", region="region_name"}

# Import duration histogram
feed_import_duration_seconds{region="region_name"}

# Active imports gauge
feed_import_active_count

# Import success/failure counters
feed_import_success_total{region="region_name", trigger_type="MANUAL|AUTOMATIC"}
feed_import_failure_total{region="region_name", trigger_type="MANUAL|AUTOMATIC"}

# Scheduler health metrics
feed_scheduler_last_run_timestamp
up{job="feed-scheduler"}

# Transit.land API metrics
transitland_api_requests_total
transitland_api_request_failures_total

# Feed version check metrics
feed_version_check_duration_seconds
feed_version_check_total

# Historical analytics metrics
feed_import_total{status="success|failure|cancelled", region="region_name", feed_id="feed_id", trigger_type="MANUAL|AUTOMATIC", error_type="error_category"}
feed_import_duration_seconds_bucket{region="region_name", feed_id="feed_id"}
feed_import_failure_total{error_type="error_category", feed_id="feed_id", region="region_name"}
```

#### WebSocket Metrics
```prometheus
# Active WebSocket connections
websocket_connections_active

# WebSocket message rates
websocket_messages_sent_total
websocket_messages_received_total
```

#### API Performance Metrics
```prometheus
# API request duration
api_request_duration_ms{endpoint="/api/feed-management/*", method="GET|POST"}

# API request count
api_requests_total{endpoint="/api/feed-management/*", status="2xx|4xx|5xx"}
```

### 3. Backend Configuration

Add the following to your Spring Boot application to expose these metrics:

**application.yml**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: feed-management
      environment: ${ENVIRONMENT:dev}
```

**Custom Metrics Bean**:
```kotlin
@Component
class FeedImportMetrics(
    private val meterRegistry: MeterRegistry
) {

    private val importCounter = Counter.builder("feed_import_total")
        .description("Total number of feed imports")
        .register(meterRegistry)

    private val importDuration = Timer.builder("feed_import_duration_seconds")
        .description("Duration of feed imports")
        .register(meterRegistry)

    private val activeImportsGauge = AtomicInteger()

    init {
        Gauge.builder("feed_import_active_count")
            .description("Number of active imports")
            .register(meterRegistry) { activeImportsGauge.get() }
    }

    fun recordImportStart() {
        activeImportsGauge.incrementAndGet()
    }

    fun recordImportComplete(duration: Duration, status: String, region: String) {
        importCounter.increment(
            Tags.of(
                Tag.of("status", status),
                Tag.of("region", region)
            )
        )
        importDuration.record(duration)
        activeImportsGauge.decrementAndGet()
    }
}
```

### 4. Alert Configuration

Set up the following alerts in Grafana Cloud:

#### High Failure Rate Alert
```yaml
alert:
  name: "Feed Import High Failure Rate"
  condition: |
    (sum(rate(feed_import_failure_total[5m])) /
     sum(rate(feed_import_total[5m]))) > 0.1
  for: 2m
  annotations:
    summary: "Feed import failure rate is above 10%"
    description: "{{ $value }}% of feed imports are failing"
```

#### Long Running Import Alert
```yaml
alert:
  name: "Feed Import Taking Too Long"
  condition: feed_import_active_count > 0 and increase(feed_import_active_count[30m]) == 0
  for: 30m
  annotations:
    summary: "Feed import has been running for over 30 minutes"
    description: "Import may be stuck or experiencing issues"
```

#### WebSocket Connection Issues
```yaml
alert:
  name: "WebSocket Connection Drop"
  condition: websocket_connections_active < 1
  for: 1m
  annotations:
    summary: "No active WebSocket connections"
    description: "Real-time updates may not be working"
```

#### Automatic Feed Update Failures
```yaml
alert:
  name: "Automatic Feed Update Failure"
  condition: |
    (sum(rate(feed_import_failure_total{trigger_type="AUTOMATIC"}[5m])) /
     sum(rate(feed_import_total{trigger_type="AUTOMATIC"}[5m]))) > 0.2
  for: 5m
  annotations:
    summary: "High failure rate for automatic feed updates"
    description: "{{ $value }}% of automatic feed updates are failing"
    priority: "high"
    team: "feed-management"
```

#### Scheduled Job Not Running
```yaml
alert:
  name: "Feed Update Scheduler Not Running"
  condition: |
    absent(up{job="feed-scheduler"}) or
    (time() - feed_scheduler_last_run_timestamp > 90000)
  for: 5m
  annotations:
    summary: "Feed update scheduler is not running"
    description: "Automatic feed updates may not be working - scheduler last run > 25 hours ago"
    priority: "critical"
    team: "feed-management"
```

#### No Automatic Updates in 48 Hours
```yaml
alert:
  name: "No Automatic Updates Recently"
  condition: |
    absent(feed_import_total{trigger_type="AUTOMATIC"}) or
    (time() - last_over_time(feed_import_total{trigger_type="AUTOMATIC"}[2d])) > 172800
  for: 10m
  annotations:
    summary: "No automatic feed updates in the last 48 hours"
    description: "Feed update scheduler may be disabled or experiencing issues"
    priority: "medium"
    team: "feed-management"
```

#### Automatic Update Queue Backup
```yaml
alert:
  name: "Automatic Update Queue Backup"
  condition: feed_import_active_count{trigger_type="AUTOMATIC"} > 5
  for: 15m
  annotations:
    summary: "Too many automatic imports running simultaneously"
    description: "{{ $value }} automatic imports are currently active - possible queue backup"
    priority: "medium"
    team: "feed-management"
```

#### Transit.land API Connection Failure
```yaml
alert:
  name: "Transit.land API Connection Failure"
  condition: |
    (sum(rate(transitland_api_request_failures_total[5m])) /
     sum(rate(transitland_api_requests_total[5m]))) > 0.5
  for: 3m
  annotations:
    summary: "High failure rate for Transit.land API requests"
    description: "{{ $value }}% of Transit.land API requests are failing - automatic updates may be affected"
    priority: "high"
    team: "feed-management"
```

#### Historical Trend Alerts

##### Declining Success Rate Over Time
```yaml
alert:
  name: "Import Success Rate Declining"
  condition: |
    (sum(increase(feed_import_total{status="success"}[7d])) /
     sum(increase(feed_import_total[7d]))) <
    (sum(increase(feed_import_total{status="success"}[14d] offset 7d)) /
     sum(increase(feed_import_total[14d] offset 7d))) - 0.05
  for: 10m
  annotations:
    summary: "Import success rate has declined by more than 5% compared to previous week"
    description: "Current 7-day success rate is significantly lower than the previous 7-day period"
    priority: "medium"
    team: "feed-management"
```

##### Feed Performance Degradation
```yaml
alert:
  name: "Feed Performance Degradation"
  condition: |
    avg by (feed_id) (rate(feed_import_duration_seconds_sum[7d]) / rate(feed_import_duration_seconds_count[7d])) >
    avg by (feed_id) (rate(feed_import_duration_seconds_sum[14d] offset 7d) / rate(feed_import_duration_seconds_count[14d] offset 7d)) * 1.5
  for: 15m
  annotations:
    summary: "Feed {{ $labels.feed_id }} performance has degraded significantly"
    description: "Average import duration for feed {{ $labels.feed_id }} is 50% higher than the previous week"
    priority: "medium"
    team: "feed-management"
```

##### Unusual Import Pattern
```yaml
alert:
  name: "Unusual Import Volume Pattern"
  condition: |
    abs(sum(increase(feed_import_total[1h])) -
        avg_over_time(sum(increase(feed_import_total[1h]))[24h:1h])) >
    stddev_over_time(sum(increase(feed_import_total[1h]))[24h:1h]) * 3
  for: 2h
  annotations:
    summary: "Import volume is significantly different from normal pattern"
    description: "Current hourly import volume deviates by more than 3 standard deviations from the 24-hour average"
    priority: "low"
    team: "feed-management"
```

## Dashboard Panels

### 1. Feed Import Metrics (Graph)
- **Query**: `rate(feed_import_total[5m])`
- **Description**: Shows import rate over time
- **Alert Threshold**: > 10 imports/minute

### 2. Import Duration (Time Series)
- **Query**: `feed_import_duration_seconds`
- **Description**: Tracks how long imports take
- **Alert Threshold**: > 300 seconds (5 minutes)

### 3. Active Imports (Stat)
- **Query**: `sum(feed_import_active_count)`
- **Description**: Current number of running imports
- **Alert Threshold**: > 5 concurrent imports

### 4. Success Rate (Stat)
- **Query**: `(sum(feed_import_success_total) / (sum(feed_import_success_total) + sum(feed_import_failure_total))) * 100`
- **Description**: Overall import success percentage
- **Alert Threshold**: < 95%

### 5. WebSocket Connections (Stat)
- **Query**: `sum(websocket_connections_active)`
- **Description**: Number of active real-time connections
- **Alert Threshold**: < 1 (no connections)

### 6. API Response Time (Stat)
- **Query**: `avg(api_request_duration_ms{endpoint="/api/feeds"})`
- **Description**: Average API response time
- **Alert Threshold**: > 200ms (constitutional requirement)

## Usage

### Real-time Monitoring
1. **Active Imports**: Monitor the "Active Imports" panel to see current operations
2. **Progress Tracking**: WebSocket connections indicate real-time update capability
3. **Performance**: API response times must stay under 200ms per constitutional requirements

### Troubleshooting
1. **High Failure Rate**: Check import logs and Transit.land API status
2. **Slow Imports**: Review import duration trends and system resources
3. **WebSocket Issues**: Verify connection stability and client reconnection logic

### Maintenance
1. **Regular Review**: Check dashboard weekly for trends
2. **Alert Tuning**: Adjust thresholds based on operational experience
3. **Capacity Planning**: Use metrics to predict scaling needs

## Integration with Feed Management System

The dashboard integrates with the following system components:

1. **ImportProgressService**: Provides real-time progress metrics
2. **FeedImportService**: Emits import duration and success/failure metrics
3. **WebSocket Infrastructure**: Tracks connection count and message rates
4. **REST Controllers**: Measure API response times and request counts
5. **ImportHistoryService**: Provides historical data aggregation and analytics
6. **HistoryController**: Exposes historical metrics for dashboard consumption
7. **FeedUpdateScheduler**: Emits automatic import scheduling and execution metrics
8. **TransitLandApiClient**: Tracks external API performance and failure patterns

## Security Considerations

1. **Metrics Exposure**: Ensure `/actuator/prometheus` is secured appropriately
2. **Grafana Access**: Configure proper authentication for dashboard access
3. **Data Retention**: Set appropriate retention policies for metrics data
4. **PII Protection**: Ensure no personally identifiable information in metrics

## Constitutional Compliance

This monitoring setup ensures compliance with constitutional requirements:

- **Performance**: 200ms API response monitoring
- **Observability**: Comprehensive metrics and alerting
- **Reliability**: Real-time tracking of system health
- **Quality**: Success rate monitoring and failure alerting
