# Feature Specification: Automated Montreal Council Meeting Summarization

**Feature Branch**: `004-automatically-summarize-the`
**Created**: 2026-04-30
**Status**: Draft
**Input**: User description: "Automatically summarize the latest Montreal city or borough council meeting notes. The system should be able to find and fetch the most recent published council meeting minutes or agenda from the Ville de Montréal website (or borough sites), then use an LLM (Claude API) to produce a concise, structured summary of the key decisions, motions, and topics discussed. The summary should be accessible via a backend API endpoint and stored for later retrieval. This is a backend-only feature for now."

---

## User Scenarios & Testing *(mandatory)*

### Primary User Story

An API consumer wants to quickly understand what was decided at the most recent Montreal city or borough council meeting without reading the full published minutes. They call an API endpoint and receive a concise, structured summary covering the key decisions, motions voted on, and main topics discussed, along with a link to the original source document and the meeting date.

### Acceptance Scenarios

1. **Given** the Ville de Montréal website has published new meeting minutes, **When** the system checks for updates, **Then** it fetches the latest document and generates a summary that is stored and retrievable via API.
2. **Given** a meeting summary has already been generated and stored, **When** the API consumer requests the latest summary, **Then** the stored summary is returned without re-fetching or re-processing the document.
3. **Given** a consumer requests the latest summary for a specific council body (e.g., city council or a named borough), **When** the request is made, **Then** the response contains the most recent available summary for that council body.
4. **Given** no new meeting minutes have been published since the last check, **When** the system checks for updates, **Then** no duplicate summary is created and the existing latest summary remains unchanged.
5. **Given** the council website is temporarily unavailable, **When** the system attempts to fetch new documents, **Then** the failure is recorded and the previously stored summary remains available to consumers.
6. **Given** a valid meeting document is fetched, **When** the summarization service processes it, **Then** the resulting summary includes: (a) a list of key decisions made, (b) motions and their outcomes, and (c) a brief narrative of main topics discussed.

### Edge Cases

- What happens when the council website returns a malformed or empty document?
- How does the system handle a meeting document that contains no actionable decisions (e.g., a ceremonial session)?
- What if two meetings are published on the same day for the same council body?
- How does the system behave if the summarization service is unavailable or returns an error?
- What if the document format (e.g., PDF vs HTML) changes on the source website?

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST check the official Ville de Montréal website and all 19 arrondissement borough websites for newly published council meeting minutes.
- **FR-002**: System MUST support both city council (Conseil municipal) and borough council (Conseil d'arrondissement) meetings.
- **FR-003**: System MUST fetch the full text of the most recently published meeting document when a new publication is detected.
- **FR-004**: System MUST generate a structured summary for each fetched meeting document, containing at minimum: key decisions, motions with their voting outcomes, and a narrative overview of main topics.
- **FR-005**: System MUST persist each generated summary alongside its source metadata (council body name, meeting date, source URL, document language) for subsequent retrieval.
- **FR-006**: System MUST expose an API endpoint to retrieve the latest available summary for a given council body.
- **FR-007**: System MUST expose an API endpoint to list available summaries, filterable by council body and date range.
- **FR-015**: Summary API endpoints MUST be publicly accessible without authentication, as meeting minutes are public government documents.
- **FR-008**: System MUST NOT re-process a document that has already been summarized.
- **FR-009**: System MUST record the outcome (success or failure reason) of each fetch and summarization attempt for operational observability.
- **FR-010**: System MUST include the original source URL and meeting date in every summary response so consumers can verify against the primary source.
- **FR-011**: System MUST handle transient failures when fetching documents by retrying before recording a permanent failure.
- **FR-012**: System MUST produce two summaries per meeting — one in French and one in English — regardless of the source document language (French).
- **FR-013**: System MUST retain summaries for [NEEDS CLARIFICATION: retention period not specified — indefinitely, 1 year, or another policy?].
- **FR-014**: System MUST check for new documents via a daily scheduled job that runs automatically on a fixed schedule (e.g., nightly).

### Key Entities

- **CouncilBody**: Represents a governing body whose meetings are tracked (e.g., Conseil municipal de Montréal, Conseil d'arrondissement du Plateau-Mont-Royal). Has a name, jurisdiction level (city or borough), and canonical source URL.
- **CouncilMeeting**: A single council meeting session. Has a date, reference to its CouncilBody, the source document URL, document format (PDF/HTML), and ingestion status (pending, fetched, summarized, failed).
- **MeetingSummary**: The AI-generated summary of a CouncilMeeting. Contains the structured summary text (decisions, motions, topics), the language of the summary (FR or EN), and a timestamp of when it was generated. Each CouncilMeeting has two MeetingSummary records — one per language.
- **SummarizationAttempt**: A log record for each attempt to fetch and summarize a meeting document, capturing timestamp, outcome, and failure reason if applicable. Supports operational observability.

---

## Clarifications

### Session 2026-04-30

- Q: How should the system discover and process new meeting documents? → A: Daily scheduled job — system automatically checks for new documents on a fixed schedule (e.g., nightly).
- Q: Which council bodies should be supported in the initial version? → A: City council (Conseil municipal) + all 19 arrondissement borough councils.
- Q: In what language should meeting documents be fetched and summaries be produced? → A: Both — produce a French summary and a separate English summary for each meeting.
- Q: Should the summary API endpoints require authentication? → A: No authentication — publicly accessible read-only endpoints.

---

## Review & Acceptance Checklist

### Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

### Requirement Completeness

- [ ] No [NEEDS CLARIFICATION] markers remain — **1 open clarification (FR-013)**
- [x] Requirements are testable and unambiguous (pending clarification resolution)
- [x] Success criteria are measurable
- [x] Scope is clearly bounded (backend-only; no frontend)
- [x] Dependencies and assumptions identified

---

## Execution Status

- [x] User description parsed
- [x] Key concepts extracted
- [x] Ambiguities marked
- [x] User scenarios defined
- [x] Requirements generated
- [x] Entities identified
- [ ] Review checklist passed — pending resolution of 1 clarification item

---
