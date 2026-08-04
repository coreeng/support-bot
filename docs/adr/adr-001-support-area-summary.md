# ADR: Summary Data Export/Import for Knowledge Gap Analysis

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
- ✅ **Privacy-Aware:** Removes PII (names, mentions) from exported data
- ✅ **UI Integration:** Results displayed
- ✅ **Trend Analysis:** Compare exports over time to track improvement
- ✅ **Role-Based Access Control:** Endpoints protected

### Negative

- ⚠️ **Manual Process:** Requires external AI processing (not automated)

