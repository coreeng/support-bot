package com.coreeng.supportbot.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.analysis.AnalysisRepository.AnalysisRecord;
import com.coreeng.supportbot.analysis.AnalysisRepository.DimensionSummary;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalysisResultsServiceTest {

    @Mock
    private AnalysisRepository analysisRepository;

    private AnalysisResultsService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisResultsService(analysisRepository);
    }

    @Test
    void getKnowledgeGapCategoriesWithSummaries_shouldReturnRepositoryResults() {
        // given
        List<DimensionSummary> expectedSummaries = List.of(
                new DimensionSummary("Monitoring & Troubleshooting", 25L, "Example summary 1"),
                new DimensionSummary("Configuration", 15L, "Example summary 2"));

        when(analysisRepository.getKnowledgeGapCategoriesWithSummaries()).thenReturn(expectedSummaries);

        // when
        List<DimensionSummary> result = service.getKnowledgeGapCategoriesWithSummaries();

        // then
        assertThat(result).isEqualTo(expectedSummaries);
        verify(analysisRepository).getKnowledgeGapCategoriesWithSummaries();
    }

    @Test
    void getDriversWithSummaries_shouldReturnRepositoryResults() {
        // given
        List<DimensionSummary> expectedSummaries = List.of(
                new DimensionSummary("Knowledge Gap", 50L, "Example summary 1"),
                new DimensionSummary("Bug", 30L, "Example summary 2"),
                new DimensionSummary("Feature Request", 20L, "Example summary 3"));

        when(analysisRepository.getDriversWithSummaries()).thenReturn(expectedSummaries);

        // when
        List<DimensionSummary> result = service.getDriversWithSummaries();

        // then
        assertThat(result).isEqualTo(expectedSummaries);
        verify(analysisRepository).getDriversWithSummaries();
    }

    @Test
    void importAnalysisData_shouldCallRepositoryUpsert() {
        // given
        List<AnalysisRecord> records = List.of(
                new AnalysisRecord(1, "Knowledge Gap", "Monitoring", "workload compute", "Summary 1", "prompt-v1"),
                new AnalysisRecord(2, "Bug", "Configuration", "networking", "Summary 2", "prompt-v1"));

        when(analysisRepository.upsert(records)).thenReturn(2);

        // when
        int result = service.importAnalysisData(records);

        // then
        assertThat(result).isEqualTo(2);
        verify(analysisRepository).upsert(records);
    }

    @Test
    void importAnalysisData_shouldHandleEmptyList() {
        // given
        List<AnalysisRecord> emptyRecords = List.of();
        when(analysisRepository.upsert(emptyRecords)).thenReturn(0);

        // when
        int result = service.importAnalysisData(emptyRecords);

        // then
        assertThat(result).isEqualTo(0);
        verify(analysisRepository).upsert(emptyRecords);
    }

    @Test
    void importAnalysisData_shouldHandleLargeDataset() {
        // given
        List<AnalysisRecord> largeDataset = List.of(
                new AnalysisRecord(1, "Knowledge Gap", "Category1", "feature1", "Summary 1", "prompt-v1"),
                new AnalysisRecord(2, "Bug", "Category2", "feature2", "Summary 2", "prompt-v1"),
                new AnalysisRecord(3, "Feature Request", "Category3", "feature3", "Summary 3", "prompt-v1"),
                new AnalysisRecord(4, "Knowledge Gap", "Category4", "feature4", "Summary 4", "prompt-v1"),
                new AnalysisRecord(5, "Bug", "Category5", "feature5", "Summary 5", "prompt-v1"));

        when(analysisRepository.upsert(largeDataset)).thenReturn(5);

        // when
        int result = service.importAnalysisData(largeDataset);

        // then
        assertThat(result).isEqualTo(5);
        verify(analysisRepository).upsert(largeDataset);
    }

    @Test
    void getKnowledgeGapCategoriesWithSummaries_shouldHandleEmptyResults() {
        // given
        when(analysisRepository.getKnowledgeGapCategoriesWithSummaries()).thenReturn(List.of());

        // when
        List<DimensionSummary> result = service.getKnowledgeGapCategoriesWithSummaries();

        // then
        assertThat(result).isEmpty();
        verify(analysisRepository).getKnowledgeGapCategoriesWithSummaries();
    }

    @Test
    void getDriversWithSummaries_shouldHandleEmptyResults() {
        // given
        when(analysisRepository.getDriversWithSummaries()).thenReturn(List.of());

        // when
        List<DimensionSummary> result = service.getDriversWithSummaries();

        // then
        assertThat(result).isEmpty();
        verify(analysisRepository).getDriversWithSummaries();
    }
}
