package com.coreeng.supportbot.summarydata.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.analysis.AnalysisRepository;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.config.SummaryDataProps;
import com.coreeng.supportbot.summarydata.ThreadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class SummaryDataControllerTest {

    @Mock
    private ThreadService threadService;

    @Mock
    private AnalysisService analysisService;

    private SlackTicketsProps slackTicketsProps;
    private SummaryDataProps summaryDataProps;
    private ObjectMapper objectMapper;
    private SummaryDataController controller;

    @BeforeEach
    void setUp() {
        slackTicketsProps = new SlackTicketsProps("C123", "eyes", "eyes", "white_check_mark", "sos");
        summaryDataProps = new SummaryDataProps("classpath:placeholder-analysis-bundle.zip");
        objectMapper = new ObjectMapper();
        controller = new SummaryDataController(
                threadService, slackTicketsProps, summaryDataProps, analysisService, objectMapper);
    }

    // --- Export tests ---

    @Test
    void export_shouldReturnZipWithThreadFiles() throws IOException {
        // given
        var threads = ImmutableList.of(
                new ThreadService.ThreadData("1700000000.000001", "Thread one content"),
                new ThreadService.ThreadData("1700000000.000002", "Thread two content"));
        when(threadService.getThreadsWithCheckMarkAsText("C123", 31)).thenReturn(threads);

        // when
        ResponseEntity<byte[]> response = controller.exportSummaryData(31);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .isEqualTo("attachment; filename=\"content.zip\"");

        // Verify zip contents
        List<String> fileNames = new ArrayList<>();
        List<String> fileContents = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(response.getBody()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                fileNames.add(entry.getName());
                fileContents.add(new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }

        assertThat(fileNames).containsExactly("1700000000.000001.txt", "1700000000.000002.txt");
        assertThat(fileContents).containsExactly("Thread one content", "Thread two content");
        verify(threadService).getThreadsWithCheckMarkAsText("C123", 31);
    }

    @Test
    void export_shouldReturnBadRequest_whenDaysExceedsMax() {
        // when
        ResponseEntity<byte[]> response = controller.exportSummaryData(366);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(threadService);
    }

    @Test
    void export_shouldReturnBadRequest_whenDaysIsZero() {
        // when
        ResponseEntity<byte[]> response = controller.exportSummaryData(0);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(threadService);
    }

    @Test
    void export_shouldReturnBadRequest_whenDaysIsNegative() {
        // when
        ResponseEntity<byte[]> response = controller.exportSummaryData(-1);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(threadService);
    }

    @Test
    void export_shouldReturnEmptyZip_whenNoThreadsFound() throws IOException {
        // given
        when(threadService.getThreadsWithCheckMarkAsText("C123", 31)).thenReturn(ImmutableList.of());

        // when
        ResponseEntity<byte[]> response = controller.exportSummaryData(31);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify zip is empty
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(response.getBody()))) {
            assertThat(zis.getNextEntry()).isNull();
        }
    }

    // --- Import tests ---

    @Test
    void import_shouldParseJsonlAndReturnCount() {
        // given
        String jsonl = """
                {"ticketId":1,"driver":"Bug","category":"Auth","feature":"Login","summary":"Fix login"}
                {"ticketId":2,"driver":"Feature","category":"UI","feature":"Dashboard","summary":"Add chart"}""";
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.jsonl", "application/jsonl", jsonl.getBytes(StandardCharsets.UTF_8));

        when(analysisService.importAnalysisData(anyList())).thenReturn(2);

        // when
        ResponseEntity<SummaryDataController.ImportResponse> response = controller.importAnalysisData(file);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().recordsImported()).isEqualTo(2);
        assertThat(response.getBody().message()).isEqualTo("Import successful");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AnalysisRepository.AnalysisRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(analysisService).importAnalysisData(captor.capture());

        List<AnalysisRepository.AnalysisRecord> records = captor.getValue();
        assertThat(records).hasSize(2);
        assertThat(records.get(0).ticketId()).isEqualTo(1);
        assertThat(records.get(0).driver()).isEqualTo("Bug");
        assertThat(records.get(1).ticketId()).isEqualTo(2);
        assertThat(records.get(1).category()).isEqualTo("UI");
    }

    @Test
    void import_shouldSkipMalformedLines() {
        // given
        String jsonl = """
                {"ticketId":1,"driver":"Bug","category":"Auth","feature":"Login","summary":"Fix login"}
                this is not valid json
                {"ticketId":3,"driver":"Feature","category":"UI","feature":"Dashboard","summary":"Add chart"}""";
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.jsonl", "application/jsonl", jsonl.getBytes(StandardCharsets.UTF_8));

        when(analysisService.importAnalysisData(anyList())).thenReturn(2);

        // when
        ResponseEntity<SummaryDataController.ImportResponse> response = controller.importAnalysisData(file);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AnalysisRepository.AnalysisRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(analysisService).importAnalysisData(captor.capture());

        List<AnalysisRepository.AnalysisRecord> records = captor.getValue();
        assertThat(records).hasSize(2);
        assertThat(records.get(0).ticketId()).isEqualTo(1);
        assertThat(records.get(1).ticketId()).isEqualTo(3);
    }

    @Test
    void import_shouldReturnBadRequest_whenFileIsEmpty() {
        // given
        MockMultipartFile file = new MockMultipartFile("file", "data.jsonl", "application/jsonl", new byte[0]);

        // when
        ResponseEntity<SummaryDataController.ImportResponse> response = controller.importAnalysisData(file);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).containsIgnoringCase("empty");
        verifyNoInteractions(analysisService);
    }

    @Test
    void import_shouldReturn500_whenDatabaseFails() {
        // given
        String jsonl =
                "{\"ticketId\":1,\"driver\":\"Bug\",\"category\":\"Auth\",\"feature\":\"Login\",\"summary\":\"Fix login\"}";
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.jsonl", "application/jsonl", jsonl.getBytes(StandardCharsets.UTF_8));

        when(analysisService.importAnalysisData(anyList()))
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        // when
        ResponseEntity<SummaryDataController.ImportResponse> response = controller.importAnalysisData(file);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Database error");
    }
}
