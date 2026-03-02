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
- Query parameter: `days` (optional, default: 7) - Number of days to look back

**Output:**
- ZIP file containing individual text files
- Each file named with thread timestamp (e.g., `1234567890.123456.txt`)
- Thread content with:
  - Messages concatenated with double newlines
  - Author is removed for privacy
  - Timestamps is removed to preserve conversation structure and data quality
  - Slack mentions removed (pattern: `<?@?[UW][A-Z0-9]{8,}>?`)
  - Capitalized words are removed using pattern matching (pattern: `\b([A-Z][a-z]+(?:\s+[A-Z][a-z]+)*)\b)`). This is aiming to remove human names for privacy.
  - Common capitalized words are preserved to maintain conversation structure (loaded from `commonly-capitalized-words.txt`). This is trade-off between privacy and context.

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
- JSONL file with fields: `ticketId`, `driver`, `category`, `feature`, `summary`

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

### 3. Service Prompt Endpoint: `GET /summary-data/analysis`

**Purpose:** Download the AI analysis bundle.

**Input:**
- None

**Output:**
- Content-Type: `text/markdown; charset=utf-8`
- Content-Disposition: `attachment; filename="analysis.zip"`

### 4. Service Analysis Endpoint `GET /summary-data/results`

**Purpose**: Fetch the summary results for display in the UI.

**Input:**
- None

**Output:**
- JSON response required by Support Area Summary page

```json
{
  "knowledgeGaps": [
    {
      "name": "CI", // Knowledge gap category
      "queryCount": 35, // Total number of support queries with this specific knowledge gap
      "queries": [
            {
              "link": "https://some.slack.com/archives/ARCACLESD/p1770295016842609",
              "text": "Query summary"
            }
        ]
    },
    ... // Other knowledge gap categories (up to 5)
  ],
  "supportAreas": [
    {
      "name": "Knowledge Gap",  // Support area name
      "queryCount": 127,  // Total number of support queries within this support area
      "queries": [
        {
          "link": "https://some.slack.com/archives/ARCACLESD/p1770295016842625",
          "text": "Query summary"
        }
      ]
    },
    {
      "name": "Feature Request",  // Support area name
      "queryCount": 2,
      "queries": [
        {
          "link": "https://some.slack.com/archives/ARCACLESD/p1770295016842611",
          "text": "Query summary"
        }
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
2. Download `analysis.zip` bundle including prompt amd AI script by using Get Analysis Bundle button in the UI into your `Downloads` folder
3. Unzip analysis.zip into analysis directory
4. Move both `content.zip` into analysis directory
5. Unzip `content.zip` into content directory
6. Run the analysis by executing `run.sh` in analysis directory - this will create `analysis.jsonl` file in the same directory
7. Import `analysis.jsonl` by using the Import Data button in the UI
8. Updated analysis will be shown on the Support Area Summary page

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
│    GET /api/summary-data/prompt                                                  │
│    (requires SUPPORT_ENGINEER role)                                 │
│    (requires CSRF token in X-CSRF-Token header)                     │
│    → analysis.zip                        │
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
- Kafka, Persistence
- HNC (Hierarchical Namespaces)
- Azure, AWS or GCP

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

