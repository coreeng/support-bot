# ADR: Summary Data Export/Import for Knowledge Gap Analysis

**Status:** Implemented
**Date:** 2026-02-13
**Authors:** Support Bot Team

## Context

The support bot collects Slack thread data from the support channel.
To enable external AI-powered analysis of support patterns and knowledge gaps, we need a way to:

1. Export raw thread data for processing by external AI tools
2. Import structured analysis results back into the bot DB
3. Display aggregated insights in the UI

The analysis workflow involves transforming unstructured thread conversations into structured taxonomy data using AI prompts, which helps identify:
- Knowledge gaps
- Temporary platform issues
- Product usability issues
- Feature requests
- Task requests requiring platform team intervention

## Decision

We implemented two new REST endpoints for summary data operations:

### 1. Export Endpoint: `GET /summary-data/export`

**Purpose:** Export Slack thread texts as a ZIP file for external analysis.

**Input:**
- Query parameter: `days` (optional, default: 31) - Number of days to look back

**Output:**
- ZIP file containing individual text files
- Each file named with thread timestamp (e.g., `1234567890.123456.txt`)
- Thread content with:
  - Messages concatenated with double newlines
  - Author and timestamps removed
  - Slack mentions removed (pattern: `<?@?[UW][A-Z0-9]{8,}>?`)
  - Human names removed using pattern matching
  - Common capitalized words preserved (loaded from `commonly-capitalized-words.txt`)

**Example:**
```bash
GET /api/summary-data/export?days=31
→ Returns: content.zip
  ├── 1234567890.123456.txt
  ├── 1234567890.234567.txt
  └── 1234567890.345678.txt
```

### 2. Import Endpoint: `POST /summary-data/import`

**Purpose:** Import AI-analyzed data in TSV format.

**Input:**
- multipart/formdata with `file` field
- TSV file with columns: `ticket_id`, `Driver`, `Category`, `Feature`, `Summary`

**Output:**
- JSON response: `{ "recordsImported": 30, "message": "Import successful" }`

