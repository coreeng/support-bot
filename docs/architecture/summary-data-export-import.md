# ADR: Summary Data Export/Import for Knowledge Gap Analysis

## Context

To enable external AI-powered analysis of support patterns and knowledge gaps, we need a way to:

1. Export raw thread data for processing by external AI tools
2. Get AI prompt for analysis
3. Run AI analysis on the exported data
3. Import structured analysis results back into the bot DB
4. Display aggregated insights in the UI

## Decision

We implemented three new REST endpoints for summary data operations.

### 1. Service Export Endpoint: `GET /summary-data/export`

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

### 2. Service Import Endpoint: `POST /summary-data/import`

**Purpose:** Import AI-analyzed data in JSONL format.

**Input:**
- multipart/formdata with `file` field
- JSONL file with fields: `ticketId`, `driver`, `sategory`, `feature`, `summary`

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

### 3. NextJS Prompt Endpoint: `GET /api/prompt`

**Purpose:** Download the AI analysis prompt file.

**Input:**
- None

**Output:**
- Markdown file: `gap_analysis_taxonomy_summary-prompt.md`
- Content-Type: `text/markdown; charset=utf-8`
- Content-Disposition: `attachment; filename="gap_analysis_taxonomy_summary-prompt.md"`

**Implementation:**
- Next.js API route at `ui/src/app/api/prompt/route.ts`
- Reads prompt file from `ui/src/data/gap_analysis_taxonomy_summary-prompt.md` (not in public directory)
- Uses NextAuth session to verify user roles
- Matches access control of `/analysis` endpoint
- File is not accessible via direct URL (protected by API route)

### 4. Service Analysis Endpoint `GET /analysis`

**Purpose:** Download the AI analysis prompt file.

**Input:**
- None

**Output:**
- JSON response required by Support Area Summary page

```json
{
  "knowledgeGaps": [
    {
      "name": "CI",
      "queryCount": 35,
      "coveragePercentage": 75,
      "queries": []
    }
  ],
  "supportAreas": [
    {
      "name": "Knowledge Gap",
      "queryCount": 127,
      "coveragePercentage": 56,
      "queries": []
    }
  ]
}
```


### 5. AI Transformation Workflow

**Pre-requisites:**

1. Node.js 22+ is installed
2. Auggie is installed

```bash
brew install nodejs@22

npm install -g @augmentcode/auggie-sdk
```

**Process:**

1. Download thread content by using the Export Data button in the UI - this will create `content.zip` in your `Downloads` folder
2. Download prompt by using Get Prompt button in the UI - this will download gap_analysis_taxonomy_summary-prompt.md in your `Downloads` folder
3. Move both `content.,zip` and he prompt file into api/analysis directory
4. Unzip `content.zip` by running `unzip content.zip -d content`
5. Run the analysis by executing `run.sh` in api/analysis directory - this will create `analysis.jsonl` file in the same directory
6. Import the analysis by uploading the analysis.jsonl file using the Import Data button in the UI
7. Updates analysis will be shown on the Support Area Summary page

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. EXPORT                                                           │
│    GET /api/summary-data/export?days=31                             │
│    (requires SUPPORT_ENGINEER role)                                 │
│    (requires CSRF token in X-CSRF-Token header)                     │
│    → content.zip (thread text files)                                │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 2. GET PROMPT                                                       │
│    GET /api/prompt                                                  │
│    (requires SUPPORT_ENGINEER role)                                 │
│    (requires CSRF token in X-CSRF-Token header)                     │
│    → gap_analysis_taxonomy_summary-prompt.md                        │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 3. EXTRACT                                                          │
│    Copy and unzip content.zip into api/analysis/content directory   │
│    Copy prompt into analysis directory                              │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 4. AI ANALYSIS (External)                                           │
│    Execute run.sh inside api/analysis directory                     │
│    This will produce analysis.jsonl file                            │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 5. IMPORT                                                           │
│    POST /api/summary-data/import (file=analysis.jsonl)              │
│    (requires SUPPORT_ENGINEER role)                                 │
│    (requires CSRF token in X-CSRF-Token header)                     │
│    → Upserts records into analysis table                            │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 6. VISUALIZATION                                                    │
│    GET /api/analysis                                                │
│    (requires LEADERSHIP or SUPPORT_ENGINEER role)                   │
│    → JSON response                                                  │
│      - Top 5 Support Areas (by Driver)                              │
│      - Top 5 Knowledge Gaps (by Category)                           │
└─────────────────────────────────────────────────────────────────────┘
```

## Access Control

| Endpoint               | Access Control | Roles Required                     |
|------------------------|----------------|------------------------------------|
| `/summary-data/export` | Restricted     | `SUPPORT_ENGINEER`                 |
| `/summary-data/import` | Restricted     | `SUPPORT_ENGINEER`                 |
| `/api/prompt`          | Restricted     | `SUPPORT_ENGINEER`                 |
| `/analysis`            | Restricted     | `LEADERSHIP` or `SUPPORT_ENGINEER` |

## Implementation Details

### Backend Components

**Controller:** `SummaryDataController.java`
- Handles HTTP requests for export/import
- Streams ZIP file for export
- Parses JSONL for import

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
- `ui/src/app/api/summary-data/export/route.ts` - Proxies export request to backend
- `ui/src/app/api/summary-data/import/route.ts` - Proxies import request to backend
- `ui/src/app/api/prompt/route.ts` - Serves prompt file with role-based access control

**UI Component:** `ui/src/components/knowledgegaps/knowledge-gaps.tsx`
- **Export Data** button (blue) - Downloads ZIP file via `/api/summary-data/export`
- **Get Prompt** button (blue) - Downloads AI prompt guide via `/api/prompt`
- **Import Data** button (green) - Uploads JSONL file via `/api/summary-data/import`
- Toast notifications for user feedback


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

- ✅ **Enables AI-Powered Analysis:** External tools can process thread data without system integration
- ✅ **Flexible Workflow:** Export → Analyze → Import cycle supports various AI tools
- ✅ **Data Persistence:** Analysis results stored in database for historical tracking
- ✅ **Upsert Support:** Can update analysis as understanding improves
- ✅ **Privacy-Aware:** Removes PII (names, mentions) from exported data
- ✅ **UI Integration:** Results displayed in Knowledge Gaps page
- ✅ **Trend Analysis:** Compare exports over time to track improvement
- ✅ **Role-Based Access Control:** Prompt file protected by API route, not accessible via direct URL

### Negative

- ⚠️ **Manual Process:** Requires external AI processing (not automated)

## References

- **AI Prompt:** `ui/public/gap_analysis_taxonomy_summary-prompt.md`
- **Controller:** `api/service/src/main/java/com/coreeng/supportbot/summarydata/rest/SummaryDataController.java`
- **Thread Service:** `api/service/src/main/java/com/coreeng/supportbot/summarydata/ThreadService.java`
- **Security Config:** `api/service/src/main/java/com/coreeng/supportbot/security/SecurityConfig.java`
- **UI Component:** `ui/src/components/knowledgegaps/knowledge-gaps.tsx`
- **Database Migration:** `api/service/src/main/resources/db/migration/V7__analysis.sql`

