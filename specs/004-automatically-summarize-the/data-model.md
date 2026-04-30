# Data Model: Automated Montreal Council Meeting Summarization

**Feature**: 004-automatically-summarize-the
**Date**: 2026-04-30

---

## Entities

### CouncilBody

Represents a governing body whose meetings are tracked.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PK | Surrogate key |
| `code` | VARCHAR(20) | UNIQUE, NOT NULL | Source code used in URL (e.g., `CM`, `CA_Pmr`) |
| `name` | VARCHAR(255) | NOT NULL | Display name (e.g., "Conseil municipal de Montréal") |
| `jurisdictionLevel` | ENUM(`CITY`, `BOROUGH`) | NOT NULL | Whether this is city-level or borough-level |
| `documentBaseUrl` | VARCHAR(500) | NOT NULL | Base URL for document discovery |
| `active` | BOOLEAN | NOT NULL, DEFAULT true | Whether to include in daily discovery |

**Uniqueness**: `code` is the natural key.

**State transitions**: none (static configuration data, seeded via Flyway).

---

### CouncilMeeting

A single council meeting session for which documents have been detected.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PK | Surrogate key |
| `councilBodyId` | UUID | FK → CouncilBody, NOT NULL | Which council body held this meeting |
| `meetingDate` | DATE | NOT NULL | Date of the meeting |
| `sourceUrl` | VARCHAR(500) | NOT NULL | URL of the source PDF document |
| `documentLanguage` | CHAR(2) | NOT NULL, DEFAULT `'FR'` | Language of source document (`FR` or `AN`) |
| `ingestionStatus` | ENUM | NOT NULL | Current processing status (see below) |
| `discoveredAt` | TIMESTAMPTZ | NOT NULL | When the document was first detected |
| `processedAt` | TIMESTAMPTZ | NULLABLE | When summarization completed (both languages) |

**Uniqueness**: `(councilBodyId, meetingDate, sourceUrl)` — prevents duplicates if URL is reused.

**Ingestion status lifecycle:**
```
PENDING → FETCHED → SUMMARIZED
              ↓
           FAILED
```

- `PENDING`: Document URL discovered but not yet downloaded.
- `FETCHED`: PDF downloaded and text extracted; waiting for summarization.
- `SUMMARIZED`: Both FR and EN summaries generated and persisted.
- `FAILED`: A permanent failure occurred (retries exhausted); see `SummarizationAttempt` for reason.

---

### MeetingSummary

The AI-generated summary of a council meeting, one record per language.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PK | Surrogate key |
| `councilMeetingId` | UUID | FK → CouncilMeeting, NOT NULL | The meeting this summarizes |
| `language` | CHAR(2) | NOT NULL | Language of this summary (`FR` or `EN`) |
| `keyDecisions` | TEXT | NOT NULL | Bullet-point list of key decisions |
| `motionsAndVotes` | TEXT | NOT NULL | Bullet-point list of motions with voting outcomes |
| `mainTopics` | TEXT | NOT NULL | Narrative overview of main topics discussed |
| `generatedAt` | TIMESTAMPTZ | NOT NULL | When this summary was generated |
| `modelVersion` | VARCHAR(100) | NOT NULL | Claude model used (e.g., `claude-sonnet-4-6`) |

**Uniqueness**: `(councilMeetingId, language)` — exactly one summary per meeting per language.

**Retention**: Indefinitely (no TTL or deletion policy).

---

### SummarizationAttempt

Operational log of each fetch-and-summarize attempt, for observability.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PK | Surrogate key |
| `councilMeetingId` | UUID | FK → CouncilMeeting, NOT NULL | Meeting this attempt targeted |
| `attemptedAt` | TIMESTAMPTZ | NOT NULL | When the attempt started |
| `outcome` | ENUM(`SUCCESS`, `FETCH_FAILED`, `EXTRACTION_FAILED`, `SUMMARIZATION_FAILED`) | NOT NULL | Result category |
| `failureReason` | TEXT | NULLABLE | Human-readable error detail (null on SUCCESS) |
| `durationMs` | INTEGER | NULLABLE | Wall-clock time for the attempt in milliseconds |

---

## Relationships

```
CouncilBody 1 ──< CouncilMeeting 1 ──< MeetingSummary (max 2 per meeting: FR + EN)
                  CouncilMeeting 1 ──< SummarizationAttempt (0..n per meeting)
```

---

## Database Indexes

| Table | Index | Columns | Purpose |
|-------|-------|---------|---------|
| `council_body` | UNIQUE | `code` | Deduplication, lookup by code |
| `council_meeting` | UNIQUE | `council_body_id, meeting_date, source_url` | Prevent duplicate ingestion |
| `council_meeting` | INDEX | `council_body_id, ingestion_status` | Efficient scheduling queries |
| `council_meeting` | INDEX | `meeting_date DESC` | Latest-first retrieval |
| `meeting_summary` | UNIQUE | `council_meeting_id, language` | One summary per meeting per language |
| `meeting_summary` | INDEX | `council_meeting_id` | Fast join from meeting to summaries |
| `summarization_attempt` | INDEX | `council_meeting_id` | Observability queries |

---

## Seeded Data (Flyway V065)

All 20 council bodies (1 city + 19 boroughs) seeded as static configuration:

| Code | Name | Level |
|------|------|-------|
| `CM` | Conseil municipal de Montréal | CITY |
| `CA_Anj` | Conseil d'arrondissement d'Anjou | BOROUGH |
| `CA_Ahu` | Conseil d'arrondissement d'Ahuntsic-Cartierville | BOROUGH |
| `CA_Cot` | Conseil d'arrondissement de Côte-des-Neiges–NDG | BOROUGH |
| `CA_Lca` | Conseil d'arrondissement de LaSalle | BOROUGH |
| `CA_Lsa` | Conseil d'arrondissement de Lachine | BOROUGH |
| `CA_Mer` | Conseil d'arrondissement de Mercier–HM | BOROUGH |
| `CA_Mtn` | Conseil d'arrondissement de Montréal-Nord | BOROUGH |
| `CA_Otr` | Conseil d'arrondissement d'Outremont | BOROUGH |
| `CA_Pir` | Conseil d'arrondissement de Pierrefonds-Roxboro | BOROUGH |
| `CA_Pmr` | Conseil d'arrondissement du Plateau-Mont-Royal | BOROUGH |
| `CA_Rap` | Conseil d'arrondissement de RDP–PAT | BOROUGH |
| `CA_Rno` | Conseil d'arrondissement de Rosemont–LPP | BOROUGH |
| `CA_SLe` | Conseil d'arrondissement de Saint-Laurent | BOROUGH |
| `CA_SLn` | Conseil d'arrondissement de Saint-Léonard | BOROUGH |
| `CA_Sud` | Conseil d'arrondissement du Sud-Ouest | BOROUGH |
| `CA_Ver` | Conseil d'arrondissement de Verdun | BOROUGH |
| `CA_VilE` | Conseil d'arrondissement de Ville-Émard–CSP | BOROUGH |
| `CA_VilR` | Conseil d'arrondissement de Villeray–SMPE | BOROUGH |
| `CA_VEm` | Conseil d'arrondissement d'Ahuntsic (verify code) | BOROUGH |
