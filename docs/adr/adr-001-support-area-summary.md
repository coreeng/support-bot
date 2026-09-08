# ADR: Summary Data Export/Import for Knowledge Gap Analysis

**Amended:** 2026-09 — [PR #327](https://github.com/coreeng/support-bot/pull/327) removed the UI export/import/results surface (the Support Area Summary page at `/knowledge-gaps`, which now redirects to `/summary`). The `/summary-data/*` endpoints (export, import, results) remain and are API-only (see `api/service/README.md`); aggregated insights are now shown on the Support Summary page (`/summary`) (settings in `api/service/docs/configuration.md`). Items 4–5 and the "UI Integration" consequence below describe the original UI and are kept as history.

---

## Decision

To enable external AI-powered analysis of support patterns and knowledge gaps, we need to provide a way to:

1. Export raw thread data for processing by external AI tools
2. Get AI prompt for analysis
3. Run AI analysis on the exported data
3. Import structured analysis results back into the bot DB
4. Display aggregated insights in the UI



## Consequences

### Positive

- ✅ **Enables AI-Powered Analysis:** External tools can process thread data without system integration
- ✅ **Flexible Workflow:** Export → Analyze → Import cycle supports various AI tools
- ✅ **Data Persistence:** Analysis results stored in database for historical tracking
- ✅ **Upsert Support:** Can update analysis as understanding improves
- ✅ **UI Integration:** Results displayed *(amended 2026-09, PR #327: the dedicated results UI was removed; insights are shown on `/summary`, fed by the automated pipeline of ADR-002, and the import/results endpoints are API-only)*
- ✅ **Trend Analysis:** Compare exports over time to track improvement
- ✅ **Role-Based Access Control:** Endpoints protected

### Negative

- ⚠️ **Manual Process:** Requires external AI processing (not automated)