**Database Schema:**
```sql
CREATE TABLE analysis (
    id SERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL,
    driver TEXT NOT NULL,
    category TEXT NOT NULL,
    feature TEXT NOT NULL,
    summary TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Behavior:**
- Performs upsert operation (INSERT ... ON CONFLICT UPDATE) by `ticket_id`
- Allows updating existing analysis records with new insights

### 3. AI Transformation Workflow

**Process:**
```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. EXPORT                                                           │
│    GET /summary-data/export?days=31                                 │
│    → content.zip (thread text files)                                │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 2. GET PROMPT                                                       │
│    Download gap_analysis_taxonomy_summary-prompt.md                 │
│    - Click "Get Prompt" button in UI, OR                            │
│    - GET /gap_analysis_taxonomy_summary-prompt.md                   │
│    → Contains AI instructions for classification                    │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 3. EXTRACT                                                          │
│    unzip content.zip → individual .txt files                        │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 4. AI ANALYSIS (External)                                           │
│    For each thread file:                                            │
│    - Apply AI prompt (gap_analysis_taxonomy_summary-prompt.md)      │
│    - Extract: Ticket ID, Driver, Category, Feature, Summary         │
│    - Append to analysis.tsv                                         │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 5. IMPORT                                                           │
│    POST /summary-data/import (file=analysis.tsv)                    │
│    → Upserts records into analysis table                            │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 6. VISUALIZATION                                                    │
│    GET /analysis → UI displays aggregated insights                  │
│    - Top 5 Support Areas (by Driver)                                │
│    - Top 5 Knowledge Gaps (by Category)                             │
└─────────────────────────────────────────────────────────────────────┘
```

**AI Prompt Structure:**

The prompt (`gap_analysis_taxonomy_summary-prompt.md`) guides AI to:

**Step 1:** Determine Primary Support Driver (choose ONE):
- **Knowledge Gap** - Tenant unaware of existing features
- **Product Usability Problem** - UX/documentation issues
- **Product Temporary Issue** - Transient outages/incidents
- **Feature Request** - Genuinely missing capability
- **Task Request** - Requires platform team authority

**Step 2:** Classify into:
- **Category** - One of 10 categories (CI, CD, Networking, etc.)
- **Platform Feature** - Specific feature or "None"
- **Summary** - 1-2 sentence description

**Output Format:**
```
Ticket: 1234
Primary Driver: Knowledge Gap
Category: CI
Platform Feature: Artifactory
Reason: Tenant attempted to use docker login for Artifactory authentication instead of using the platform-provided credential helper.
```

**Converted to TSV:**
```tsv
ticket_id	Driver	Category	Feature	Summary
1234	Knowledge Gap	CI	Artifactory	Tenant attempted to use docker login for Artifactory authentication instead of using the platform-provided credential helper.
```
NOTE: the header row must be present

## Access Control

### Endpoint Access Matrix

Both endpoints require OAuth token mapped to a user with `SUPPORT_ENGINEER` or `LEADERSHIP` role.

| Endpoint               | Access Control | Roles Required                     |
|------------------------|----------------|------------------------------------|
| `/summary-data/export` | Restricted     | `LEADERSHIP` or `SUPPORT_ENGINEER` |
| `/summary-data/import` | Restricted     | `LEADERSHIP` or `SUPPORT_ENGINEER` |
| `/analysis`            | Restricted     | `LEADERSHIP` or `SUPPORT_ENGINEER` |

## Implementation Details

### Backend Components

**Controller:** `SummaryDataController.java`
- Handles HTTP requests for export/import
- Streams ZIP file for export
- Parses TSV for import

**Service:** `ThreadService.java`
- Fetches threads from Slack API
- Processes thread text (removes mentions, names)
- Loads common words from `common-words.txt` resource file

**Repository:** `AnalysisRepository.java`
- Performs batch upsert operations
- Queries aggregated data for UI

**Key Methods:**
- `getThreadsFromLastNDays(int days)` - Fetches threads
- `getThreadAsText(String channelId, String threadTs)` - Processes thread
- `removeHumanNames(String text)` - NLP-based name removal
- `batchUpsert(List<AnalysisRecord> records)` - Imports data

### Frontend Components

**API Routes:**
- `ui/src/app/api/summary-data/export/route.ts` - Proxies export request
- `ui/src/app/api/summary-data/import/route.ts` - Proxies import request

**UI Component:** `ui/src/components/knowledgegaps/knowledge-gaps.tsx`
- **Export Data** button (blue) - Downloads ZIP file
- **Prompt** button (blue) - Downloads AI prompt guide
- **Import Data** button (green) - Uploads TSV file
- Toast notifications for user feedback

**Analysis Display:** `GET /analysis` endpoint provides:
```json
{
  "knowledgeGaps": [
    {
      "name": "CI",
      "queryCount": 42,
      "coveragePercentage": 75,
      "queries": [...]
    }
  ],
  "supportAreas": [
    {
      "name": "Knowledge Gap",
      "queryCount": 2127,
      "coveragePercentage": 56,
      "queries": [...]
    }
  ]
}
```

## Taxonomy

### Support Drivers (Primary Classification)

1. **Knowledge Gap** - Tenant unaware of existing features or misunderstands platform
2. **Product Usability Problem** - Platform works but UX/docs are confusing
3. **Product Temporary Issue** - Transient outages, regressions, incidents
4. **Feature Request** - Genuinely missing capability
5. **Task Request** - Requires platform team authority

### Categories (10 total)

- Tenancy & Onboarding
- CI (Continuous Integration)
- CD (Continuous Deployment)
- Connectivity & Networking
- Platform-Provided Tooling
- Configuring Platform Features
- Deploying & Configuring Tenant Applications
- Monitoring & Troubleshooting Tenant Applications
- Security & Compliance
- Observability & Telemetry

### Platform Features (Examples)

- Egress, Ingress, Workload compute
- Artifactory, Central ECR, Docker registry
- GHA, Build environment, Pipeline shapes
- Kafka as a service, Persistence as a service
- 1TI, 1AI, HNC (Hierarchical Namespaces)
- Azure, AWS Accounts
- None (if no specific feature applies)

## Consequences

### Positive

✅ **Enables AI-Powered Analysis:** External tools can process thread data without system integration
✅ **Flexible Workflow:** Export → Analyze → Import cycle supports various AI tools
✅ **Data Persistence:** Analysis results stored in database for historical tracking
✅ **Upsert Support:** Can update analysis as understanding improves
✅ **Privacy-Aware:** Removes PII (names, mentions) from exported data
✅ **UI Integration:** Results displayed in Knowledge Gaps page
✅ **Trend Analysis:** Compare exports over time to track improvement

### Negative

⚠️ **Manual Process:** Requires external AI processing (not automated)

## References

- **AI Prompt:** `ui/public/gap_analysis_taxonomy_summary-prompt.md`
- **Controller:** `api/service/src/main/java/com/coreeng/supportbot/summarydata/rest/SummaryDataController.java`
- **Thread Service:** `api/service/src/main/java/com/coreeng/supportbot/summarydata/ThreadService.java`
- **Security Config:** `api/service/src/main/java/com/coreeng/supportbot/security/SecurityConfig.java`
- **UI Component:** `ui/src/components/knowledgegaps/knowledge-gaps.tsx`
- **Database Migration:** `api/service/src/main/resources/db/migration/V7__analysis.sql`

