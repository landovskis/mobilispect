# Architecture Diagrams

This directory contains architectural diagrams for the Mobilispect system.

## GTFS Import Sequence Diagram

**File:** `gtfs-import-sequence.puml`

### Overview

This sequence diagram illustrates the complete GTFS (General Transit Feed
Specification) import flow, from user initiation through data persistence
and event publishing.

### Key Flows

1. **API Request & Job Launch** (0-12%)
   - User initiates import via REST API
   - Creates `FeedImport` record in database
   - Launches Spring Batch job asynchronously
   - Returns immediately to user with job ID

2. **Download & Extract** (12-37%)
   - Downloads GTFS ZIP file using curl
   - Extracts archive to temporary directory
   - Validates required GTFS files exist

3. **Parse & Process** (37-50%)
   - Uses Conveyal gtfs-lib to parse GTFS files
   - Parses: agencies, routes, stops, trips, stop_times, shapes
   - Handles large feeds with MapDB disk-backed storage

4. **Domain Import** (50-75%)
   - Persists agencies with Onestop ID generation
   - Persists routes with Onestop ID generation
   - Persists stops and links to variants

5. **Variant Identification** (50-62%)
   - Groups trips by stop sequence pattern
   - Generates SHA256 hash for each unique variant
   - Calculates average stop spacing:
     - **Tier 1:** Pre-calculated distances from `stop_times.shape_dist_traveled`
     - **Tier 2:** Perpendicular projection onto route shapes
     - **Tier 3:** Returns null if no shape data available

6. **Frequency Calculation** (62-75%)
   - Calculates headways from departure times
   - Aggregates by time period (AM/PM peak, off-peak)
   - Stores frequency metrics per variant

7. **Common Section Detection** (75-87%)
   - Identifies shared segments across route variants
   - Enables combined frequency analysis

8. **Finalization** (87-100%)
   - Publishes domain events
   - Updates import status to COMPLETED
   - Cleans up temporary files
   - Updates feed status to ACTIVE

### Rendering the Diagram

#### Option 1: PlantUML CLI (Recommended)

```bash
# Install PlantUML
brew install plantuml  # macOS
apt-get install plantuml  # Ubuntu/Debian

# Render to PNG
plantuml gtfs-import-sequence.puml

# Render to SVG (better quality)
plantuml -tsvg gtfs-import-sequence.puml

# Render to PDF
plantuml -tpdf gtfs-import-sequence.puml
```

#### Option 2: PlantUML Online Server

Visit: <https://www.plantuml.com/plantuml/uml/>

Paste the contents of `gtfs-import-sequence.puml`

#### Option 3: VS Code Extension

Install the [PlantUML extension](https://marketplace.visualstudio.com/items?itemName=jebbs.plantuml)

Right-click the `.puml` file → "PlantUML: Preview Current Diagram"

#### Option 4: IntelliJ IDEA Plugin

Install the
[PlantUML Integration](https://plugins.jetbrains.com/plugin/7017-plantuml-integration)
plugin

Open the `.puml` file to see live preview

### Component Reference

| Component | Responsibility |
|-----------|---------------|
| `ImportController` | REST API endpoint for import operations |
| `FeedImportService` | Orchestrates Spring Batch job launch |
| `FeedImportTasklet` | Spring Batch tasklet execution |
| `FeedManagementImportProcessor` | Download, extract, validate GTFS |
| `ConveyalGtfsParser` | GTFS file parsing |
| `TransitAnalysisFeedImportService` | Domain orchestration |
| `VariantIdentificationService` | Route variant identification |
| `StopSpacingCalculationService` | Stop spacing calculation |
| `FrequencyCalculationService` | Frequency/headway calculation |
| `CommonSectionDetectionService` | Shared segment detection |

See `backend/` source tree for full file paths.

### Progress Tracking

The import process reports progress at 8 distinct steps:

| Step | Progress | Description |
|------|----------|-------------|
| 0 | 0% | Starting import |
| 1 | 12% | Downloading feed |
| 2 | 25% | Extracting feed |
| 3 | 37% | Validating GTFS files |
| 4 | 50% | Processing GTFS data |
| 5 | 62% | (Implicit) Variant identification |
| 6 | 75% | (Implicit) Frequency calculation |
| 7 | 87% | (Implicit) Common section detection |
| 8 | 100% | Finalizing import |

Progress updates are broadcast via WebSocket to subscribed clients in real-time.

### Events Published

| Event | When | Payload |
|-------|------|---------|
| `RouteVariantIdentified` | Variant saved | `variantId`, `routeId` |
| `FrequencyCalculationCompleted` | Frequency done | `variantId`, `serviceDate` |
| `FeedImportCompleted` | Import complete | Stats: routes, variants, time |

### Error Handling

The import flow includes comprehensive error handling:

- **Download failures:** Network errors, invalid URLs, 404s
- **Extraction failures:** Corrupt archives, disk space
- **Validation failures:** Missing required GTFS files
- **Parsing failures:** Invalid GTFS data, encoding issues
- **Database failures:** Constraint violations, connection issues

All failures result in:

1. Import status set to `FAILED`
2. Error message recorded in `FeedImport.errorMessage`
3. WebSocket notification to UI
4. Spring Batch job marked as `FAILED`

### Performance Considerations

- **Async execution:** Import runs in background, UI doesn't block
- **MapDB storage:** Handles GTFS feeds larger than available RAM
- **Batch processing:** Spring Batch provides transaction management
- **Progress visibility:** Real-time WebSocket updates every 12-15%
- **Cleanup:** Temporary files removed after processing

### Testing

See test files:

- `FeedImportTaskletTest.kt`
- `ConveyalGtfsParserTest.kt`
- `VariantIdentificationServiceTest.kt`
- `StopSpacingCalculationServiceTest.kt`

---

**Last Updated:** 2025-12-25
**Version:** 1.0
**Author:** Generated with Claude Code
