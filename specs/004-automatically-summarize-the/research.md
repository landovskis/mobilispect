# Research: Automated Montreal Council Meeting Summarization

**Feature**: 004-automatically-summarize-the
**Date**: 2026-04-30

---

## 1. Ville de Montréal Document Discovery

### Decision
Scrape PDFs directly from `ville.montreal.qc.ca` using a predictable URL pattern rather than parsing portal index pages.

### Rationale
The portal index pages use an Oracle Portal URL scheme that is not machine-friendly and changes without notice. The direct PDF URL pattern is stable, documented via public examples, and covers both city council and all 19 borough councils under one domain.

### URL Patterns

**City council (Conseil municipal):**
```
https://ville.montreal.qc.ca/documents/Adi_Public/CM/CM_PV_ORDI_{YYYY-MM-DD}_{HHhMM}_FR.pdf
```
Example: `CM_PV_ORDI_2026-01-26_13h00_FR.pdf`

**Borough councils (Conseil d'arrondissement):**
```
https://ville.montreal.qc.ca/documents/Adi_Public/{BOROUGH_CODE}/{BOROUGH_CODE}_PV_ORDI_{YYYY-MM-DD}_{HHhMM}_FR.pdf
```

**Document type codes:**
- `PV` — procès-verbal (minutes, post-meeting)
- `ODJ` / `ODJP` — ordre du jour (agenda, pre-meeting)
- `ORDI` — session ordinaire; `SPEC` — session spéciale
- Language suffix: `FR` (French), `AN` (English — inconsistently available)

### Borough Codes (all 19 arrondissements)
| Code | Borough |
|------|---------|
| `CM` | Conseil municipal (city council) |
| `CA_Anj` | Anjou |
| `CA_Cot` | Côte-des-Neiges–Notre-Dame-de-Grâce |
| `CA_Lca` | LaSalle |
| `CA_Lsa` | Lachine |
| `CA_Mer` | Mercier–Hochelaga-Maisonneuve |
| `CA_Mtn` | Montréal-Nord |
| `CA_Otr` | Outremont |
| `CA_Pir` | Pierrefonds-Roxboro |
| `CA_Pmr` | Plateau-Mont-Royal |
| `CA_Rap` | Rivière-des-Prairies–Pointe-aux-Trembles |
| `CA_Rno` | Rosemont–La Petite-Patrie |
| `CA_SLe` | Saint-Laurent |
| `CA_SLn` | Saint-Léonard |
| `CA_Sud` | Le Sud-Ouest |
| `CA_Ver` | Verdun |
| `CA_VilE` | Ville-Émard–Côte-Saint-Paul |
| `CA_VilR` | Villeray–Saint-Michel–Parc-Extension |
| `CA_VEm` | Westmount (Note: independent city; confirm if in scope) |
| `CA_Ahu` | Ahuntsic-Cartierville |

> **Note**: Some borough codes need confirmation against the Open Government Portal dataset. The scraper must treat unresolvable codes as a configuration error, not a runtime failure.

### Meeting Frequency
- City council: ~10–11 sessions/year (monthly except August)
- Borough councils: ~10 sessions/year (monthly, occasionally 2 in June)
- Typical meeting time: city council 13:00 or 09:30; borough councils 19:00

### Document Discovery Strategy
Because the URL encodes date and time (which we don't know in advance), the scraper must:
1. Attempt candidate URLs for the current month (try last 45 days of date + common time slots)
2. Use HTTP HEAD requests to check existence cheaply before downloading
3. Maintain a `last_known_date` per council body to bound the search window

### Alternatives Considered
- **RSS/atom feed**: Does not exist for council minutes.
- **Open Data Portal API**: Exists but only covers some datasets; meeting minutes not included.
- **Portal HTML scraping**: Brittle; Oracle Portal markup is unstable.

---

## 2. PDF Text Extraction

### Decision
Use **Apache PDFBox** (version 3.x) for PDF-to-text extraction on the JVM.

### Rationale
PDFBox is Apache-licensed, well-maintained, requires no native dependencies, and is the standard choice for Kotlin/Java projects that need to extract text from government PDFs. iText is AGPL unless commercially licensed, which adds legal complexity.

### Dependency
```kotlin
implementation("org.apache.pdfbox:pdfbox:3.0.3")
```

### Known Limitations
- Multi-column layouts may produce interleaved text; council minutes are single-column so this is not a concern.
- Scanned PDFs (image-only) require OCR (e.g., Tesseract). Montreal council PDFs are digitally produced, not scanned, so plain text extraction is sufficient.

### Alternatives Considered
- **iText**: AGPL license requires commercial license for closed-source use.
- **Claude's native PDF API**: Supports direct PDF upload as a document block (up to 100 pages). This is a viable alternative to PDFBox that eliminates the extraction step entirely — evaluated and **deferred** for v2 to reduce API coupling in the extraction layer.

---

## 3. Claude API Integration

### Decision
Use the **Anthropic Java SDK** (`com.anthropic:anthropic-java-client-okhttp`) with a **single bilingual prompt** (FR + EN in one API call) and **prompt caching** with 1-hour TTL on the system prompt.

### Rationale
- Single prompt produces consistent summarization decisions across both languages and halves API cost compared to two separate calls.
- Prompt caching reduces costs by 90% on cache hits (0.1× input price). With 20 council bodies processed daily, the system prompt is reused 19 times after the first call — significant savings.
- The Anthropic Java SDK is idiomatic in Kotlin and works seamlessly with Spring Boot.

### Model
**`claude-sonnet-4-6`** — 200k token context window, up to 128k output tokens.

Rationale: A typical 50-page council minutes PDF is ~15,000–25,000 tokens, well within the 200k context. Sonnet 4.6 balances quality and cost for this task. Opus 4.7 is available if quality proves insufficient.

### Dependencies
```kotlin
implementation("com.anthropic:anthropic-java-client-okhttp:2.5.0")
```

### Prompt Caching Setup
- Cache the **system message** (summarization instructions + output format schema) with `"ttl": "1h"`.
- The **user message** (extracted PDF text) is not cached (unique per meeting).
- Minimum cacheable block: 1,024 tokens (Sonnet) — the system prompt comfortably exceeds this.

### System Prompt Structure
```
You are a municipal affairs analyst. Summarize the following Montreal city or
borough council meeting minutes. Produce your response with exactly two
top-level sections:

## Résumé (French)
- **Décisions clés**: [bullet list]
- **Motions et votes**: [bullet list with outcomes]
- **Sujets principaux**: [narrative paragraph]

## Summary (English)
- **Key decisions**: [bullet list]
- **Motions and votes**: [bullet list with outcomes]
- **Main topics**: [narrative paragraph]

Be concise. Include only information present in the document.
```

### API Key Management
- Stored via `@ConfigurationProperties(prefix = "app.anthropic")` → `AnthropicProperties.apiKey`
- Retrieved at runtime from environment variable `APP_ANTHROPIC_API_KEY` or Spring Boot secrets store.
- Never committed to VCS.

### Alternatives Considered
- **Two separate prompts (FR then EN)**: Higher cost, risk of inconsistency between language versions. Rejected.
- **OpenAI / Mistral**: Not considered; Claude API is the stated requirement.
- **Spring AI**: Abstracts over providers but adds indirection. Rejected in favour of direct SDK for simpler dependency graph.

---

## 4. Scheduling

### Decision
Use Spring's `@Scheduled(cron = "0 0 6 * * *")` (daily at 06:00 server time) on a `@Component` in the `councilmeeting` module. The existing `SchedulingConfig` in the backend is already configured with `@EnableScheduling` and a thread pool — no new configuration required.

### Rationale
The existing `SchedulingConfig` provides a 5-thread pool with graceful shutdown. Adding `@Scheduled` to a new component is the minimal incremental change.

### Controlled by Property
```yaml
# application.yml
app:
  council-meeting:
    scheduler:
      enabled: true
      cron: "0 0 6 * * *"  # Daily at 06:00
```

The `@ConditionalOnProperty` pattern from `SchedulingConfig` will be replicated for the new job so it can be disabled in tests.

### Alternatives Considered
- **Airflow DAG**: The project has an Airflow directory. Airflow is more appropriate for data pipelines with complex dependencies; a simple daily job is better kept in-process via Spring Scheduler.
- **Quartz**: Overkill for a single daily job; requires additional tables.

---

## 5. Module Architecture

### Decision
New standalone Spring Modulith module `councilmeeting` at `com.mobilispect.backend.councilmeeting` with **no dependencies on other backend modules**.

### Rationale
Council meeting summarization is a self-contained capability with no need for transit feed, agency, or route data. Keeping it independent avoids coupling and makes the module extractable to a separate service if needed.

### Flyway Migration
Next migration number: **V064** (`V064__create_council_meeting_tables.sql`)
